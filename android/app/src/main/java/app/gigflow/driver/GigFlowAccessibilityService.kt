package app.gigflow.driver

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.view.accessibility.AccessibilityEvent
import org.json.JSONObject

/**
 * Supervisa las ventanas de las aplicaciones de conductor configuradas, extrae tarjetas de ofertas, las
 * evalúa según las reglas del usuario, muestra el resultado flotante y, opcionalmente,
 * toca aceptar/rechazar cuando los interruptores de automatización están activos.
 *
 * Todo se ejecuta en el dispositivo — ningún contenido de pantalla o datos de ofertas sale del teléfono.
 */
class GigFlowAccessibilityService : AccessibilityService() {

    private lateinit var settings: SettingsRepository
    private lateinit var overlay: OverlayController

    private var lastFingerprint = ""
    private var lastSeenAt = 0L
    private var lastScanAt = 0L
    private var activeOffer: ScoredOffer? = null

    private val handler = Handler(Looper.getMainLooper())
    private var hideRunnable: Runnable? = null
    private var hideToken = ""
    private var tts: TextToSpeech? = null
    private var localDb: LocalDb? = null
    private var countdownTicker: Runnable? = null

    // Estado de seguimiento automático de turnos
    private var lastDriverAppAt = 0L
    private var idleStopCheck: Runnable? = null
    private val loggedCompletions = mutableSetOf<String>()

    companion object {
        /** Detener automáticamente el seguimiento del turno después de este tiempo sin eventos de aplicaciones de conductor. */
        private const val IDLE_STOP_MS = 30 * 60_000L

        /** Los fotogramas pueden parpadear a mitad de una animación; dale este tiempo a una oferta para
         *  reaparecer antes de ocultar la capa flotante. */
        private const val MISS_GRACE_MS = 2_000L

        /** Instancia activa mientras el servicio está habilitado — nula en caso contrario. */
        @Volatile var instance: GigFlowAccessibilityService? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        settings = SettingsRepository(this)
        overlay = OverlayController(this)
        localDb = LocalDb(this)
        tts = TextToSpeech(this) { /* listo */ }
        instance = this
    }

    /**
     * Oferta de prueba sintética — prueba todo el proceso: evaluación → capa flotante →
     * cuenta regresiva → envío de gestos → voz. El nodo de aceptación apunta a una
     * zona muerta en el margen de la pantalla para que el toque de auto-aceptación se dispare demostrablemente sin
     * tocar nada real; no se registra nada (ver recordAction).
     */
    fun testOffer() {
        handler.post {
            val dead = NodeRef(android.graphics.Rect(8, 1150, 14, 1190), "test")
            val o = DetectedOffer(
                appPackage = "app.gigflow.test",
                payoutCents = (12.50 * 100).toInt(),
                tipCents = 200,
                distanceKm = 4.2,
                durationMin = 18.0,
                extraDistanceKm = null,
                acceptNode = dead,
                declineNode = dead,
                rawTexts = listOf("Oferta de prueba"),
                currencySymbol = settings.currencySymbol,
                expiresInSec = 12,
            )
            val scored = RuleEngine.score(o, settings.rules())
            activeOffer = scored // para que stopWatching()/onInterrupt también la limpien
            val delay = if (settings.autoAccept && scored.verdict == Verdict.GOOD)
                settings.autoAcceptDelaySec else 0
            overlay.show(scored, delay) { action ->
                cancelAutoAccept()
                if (action == OverlayController.Action.EXPIRED) offerGone()
                overlay.hide()
            }
            if (delay > 0) scheduleAutoAccept(scored, dead, delay)
            if (settings.voiceAlerts) {
                tts?.speak(
                    "Oferta de prueba, ${spokenAmount(o.payoutCents, o.currencySymbol)}",
                    TextToSpeech.QUEUE_FLUSH, null, "test",
                )
            }
        }
    }

    /**
     * Cierre iniciado desde la interfaz. Los eventos de nuestro propio paquete nunca llegan a
     * onAccessibilityEvent (filtro del manifiesto), por lo que desactivar el monitoreo
     * dentro de la aplicación no borraría la capa flotante ni los toques pendientes — el
     * interruptor de Monitoreo llama a esto directamente.
     */
    fun stopWatching() = handler.post { offerGone() }

    /**
     * Las tarjetas de oferta a menudo viven en una ventana emergente o de diálogo que no es la
     * ventana "activa", y rootInActiveWindow es una API conocida por ser nula. Volver
     * a escanear todas las ventanas interactivas para encontrar la aplicación de conductor.
     */
    private fun appWindowRoot(pkg: String): android.view.accessibility.AccessibilityNodeInfo? {
        rootInActiveWindow?.let { if (it.packageName?.toString() == pkg) return it }
        try {
            for (w in windows) {
                val r = w.root ?: continue
                if (r.packageName?.toString() == pkg) return r
            }
        } catch (_: Exception) { }
        return null
    }

