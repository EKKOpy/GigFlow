package app.gigflow.driver.ui

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.provider.Settings
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import app.gigflow.driver.*
import app.gigflow.driver.ui.Ios.Palette
import app.gigflow.driver.ui.Ios.dp
import app.gigflow.driver.ui.Ios.haptic
import app.gigflow.driver.ui.Ios.pressable

/** Assist — estado del asistente, reglas, interruptores de automatización, diagnósticos. */
class AssistScreen(
    ctx: Context,
    private val p: Palette,
    private val settings: SettingsRepository,
    private val db: LocalDb,
) {
    val screen = Screen(ctx, p, "Asistente")
    private val ctx: Context = ctx

    private fun gap(h: Float) = View(ctx).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(ctx, h))
    }

    fun refresh() {
        screen.column.removeAllViews()
        val c = screen.column

        c.addView(statusCard())
        c.addView(gap(10f))
        c.addView(setupCard())
        c.addView(Sections.header(ctx, p, "Automatización"))
        c.addView(automationCard())
        c.addView(Sections.header(ctx, p, "Reglas de ofertas"))
        c.addView(rulesCard())
        c.addView(Sections.header(ctx, p, "Reglas por aplicación"))
        c.addView(perAppCard())
        c.addView(TextView(ctx).apply {
            text = "Las aplicaciones sin una regla personalizada usan las reglas globales de arriba."
            textSize = Ios.T_FOOTNOTE; setTextColor(p.label3)
            setPadding(dp(ctx, 20f), dp(ctx, 6f), dp(ctx, 16f), 0)
        })
        c.addView(Sections.header(ctx, p, "Preferencias"))
        c.addView(preferencesCard())
        c.addView(Sections.header(ctx, p, "Diagnóstico"))
        c.addView(diagnosticsCard())
        c.addView(TextView(ctx).apply {
            text = "Lo último que vio el asistente en cada aplicación. Si una oferta no fue evaluada, toca la aplicación para ver los textos que leyó; eso nos indica qué debemos corregir."
            textSize = Ios.T_FOOTNOTE; setTextColor(p.label3)
            setPadding(dp(ctx, 20f), dp(ctx, 6f), dp(ctx, 16f), 0)
        })
        c.addView(Sections.header(ctx, p, "Acerca de"))
        c.addView(aboutCard())
        c.addView(Sections.header(ctx, p, "Créditos"))
        c.addView(creditsCard())
        screen.animateIn()
    }

    private fun statusCard(): View {
        val card = Sections.card(ctx, p)
        val on = OverlayController.isServiceEnabled(ctx)
        card.addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(ctx, 16f), dp(ctx, 14f), dp(ctx, 16f), dp(ctx, 14f))
            addView(View(ctx).apply {
                background = Ios.rounded(14f, if (on) p.green else p.red, ctx)
                layoutParams = LinearLayout.LayoutParams(dp(ctx, 10f), dp(ctx, 10f))
                    .apply { rightMargin = dp(ctx, 12f) }
            })
            addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                addView(TextView(ctx).apply {
                    text = if (on) "El asistente está activado" else "El asistente está desactivado"
                    textSize = Ios.T_HEADLINE
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(p.label)
                })
                addView(TextView(ctx).apply {
                    text = if (on) "Evaluando ofertas en tus aplicaciones de conductor"
                    else "Actívalo para evaluar y automatizar ofertas"
                    textSize = Ios.T_FOOTNOTE
                    setTextColor(p.label2)
                })
            })
        })
        // Botón de ancho completo debajo del texto — un botón en línea abarrotaba
        // el subtítulo y se cortaba a mitad de palabra en pantallas más estrechas.
        if (!on) {
            card.addView(LinearLayout(ctx).apply {
                setPadding(dp(ctx, 16f), 0, dp(ctx, 16f), dp(ctx, 14f))
                val btn = iosButton(ctx, p, "Activar ahora — toma 10 segundos")
                addView(btn, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
                btn.setOnClickListener {
                    ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
            })
        }
        return card
    }

    /** Configuración guiada — la gran lección de UX de Mystro: la aplicación *no hace nada*
     *  hasta que el servicio de accesibilidad está activado, así que hay que resaltarlo con fuerza. */
    private fun setupCard(): View {
        val serviceOn = OverlayController.isServiceEnabled(ctx)
        val locOn = MileageTracker.hasLocationPermission(ctx)
        val notifOn = android.os.Build.VERSION.SDK_INT < 33 ||
            ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        if (serviceOn && locOn && notifOn) return View(ctx) // todo bien — ocultar

        val card = Sections.card(ctx, p)
        var first = true
        fun step(title: String, subtitle: String, glyph: String, done: Boolean, action: (() -> Unit)?) {
            if (!first) card.addView(Sections.separator(ctx, p))
            first = false
            card.addView(Sections.row(ctx, p,
                title = title,
                subtitle = subtitle,
                iconGlyph = if (done) "checkmark" else glyph,
                iconTint = if (done) p.green else p.orange,
                chevron = !done && action != null,
            ) { action?.invoke() })
        }
        step("Activar el servicio de accesibilidad",
            "Obligatorio — sin él, el asistente no puede ver las ofertas",
            "slider", serviceOn) {
            ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        step("Permitir acceso a la ubicación",
            "Necesario para el registro automático de kilometraje de los turnos",
            "mappin", locOn) {
            (ctx as? android.app.Activity)?.requestPermissions(
                arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION),
                MainActivity.REQ_LOCATION,
            )
        }
        step("Permitir notificaciones",
            "Muestra la notificación de seguimiento mientras el turno está activo",
            "bell", notifOn) {
            (ctx as? android.app.Activity)?.requestPermissions(
                arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 9)
        }
        c_footer(card, "Completa estos tres pasos y luego activa los interruptores de automatización de abajo.")
        return card
    }

    /** Lo último que vio el servicio por cada aplicación vigilada — toca para inspeccionar. */
    private fun diagnosticsCard(): View {
        val card = Sections.card(ctx, p)
        var first = true
        watchedApps.forEach { (name, pkg) ->
            if (!first) card.addView(Sections.separator(ctx, p))
            first = false
            val snap = Diagnostics.get(ctx, pkg)
            val (label, tint) = when {
                snap == null -> "Aún no se ha visto" to p.label3
                snap.outcome == "offer" -> "Oferta evaluada · ${ago(snap.at)}" to p.green
                snap.outcome == "miss" -> "Pantalla no detectada · ${ago(snap.at)}" to p.orange
                else -> "Pantalla vista · ${ago(snap.at)}" to p.label2
            }
            card.addView(Sections.row(ctx, p,
                title = name,
                subtitle = label,
                iconGlyph = "doc", iconTint = tint,
                chevron = snap?.texts?.isNotEmpty() == true,
            ) { snap?.let { showDiag(name, it) } })
        }
        return card
    }

    private fun ago(at: Long): String {
        val s = (System.currentTimeMillis() - at) / 1000
        return when {
            s < 60 -> "hace un momento"
            s < 3600 -> "hace ${s / 60}m"
            s < 86400 -> "hace ${s / 3600}h"
            else -> "hace ${s / 86400}d"
        }
    }

    /** Hoja que lista los textos que leyó el analizador — toma una captura de pantalla para reportarlo. */
    private fun showDiag(name: String, snap: Diagnostics.Snap) {
        val sheet = IosSheet(ctx, p)
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(ctx, 20f), dp(ctx, 4f), dp(ctx, 20f), dp(ctx, 12f))
        }
        col.addView(TextView(ctx).apply {
            text = "$name · ${snap.outcome}"
            textSize = Ios.T_HEADLINE; setTypeface(typeface, Typeface.BOLD)
            setTextColor(p.label); setPadding(0, 0, 0, dp(ctx, 4f))
        })
        col.addView(TextView(ctx).apply {
            text = if (snap.outcome == "miss")
                "Esta pantalla contenía un precio pero no se reconoció como una oferta; probablemente cambió el diseño de la aplicación. Toca 'Compartir este reporte' y envíalo."
            else "Todo lo que el asistente leyó en la última pantalla."
            textSize = Ios.T_FOOTNOTE; setTextColor(p.label2)
            setPadding(0, 0, 0, dp(ctx, 10f))
        })
        snap.texts.forEach { t ->
            col.addView(TextView(ctx).apply {
                text = "· $t"
                textSize = Ios.T_FOOTNOTE; setTextColor(p.label)
                setPadding(0, dp(ctx, 2f), 0, dp(ctx, 2f))
            })
        }
        // Compartir el volcado de nodos en bruto — marcadores de [tap]/view-id incluidos — para que un
        // fallo de análisis pueda reportarse como texto y no solo con una captura.
        col.addView(TextView(ctx).apply {
            text = "Compartir este reporte"
            textSize = Ios.T_BODY; setTypeface(typeface, Typeface.BOLD)
            setTextColor(p.tint); gravity = android.view.Gravity.CENTER
            setPadding(0, dp(ctx, 14f), 0, dp(ctx, 6f))
            setOnClickListener {
                val body = buildString {
                    append("$name · outcome=${snap.outcome}\n")
                    append("pkg=${
                        watchedApps.firstOrNull { it.first == name }?.second ?: "?"
                    }\n")
                    append("app=${ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName}\n\n")
                    snap.texts.forEach { append("· $it\n") }
                }
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, body)
                }
                ctx.startActivity(Intent.createChooser(send, "Compartir diagnóstico"))
            }
        })
        sheet.add(col).show()
    }

    private fun automationCard(): View {
        val card = Sections.card(ctx, p)
        val (r1, _) = Sections.switchRow(ctx, p, "Monitoreo",
            "Evaluar ofertas a medida que aparecen", settings.enabled) {
            settings.enabled = it
            // Los eventos de nuestra propia interfaz nunca llegan al filtro de eventos del servicio, así que
            // cerramos la capa flotante explícitamente en lugar de esperar al
            // siguiente fotograma de la aplicación del conductor.
            if (!it) GigFlowAccessibilityService.instance?.stopWatching()
        }
        val (r2, _) = Sections.switchRow(ctx, p, "Auto-aceptar buenas ofertas",
            "Solo cuando se cumplan todas las reglas", settings.autoAccept) { settings.autoAccept = it }
        val (r3, _) = Sections.switchRow(ctx, p, "Auto-rechazar malas ofertas",
            "Solo cuando fallen la mayoría de las reglas", settings.autoDecline) { settings.autoDecline = it }
        val (r4, _) = Sections.switchRow(ctx, p, "Alertas de voz",
            "Decir los resultados en voz alta cuando aparezcan ofertas", settings.voiceAlerts) { settings.voiceAlerts = it }
        val (r5, _) = Sections.switchRow(ctx, p, "Auto-rastrear kilometraje",
            "Iniciar un turno GPS al abrir una aplicación de conductor", settings.autoTrackShift) {
            settings.autoTrackShift = it
        }
        val (r6, _) = Sections.switchRow(ctx, p, "Auto-registrar ganancias",
            "Guardar pagos desde las pantallas de resumen post-viaje", settings.autoLogEarnings) {
            settings.autoLogEarnings = it
        }
        card.addView(r1); card.addView(Sections.separator(ctx, p))
        card.addView(r2); card.addView(Sections.separator(ctx, p))
        card.addView(r3); card.addView(Sections.separator(ctx, p))
        card.addView(r4); card.addView(Sections.separator(ctx, p))
        card.addView(r5); card.addView(Sections.separator(ctx, p))
        card.addView(r6)

        // Cuenta regresiva de auto-aceptación — ventana de 5 segundos de Mystro, cancelable.
        card.addView(Sections.separator(ctx, p))
        card.addView(Sections.row(ctx, p,
            title = "Cuenta regresiva de auto-aceptación",
            subtitle = "Toca la cuenta regresiva en la capa flotante para cancelar",
            iconGlyph = "bolt", iconTint = p.orange,
        ))
        card.addView(LinearLayout(ctx).apply {
            setPadding(dp(ctx, 16f), 0, dp(ctx, 16f), dp(ctx, 14f))
            val delays = listOf(3, 5, 8, 10)
            addView(IosSegmented(ctx, p,
                delays.map { "${it}s" },
                initial = delays.indexOf(settings.autoAcceptDelaySec).coerceAtLeast(0)
            ).apply { onSelected = { settings.autoAcceptDelaySec = delays[it] } },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        })
        val (rRes, _) = Sections.switchRow(ctx, p, "Incluir ofertas reservadas",
            "El trabajo programado también se puede auto-aceptar", settings.autoAcceptReservations) {
            settings.autoAcceptReservations = it
        }
        card.addView(rRes)
        // Prueba de funcionamiento de todo el proceso: evaluación → capa flotante → voz. Sin registros,
        // sin toques — seguro de usar mientras está estacionado.
        card.addView(Sections.separator(ctx, p))
        card.addView(Sections.row(ctx, p,
            title = "Enviar una oferta de prueba",
            subtitle = if (OverlayController.isServiceEnabled(ctx))
                "Muestra la tarjeta de resultado + cuenta regresiva sin una oferta real"
            else "Activa el servicio primero — luego esto mostrará la capa flotante",
            iconGlyph = "bolt", iconTint = p.tint,
            chevron = OverlayController.isServiceEnabled(ctx),
        ) {
            val svc = GigFlowAccessibilityService.instance
            if (svc != null) svc.testOffer()
            else Toast.makeText(ctx, "Activa primero el servicio de accesibilidad", Toast.LENGTH_SHORT).show()
        })
        c_footer(card, "GigFlow solo toca cuando el botón se encuentra claramente. Nunca toca otras aplicaciones.")
        return card
    }

    private fun rulesCard(): View {
        val card = Sections.card(ctx, p)
        val isKm = settings.distanceUnit == "KM"
        val cur = settings.currencySymbol
        val curEntry = Currencies.forCode(settings.currencyCode)
        val rCur = Sections.row(ctx, p,
            title = "Moneda",
            subtitle = curEntry?.name ?: "Símbolo personalizado",
            value = if (curEntry != null) "${curEntry.symbol} · ${curEntry.code}"
                    else settings.currencySymbol,
            iconGlyph = "globe", iconTint = p.tint, chevron = true,
        ) { pickCurrency() }
        val (rMile, mile) = Sections.formField(ctx, p,
            "Mín $cur / ${if (isKm) "km" else "milla"}",
            "%.2f".format(if (isKm) settings.minPerMileCents / 1.609344 / 100.0 else settings.minPerMileCents / 100.0))
        val (rHour, hour) = Sections.formField(ctx, p, "Mín $cur / hora", "%.2f".format(settings.minPerHourCents / 100.0))
        val (rPayout, payout) = Sections.formField(ctx, p, "Mínimo pago", "%.2f".format(settings.minPayoutCents / 100.0), hint = cur)
        val (rDist, dist) = Sections.formField(ctx, p, "Distancia máxima (${if (isKm) "km" else "mi"})",
            "%.1f".format(if (isKm) settings.maxDistanceKm else settings.maxDistanceKm * 0.621371))
        card.addView(rCur); card.addView(Sections.separator(ctx, p))
        card.addView(rMile); card.addView(Sections.separator(ctx, p))
        card.addView(rHour); card.addView(Sections.separator(ctx, p))
        card.addView(rPayout); card.addView(Sections.separator(ctx, p))
        card.addView(rDist); card.addView(Sections.separator(ctx, p))
        card.addView(LinearLayout(ctx).apply {
            setPadding(dp(ctx, 12f), dp(ctx, 12f), dp(ctx, 12f), dp(ctx, 12f))
            val btn = iosButton(ctx, p, "Guardar reglas")
            addView(btn, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            btn.setOnClickListener {
                val perDist = (mile.text.toString().toDoubleOrNull() ?: 1.5) * 100
                settings.minPerMileCents = (if (isKm) perDist * 1.609344 else perDist).toInt()
                settings.minPerHourCents = ((hour.text.toString().toDoubleOrNull() ?: 20.0) * 100).toInt()
                settings.minPayoutCents = ((payout.text.toString().toDoubleOrNull() ?: 4.0) * 100).toInt()
                settings.maxDistanceKm = (dist.text.toString().toDoubleOrNull() ?: 25.0).let {
                    if (isKm) it else it / 0.621371
                }
                Toast.makeText(ctx, "Guardado", Toast.LENGTH_SHORT).show()
            }
        })
        c_footer(card, "Los umbrales están en tu moneda — los conductores en Forintos configuran montos en HUF (p. ej. 600 Ft/km).")
        return card
    }

    private fun aboutCard(): View {
        val card = Sections.card(ctx, p)
        card.addView(Sections.row(ctx, p,
            title = "Aplicaciones vigiladas",
            value = "${ctx.resources.getStringArray(app.gigflow.driver.R.array.watched_packages).size} aplicaciones de conductor",
            iconGlyph = "grid", iconTint = p.tint,
            chevron = true,
        ) { showWatchedApps() })
        card.addView(Sections.separator(ctx, p))
        card.addView(Sections.row(ctx, p,
            title = "Política de privacidad",
            iconGlyph = "doc", iconTint = p.tint,
            chevron = true,
        ) { openUrl("https://github.com/w0wzahh/GigFlow/blob/master/docs/legal/privacy-policy.md") })
        card.addView(Sections.separator(ctx, p))
        card.addView(Sections.row(ctx, p,
            title = "Términos de servicio",
            iconGlyph = "doc", iconTint = p.tint,
            chevron = true,
        ) { openUrl("https://github.com/w0wzahh/GigFlow/blob/master/docs/legal/terms-of-service.md") })
        card.addView(Sections.separator(ctx, p))
        card.addView(Sections.row(ctx, p,
            title = "Buscar actualizaciones",
            subtitle = "Revisa los Lanzamientos de GitHub en busca de un APK más nuevo",
            iconGlyph = "globe", iconTint = p.tint,
            chevron = true,
        ) {
            UpdateChecker.check(ctx) { rel ->
                if (rel != null) UpdateChecker.showUpdateSheet(ctx, p, rel)
                else Toast.makeText(ctx,
                    "Estás actualizado (v${UpdateChecker.currentVersion(ctx)})",
                    Toast.LENGTH_SHORT).show()
            }
        })
        card.addView(Sections.separator(ctx, p))
        card.addView(Sections.row(ctx, p,
            title = "Versión",
            value = try {
                ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName
            } catch (_: Exception) { "?" },
            iconGlyph = "doc", iconTint = p.gray,
        ))
        c_footer(card, "Todo se queda en el dispositivo. GigFlow no está afiliado con ninguna plataforma de gigs.")
        return card
    }

    /** Unidades, tiempo de la capa flotante, háptica, gestión de registros. */
    private fun preferencesCard(): View {
        val card = Sections.card(ctx, p)

        // Unidad de distancia — segmentado
        card.addView(prefSegmentedRow("Unidades", listOf("Millas", "Kilómetros"),
            if (settings.distanceUnit == "KM") 1 else 0) {
            settings.distanceUnit = if (it == 1) "KM" else "MI"
        })
        card.addView(Sections.separator(ctx, p))

        // Tiempo de visibilidad de la capa flotante — segmentado
        val secs = listOf(0, 10, 30, 60)
        card.addView(prefSegmentedRow("La capa se muestra durante",
            listOf("Hasta que desaparezca", "10 s", "30 s", "60 s"),
            secs.indexOf(settings.overlaySeconds).coerceAtLeast(0)) {
            settings.overlaySeconds = secs[it]
        })
        card.addView(Sections.separator(ctx, p))

        // Respuesta háptica
        val (rHap, _) = Sections.switchRow(ctx, p, "Respuesta háptica",
            "Vibrar al tocar y cambiar interruptores", settings.haptics) { settings.haptics = it }
        card.addView(rHap)
        card.addView(Sections.separator(ctx, p))

        // Borrar registro de ofertas — destructivo
        card.addView(Sections.row(ctx, p,
            title = "Borrar registro de ofertas",
            subtitle = "Elimina el historial local de ofertas evaluadas",
            iconGlyph = "trash", iconTint = p.red,
        ) {
            OfferLog.clear(ctx)
            Toast.makeText(ctx, "Registro de ofertas borrado", Toast.LENGTH_SHORT).show()
        })
        return card
    }

    private fun prefSegmentedRow(
        label: String, options: List<String>, initial: Int, onPick: (Int) -> Unit,
    ): View {
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(ctx, 16f), dp(ctx, 10f), dp(ctx, 16f), dp(ctx, 12f))
            addView(TextView(ctx).apply {
                text = label; textSize = Ios.T_SUBHEAD; setTextColor(p.label2)
                setPadding(0, 0, 0, dp(ctx, 8f))
            })
            addView(IosSegmented(ctx, p, options, initial = initial).apply {
                onSelected = onPick
            })
        }
    }

    private val watchedApps = listOf(
        "Uber Driver" to "com.uber.driver",
        "Lyft Driver" to "com.lyft.driver",
        "Dasher" to "com.doordash.driverapp",
        "Instacart Shopper" to "com.instacart.shopper",
        "Amazon Flex" to "com.amazon.rabbit",
        "Spark Driver" to "com.walmart.sparkdriver",
        "Wolt Courier Partner" to "com.wolt.courierapp",
        "foodora rider" to "com.logistics.rider.foodora",
    )

    /** Filtros por servicio al estilo Mystro — una fila por cada aplicación vigilada. */
    private fun perAppCard(): View {
        val card = Sections.card(ctx, p)
        watchedApps.forEachIndexed { i, (name, pkg) ->
            if (i > 0) card.addView(Sections.separator(ctx, p))
            val overridden = settings.platformOverride(pkg) != null
            card.addView(Sections.row(ctx, p,
                title = name,
                value = if (overridden) "Personalizado" else null,
                iconGlyph = "slider", iconTint = if (overridden) p.orange else p.gray,
                chevron = true,
            ) { editAppRules(name, pkg) })
        }
        return card
    }

    /** Hoja de anulación: mismos campos de reglas, específicos para una aplicación de conductor. */
    private fun editAppRules(name: String, pkg: String) {
        val sheet = IosSheet(ctx, p)
        val isKm = settings.distanceUnit == "KM"
        val cur = settings.currencySymbol
        val eff = settings.rulesFor(pkg) // reglas efectivas: anulación ?? global

        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(ctx, 20f), dp(ctx, 4f), dp(ctx, 20f), dp(ctx, 16f))
            addView(TextView(ctx).apply {
                text = name
                textSize = Ios.T_HEADLINE; setTypeface(typeface, Typeface.BOLD); setTextColor(p.label)
            })
            addView(TextView(ctx).apply {
                text = "Anula las reglas globales para ofertas de esta aplicación."
                textSize = Ios.T_FOOTNOTE; setTextColor(p.label2)
                setPadding(0, dp(ctx, 2f), 0, dp(ctx, 12f))
            })
        }

        val (rA, swA) = Sections.switchRow(ctx, p, "Auto-aceptar buenas ofertas",
            "Aplica la cuenta regresiva", eff.autoAccept) { }
        val (rD, swD) = Sections.switchRow(ctx, p, "Auto-rechazar malas ofertas",
            "Inmediato", eff.autoDecline) { }
        col.addView(rA); col.addView(Sections.separator(ctx, p)); col.addView(rD)
        col.addView(Sections.separator(ctx, p))

        val (r1, mile) = Sections.formField(ctx, p,
            "Mín $cur / ${if (isKm) "km" else "milla"}",
            "%.2f".format(if (isKm) eff.minPerMileCents / 1.609344 / 100.0 else eff.minPerMileCents / 100.0))
        val (r2, hour) = Sections.formField(ctx, p, "Mín $cur / hora", "%.2f".format(eff.minPerHourCents / 100.0))
        val (r3, payout) = Sections.formField(ctx, p, "Mínimo pago", "%.2f".format(eff.minPayoutCents / 100.0), hint = cur)
        val (r4, dist) = Sections.formField(ctx, p, "Distancia máxima (${if (isKm) "km" else "mi"})",
            "%.1f".format(if (isKm) eff.maxDistanceKm else eff.maxDistanceKm * 0.621371))
        col.addView(r1); col.addView(Sections.separator(ctx, p))
        col.addView(r2); col.addView(Sections.separator(ctx, p))
        col.addView(r3); col.addView(Sections.separator(ctx, p))
        col.addView(r4)
        sheet.add(col)

        val save = iosButton(ctx, p, "Guardar anulaciones")
        val reset = iosButton(ctx, p, "Restablecer a reglas globales", p.fill)
        reset.setTextColor(p.label)
        sheet.add(LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(ctx, 16f), 0, dp(ctx, 16f), 0)
            addView(save); addView(reset)
            (reset.layoutParams as? LinearLayout.LayoutParams)?.topMargin = dp(ctx, 8f)
        })

        save.setOnClickListener {
            val dpm = mile.text.toString().toDoubleOrNull() ?: return@setOnClickListener bad("Mínimo por distancia")
            val dph = hour.text.toString().toDoubleOrNull() ?: return@setOnClickListener bad("Mínimo por hora")
            val pay = payout.text.toString().toDoubleOrNull() ?: return@setOnClickListener bad("Mínimo pago")
            val d = dist.text.toString().toDoubleOrNull() ?: return@setOnClickListener bad("Distancia máxima")
            settings.setPlatformOverride(pkg, JSONObject()
                .put("autoAccept", swA.isChecked)
                .put("autoDecline", swD.isChecked)
                .put("minPerMileCents", ((if (isKm) dpm * 1.609344 else dpm) * 100).toInt())
                .put("minPerHourCents", (dph * 100).toInt())
                .put("minPayoutCents", (pay * 100).toInt())
                .put("maxDistanceKm", if (isKm) d else d * 1.609344)
            )
            Toast.makeText(ctx, "Reglas de $name guardadas", Toast.LENGTH_SHORT).show()
            sheet.dismiss(); refresh()
        }
        reset.setOnClickListener {
            settings.clearPlatformOverride(pkg)
            sheet.dismiss(); refresh()
        }
        sheet.show()
    }

    private fun bad(msg: String) {
        Toast.makeText(ctx, "$msg debe ser un número", Toast.LENGTH_SHORT).show()
    }

    /** Todas las monedas del registro, más una opción de símbolo personalizado. */
    private fun pickCurrency() {
        val sheet = IosSheet(ctx, p)
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(ctx, 20f), dp(ctx, 4f), dp(ctx, 20f), dp(ctx, 10f))
            addView(TextView(ctx).apply {
                text = "Moneda"
                textSize = Ios.T_HEADLINE
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(p.label)
            })
            addView(TextView(ctx).apply {
                text = "Se usa para los umbrales de reglas, montos y alertas de voz. El asistente busca el símbolo y código de esta moneda en las tarjetas de oferta."
                textSize = Ios.T_FOOTNOTE
                setTextColor(p.label2)
                setPadding(0, dp(ctx, 2f), 0, 0)
            })
        }
        sheet.add(col)

        val list = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(ctx, 8f))
        }
        Currencies.ALL.forEach { c ->
            list.addView(currencyRow(c, sheet))
        }
        list.addView(Sections.separator(ctx, p))
        list.addView(Sections.row(ctx, p,
            title = "Símbolo personalizado…",
            subtitle = "Escribe cualquier símbolo — las ofertas también coinciden con él",
            iconGlyph = "plus.circle", iconTint = p.gray,
            chevron = true,
        ) { sheet.dismiss(); pickCustomCurrency() })

        val scroll = android.widget.ScrollView(ctx).apply {
            isVerticalScrollBarEnabled = false
            addView(list)
        }
        sheet.add(scroll)
        // Limitar la lista para que la hoja siga viéndose como una hoja.
        scroll.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(ctx, 380f))
        sheet.show()
    }

    private fun currencyRow(c: Currencies.Entry, sheet: IosSheet): View {
        val selected = settings.currencyCode == c.code
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(ctx, 20f), dp(ctx, 8f), dp(ctx, 20f), dp(ctx, 8f))
        }
        row.addView(TextView(ctx).apply {
            text = c.symbol
            textSize = Ios.T_SUBHEAD
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(if (selected) p.tint else p.label)
            gravity = android.view.Gravity.CENTER
            background = Ios.rounded(7f,
                if (selected) p.card2 else p.fill, ctx)
        }, LinearLayout.LayoutParams(dp(ctx, 46f), dp(ctx, 30f)).apply {
            rightMargin = dp(ctx, 12f)
        })
        row.addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(ctx).apply {
                text = c.name; textSize = Ios.T_BODY; setTextColor(p.label)
            })
            addView(TextView(ctx).apply {
                text = c.code; textSize = Ios.T_CAPTION; setTextColor(p.label3)
            })
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        if (selected) {
            row.addView(Icons.view(ctx, "checkmark", p.tint, 16f))
        }
        row.pressable()
        row.setOnClickListener {
            row.haptic()
            settings.currencyCode = c.code
            sheet.dismiss(); refresh()
        }
        return row
    }

    /** Opción alternativa para símbolos fuera del registro. */
    private fun pickCustomCurrency() {
        val sheet = IosSheet(ctx, p)
        val field = android.widget.EditText(ctx).apply {
            hint = "p. ej. ¥, R$, din"
            setHintTextColor(p.label3)
            setTextColor(p.label)
            textSize = Ios.T_BODY
            setText(settings.currencySymbol)
            setSingleLine()
            setPadding(dp(ctx, 16f), dp(ctx, 12f), dp(ctx, 16f), dp(ctx, 12f))
            background = Ios.rounded(Ios.R_BUTTON, p.fill, ctx)
        }
        sheet.add(LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(ctx, 20f), dp(ctx, 4f), dp(ctx, 20f), dp(ctx, 12f))
            addView(TextView(ctx).apply {
                text = "Moneda personalizada"
                textSize = Ios.T_HEADLINE
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(p.label)
                setPadding(0, 0, 0, dp(ctx, 10f))
            })
            addView(field)
        })
        val save = iosButton(ctx, p, "Usar este símbolo")
        sheet.add(LinearLayout(ctx).apply {
            setPadding(dp(ctx, 16f), 0, dp(ctx, 16f), 0)
            addView(save)
        })
        save.setOnClickListener {
            val sym = field.text.toString().trim()
            if (sym.isEmpty()) {
                Toast.makeText(ctx, "Introduce un símbolo", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            settings.currencySymbol = sym
            sheet.dismiss(); refresh()
        }
        sheet.show()
    }

    private fun showWatchedApps() {
        val sheet = IosSheet(ctx, p)
        val names = watchedApps
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(ctx, 20f), dp(ctx, 4f), dp(ctx, 20f), dp(ctx, 12f))
        }
        col.addView(TextView(ctx).apply {
            text = "Aplicaciones vigiladas"
            textSize = Ios.T_HEADLINE
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(p.label)
            setPadding(0, 0, 0, dp(ctx, 10f))
        })
        names.forEach { (name, pkg) ->
            col.addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dp(ctx, 6f), 0, dp(ctx, 6f))
                addView(TextView(ctx).apply {
                    text = name; textSize = Ios.T_BODY; setTextColor(p.label)
                })
                addView(TextView(ctx).apply {
                    text = pkg; textSize = Ios.T_CAPTION; setTextColor(p.label3)
                })
            })
        }
        col.addView(TextView(ctx).apply {
            text = "El asistente solo lee estas aplicaciones. No puede ver nada más en tu teléfono."
            textSize = Ios.T_FOOTNOTE; setTextColor(p.label2)
            setPadding(0, dp(ctx, 8f), 0, 0)
        })
        sheet.add(col).show()
    }

    private fun creditsCard(): View {
        val card = Sections.card(ctx, p)
        card.addView(Sections.row(ctx, p,
            title = "Creado por w0wzahh",
            subtitle = "Código abierto · comentarios bienvenidos",
            iconGlyph = "person", iconTint = p.tint,
        ))
        card.addView(Sections.separator(ctx, p))
        card.addView(Sections.row(ctx, p,
            title = "GitHub",
            subtitle = "github.com/w0wzahh",
            iconGlyph = "link", iconTint = p.gray,
            chevron = true,
        ) { openUrl("https://github.com/w0wzahh") })
        card.addView(Sections.separator(ctx, p))
        card.addView(Sections.row(ctx, p,
            title = "Apoya en Ko-fi",
            subtitle = "Invítale un café al desarrollador — ko-fi.com/w0wzahh",
            iconGlyph = "heart", iconTint = p.red,
            chevron = true,
        ) { openUrl("https://ko-fi.com/w0wzahh") })
        return card
    }

    private fun openUrl(url: String) {
        try {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))
        } catch (_: Exception) {
            Toast.makeText(ctx, url, Toast.LENGTH_SHORT).show()
        }
    }

    private fun c_footer(card: LinearLayout, text: String) {
        card.addView(TextView(ctx).apply {
            this.text = text
            textSize = Ios.T_CAPTION
            setTextColor(p.label3)
            setPadding(dp(ctx, 16f), dp(ctx, 6f), dp(ctx, 16f), dp(ctx, 10f))
        })
    }
}