    /** Ocultar la capa flotante solo después de que la oferta haya estado ausente por un momento —
     *  las omisiones de análisis de un solo fotograma son normales (animaciones, dibujos parciales)
     *  y no deberían hacer parpadear el veredicto. */
    private fun maybeOfferGone(now: Long) {
        if (activeOffer == null) return
        if (now - lastSeenAt < MISS_GRACE_MS) return
        offerGone()
    }

    /** Última ubicación conocida — suficiente para etiquetar dónde apareció una oferta. */
    private fun lastLocation(): Pair<Double, Double>? {
        if (!MileageTracker.hasLocationPermission(this)) return null
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return try {
            (lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER))
                ?.let { it.latitude to it.longitude }
        } catch (_: SecurityException) { null }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        android.util.Log.d("GigFlowSvc", "evento pkg=${event.packageName} tipo=${event.eventType}")
        val pkg = event.packageName?.toString() ?: return
        val now = System.currentTimeMillis()

        // Los eventos solo provienen de aplicaciones de conductor vigiladas (filtros de manifiesto), por lo que
        // cualquier evento significa que el conductor está dentro de una aplicación de trabajo — alimentar el
        // rastreador de turnos automáticos antes que nada.
        onDriverAppSeen(now)

        val prefs = settings.rulesFor(pkg) // anulaciones por aplicación sobre las globales
        // Monitoreo desactivado mientras la capa flotante estaba visible — cerrarla.
        if (!prefs.enabled) { offerGone(); return }
        if (now - lastScanAt < 350) return // limitar tormentas de contentChanged
        lastScanAt = now

        // Los símbolos más allá del conjunto integrado (R$, ₩, KSh…) coinciden a través de la
        // moneda configurada por el usuario para que cualquier mercado pueda ser analizado.
        val curSymbol = settings.currencySymbol
        val extraRe = OfferParser.extraMoney(settings.currencyCode, curSymbol)

        val root = appWindowRoot(pkg) ?: run {
            // La ventana existe pero su contenido no se puede recuperar (o la lista de ventanas
            // aún no se ha actualizado) — regístralo para que Diagnóstico muestre
            // "vimos la aplicación pero no pudimos leerla" en lugar de nada.
            Diagnostics.record(this, pkg, "idle", listOf("(ventana no legible)"))
            maybeOfferGone(now)
            return
        }

        val nodes = OfferParser.flatten(root)
        val offer = OfferParser.parse(pkg, nodes, extraRe, curSymbol)
        if (offer == null) {
            // Una pantalla con una cifra de dinero pero sin análisis es un "fallo" — la
            // tarjeta probablemente estaba allí pero el diseño superó al heurístico.
            // Regístralo para que Diagnóstico pueda mostrar lo que vimos.
            val hasMoney = nodes.any {
                OfferParser.moneyOf(it.text, extraRe, curSymbol) != null
            }
            Diagnostics.record(this, pkg, if (hasMoney) "miss" else "idle",
                nodes.map { it.diag() })
            maybeLogCompletion(pkg, nodes, now, extraRe, curSymbol)
            maybeOfferGone(now)
            return
        }
        Diagnostics.record(this, pkg, "offer", nodes.map { it.diag() })

        // La misma tarjeta sigue en pantalla — no volver a evaluar ni recrear la capa flotante en cada fotograma.
        if (offer.fingerprint == lastFingerprint && now - lastSeenAt < 60_000) {
            lastSeenAt = now
            return
        }
        lastFingerprint = offer.fingerprint
        lastSeenAt = now

        val scored = RuleEngine.score(offer, prefs)
        activeOffer = scored

        val loc = lastLocation()

        OfferLog.add(this, OfferLog.Entry(
            at = offer.seenAt,
            pkg = pkg,
            payoutCents = offer.payoutCents,
            distanceKm = offer.distanceKm,
            durationMin = offer.durationMin,
            perMileCents = scored.perMileCents,
            perHourCents = scored.perHourCents,
            verdict = scored.verdict.name,
            action = "shown",
            lat = loc?.first,
            lng = loc?.second,
            reservation = offer.isReservation,
            currency = offer.currencySymbol,
        ))

        // Alerta de voz al estilo Mystro para el veredicto, cuando está habilitada.
        if (settings.voiceAlerts) {
            val label = when (scored.verdict) {
                Verdict.GOOD -> "Buena oferta"
                Verdict.MEH -> "Oferta límite"
                Verdict.BAD -> "Omitir"
            }
            tts?.speak(
                "$label, ${spokenAmount(offer.payoutCents, offer.currencySymbol)}",
                TextToSpeech.QUEUE_FLUSH, null, "offer",
            )
        }

        val autoAcceptDelay =
            if (prefs.autoAccept && scored.verdict == Verdict.GOOD && offer.acceptNode != null &&
                (settings.autoAcceptReservations || !offer.isReservation))
                settings.autoAcceptDelaySec else 0

        overlay.show(scored, autoAcceptDelay) { action ->
            cancelAutoAccept()
            when (action) {
                OverlayController.Action.ACCEPT -> offer.acceptNode?.let {
                    acceptGesture(it, offer.appPackage)
                    recordAction(scored, "manual_accept")
                }
                OverlayController.Action.DECLINE -> offer.declineNode?.let {
                    GesturePerformer.tap(this, it.bounds)
                    recordAction(scored, "manual_decline")
                }
                // El temporizador en la tarjeta de la oferta llegó a cero — el mismo cierre que cuando
                // la tarjeta desaparece; nunca dejes un toque pendiente atrás.
                OverlayController.Action.EXPIRED -> offerGone()
                OverlayController.Action.CANCEL_AUTO -> Unit
            }
            overlay.hide()
        }

        // Tiempo de espera opcional de la capa flotante — 0 = permanecer hasta que la tarjeta desaparezca.
        val secs = prefs.overlaySeconds
        if (secs > 0) {
            val token = offer.fingerprint
            hideToken = token
            hideRunnable?.let { handler.removeCallbacks(it) }
            val r = Runnable { if (hideToken == token) overlay.hide() }
            hideRunnable = r
            handler.postDelayed(r, secs * 1000L)
        }

        // Automatización: solo cuando el usuario la habilitó explícitamente, y solo cuando
        // realmente encontramos los límites del botón. La auto-aceptación se ejecuta en la
        // cuenta regresiva configurada (como la de Mystro) para que el conductor pueda cancelar.
        if (autoAcceptDelay > 0) {
            scheduleAutoAccept(scored, offer.acceptNode!!, autoAcceptDelay)
        } else if (prefs.autoDecline && scored.verdict == Verdict.BAD) {
            offer.declineNode?.let { node ->
                GesturePerformer.tap(this, node.bounds) { ok ->
                    if (ok) recordAction(scored, "auto_decline")
                }
            }
        }
    }

    /**
     * La opción de aceptación difiere según la aplicación: la mayoría son un toque en el control con etiqueta,
     * pero Amazon Flex utiliza un control deslizante de "deslizar para aceptar" donde un toque
     * no hace nada. Uber/Lyft no necesitan ningún gesto especial — el analizador resuelve
     * la tarjeta de solicitud interactiva por sí mismo para esos (aceptar tocando en cualquier parte).
     */
    private fun acceptGesture(node: NodeRef, pkg: String, done: (Boolean) -> Unit = {}) {
        if (pkg == "com.amazon.rabbit" || node.label.contains("swipe", ignoreCase = true)) {
            GesturePerformer.swipeRight(this, node.bounds, done)
        } else {
            GesturePerformer.tap(this, node.bounds, done)
        }
    }

    /** Descontar la cuenta regresiva de la capa flotante y luego tocar aceptar en 0. */
    private fun scheduleAutoAccept(scored: ScoredOffer, node: NodeRef, delaySec: Int) {
        cancelAutoAccept() // una nueva oferta puede llegar antes de que termine la cuenta regresiva anterior
        var left = delaySec
        val ticker = object : Runnable {
            override fun run() {
                left--
                if (left <= 0) {
                    acceptGesture(node, scored.offer.appPackage) { ok ->
                        if (ok) recordAction(scored, "auto_accept")
                    }
                    overlay.hide()
                } else {
                    overlay.setCountdown(left)
                    handler.postDelayed(this, 1000)
                }
            }
        }
        countdownTicker = ticker
        handler.postDelayed(ticker, 1000)
    }

    /**
     * Seguimiento automático de turnos (opcional): abrir cualquier aplicación de conductor vigilada inicia el kilometraje
     * por GPS; 30 minutos sin una aplicación vigilada lo detiene. El permiso de capa flotante
     * que ya poseemos nos exime de los límites de servicios en segundo plano (FGS), pero
     * lo protegemos de todos modos — en el peor de los casos, el usuario toca "Iniciar turno" por sí mismo.
     */
    private fun onDriverAppSeen(now: Long) {
        lastDriverAppAt = now
        if (!settings.autoTrackShift) return
        if (!MileageTracker.hasLocationPermission(this)) return
        if (!MileageTracker.running) {
            try {
                startForegroundService(MileageTracker.startIntent(this))
            } catch (_: Exception) { /* inicio en segundo plano bloqueado — el inicio manual sigue funcionando */ }
        }
        // Activar la comprobación de parada por inactividad; cada evento de la aplicación de conductor la pospone.
        idleStopCheck?.let { handler.removeCallbacks(it) }
        val check = Runnable {
            if (System.currentTimeMillis() - lastDriverAppAt >= IDLE_STOP_MS &&
                MileageTracker.running
            ) {
                stopService(MileageTracker.stopIntent(this))
            }
        }
        idleStopCheck = check
        handler.postDelayed(check, IDLE_STOP_MS)
    }

    /**
     * Captura de ganancias post-viaje — lee la pantalla de entrega completada / resumen
     * de ganancias y registra el pago como una ganancia local.
     */
    private fun maybeLogCompletion(
        pkg: String,
        nodes: List<OfferParser.FlatNode>,
        now: Long,
        extra: Regex? = null,
        extraSymbol: String = "$",
    ) {
        if (!settings.autoLogEarnings) return
        val comp = CompletionParser.parse(pkg, nodes, extra, extraSymbol) ?: return
        if (!loggedCompletions.add(comp.fingerprint)) return // esta pantalla ya fue registrada
        if (loggedCompletions.size > 200) loggedCompletions.clear()

        val payload = JSONObject()
            .put("amountCents", comp.payoutCents)
            .put("category", "TRIP")
            .put("platformKey", platformKeyFor(pkg))
            .put("notes", "Capturado automáticamente")
            .put("earnedAt", now)
        comp.tipCents?.let { payload.put("tipCents", it) }

        localDb?.insert("earning", payload)
        if (settings.voiceAlerts) {
            tts?.speak(
                "Registrado ${spokenAmount(comp.payoutCents, comp.currencySymbol)}",
                TextToSpeech.QUEUE_ADD, null, "earn",
            )
        }
    }

    /** "12.50 dollars" / "1850 forints" — los nombres y el manejo de ceros decimales
     *  provienen del registro de monedas. */
    private fun spokenAmount(cents: Int, symbol: String): String =
        Currencies.spokenAmount(cents, symbol)

    private fun cancelAutoAccept() {
        countdownTicker?.let { handler.removeCallbacks(it) }
        countdownTicker = null
    }

    private fun recordAction(s: ScoredOffer, action: String) {
        val o = s.offer
        if (o.appPackage == "app.gigflow.test") return // oferta de prueba — no registrar
        OfferLog.add(this, OfferLog.Entry(
            at = o.seenAt, pkg = o.appPackage, payoutCents = o.payoutCents,
            distanceKm = o.distanceKm, durationMin = o.durationMin,
            perMileCents = s.perMileCents, perHourCents = s.perHourCents,
            verdict = s.verdict.name, action = action,
            lat = null, lng = null,
            reservation = o.isReservation,
            currency = o.currencySymbol,
        ))
    }

    /** "· [tap] (btn_accept) Elfogadás" — la capacidad de interacción y el ID de vista son
     *  lo que necesita un reporte de fallo de análisis: si la aplicación expone botones reales
     *  frente a una tarjeta donde se puede tocar en cualquier parte, y qué control deberíamos haber pulsado. */
    private fun OfferParser.FlatNode.diag() = buildString {
        if (clickable) append("[tap] ")
        viewId?.substringAfterLast('/')?.let { append("($it) ") }
        append(text)
    }

    /** Paquete vigilado → clave de plataforma utilizada para etiquetar las ganancias capturadas. */
    private fun platformKeyFor(pkg: String): String = when {
        "uber" in pkg -> "uber"
        "lyft" in pkg -> "lyft"
        "doordash" in pkg -> "doordash"
        "instacart" in pkg -> "instacart"
        "amazon" in pkg || "rabbit" in pkg -> "amazon-flex"
        "spark" in pkg || "walmart" in pkg -> "spark"
        "wolt" in pkg -> "wolt"
        "foodora" in pkg || "logistics.rider" in pkg -> "foodora"
        else -> "other"
    }

    private fun offerGone() {
        hideRunnable?.let { handler.removeCallbacks(it) }
        hideToken = ""
        cancelAutoAccept() // nunca tocar límites de una tarjeta que ya no está visible
        activeOffer = null
        lastFingerprint = ""
        overlay.hide()
    }

    override fun onInterrupt() {
        offerGone()
    }

    override fun onDestroy() {
        instance = null
        cancelAutoAccept()
        overlay.hide()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }
}