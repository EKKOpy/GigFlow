package app.gigflow.driver

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Tarjeta de puntuación flotante que se muestra sobre la aplicación del conductor mientras hay una oferta en pantalla.
 * Los servicios de accesibilidad pueden añadir ventanas TYPE_ACCESSIBILITY_OVERLAY sin
 * el permiso SYSTEM_ALERT_WINDOW.
 */
class OverlayController(private val service: AccessibilityService) {

    private val wm = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var view: LinearLayout? = null
    private var countdown: TextView? = null
    private var expiry: TextView? = null
    private val tickerHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var expiryTicker: Runnable? = null

    /**
     * Descuenta el propio temporizador de expiración de la oferta mostrado en la tarjeta. Al llegar a 0
     * se redirige a través de [onExpire] (en lugar de un simple ocultamiento) para que el servicio pueda cancelar
     * cualquier auto-aceptación pendiente en lugar de tocar una tarjeta que acaba de expirar.
     */
    private fun tickExpiry(startSec: Int, onExpire: () -> Unit) {
        expiryTicker?.let { tickerHandler.removeCallbacks(it) }
        var left = startSec
        val r = object : Runnable {
            override fun run() {
                left--
                if (left <= 0) { onExpire(); return }
                expiry?.text = "Expira en ${left}s"
                tickerHandler.postDelayed(this, 1000)
            }
        }
        expiryTicker = r
        tickerHandler.postDelayed(r, 1000)
    }

    private fun money(cents: Int?, symbol: String = "$") =
        app.gigflow.driver.ui.Ios.money(cents, symbol)

    /** Actualiza la línea de cuenta regresiva de auto-aceptación, si se muestra. */
    fun setCountdown(secondsLeft: Int) {
        countdown?.text = "Autoaceptando en ${secondsLeft}s · toca para cancelar"
    }

    fun show(scored: ScoredOffer, autoAcceptSec: Int = 0, onAction: (Action) -> Unit) {
        hide()
        val o = scored.offer
        val (bg, fgColor, label) = when (scored.verdict) {
            Verdict.GOOD -> Triple(Color.parseColor("#16915A"), Color.WHITE, "BUENA OFERTA")
            Verdict.MEH -> Triple(Color.parseColor("#B07A0D"), Color.WHITE, "LÍMITE")
            Verdict.BAD -> Triple(Color.parseColor("#D2432E"), Color.WHITE, "OMITIR")
        }

        val density = service.resources.displayMetrics.density
        fun Int.dp() = (this * density).toInt()

        val container = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dp(), 10.dp(), 16.dp(), 12.dp())
            background = GradientDrawable().apply {
                cornerRadius = 14.dp().toFloat()
                setColor(Color.parseColor("#F214191F"))
            }
            elevation = 8.dp().toFloat()
        }

        val title = TextView(service).apply {
            text = (if (o.isReservation) "RESERVADO · " else "") +
                "$label  ·  ${money(o.payoutCents, o.currencySymbol)}"
            setTextColor(fgColor)
            textSize = 15f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            background = GradientDrawable().apply {
                cornerRadius = 8.dp().toFloat()
                setColor(bg)
                setPadding(10.dp(), 4.dp(), 10.dp(), 4.dp())
            }
        }
        val isKm = SettingsRepository(service).distanceUnit == "KM"
        val cur = o.currencySymbol
        val perDist = scored.perMileCents?.let { if (isKm) (it / 1.609344).toInt() else it }
        val stats = TextView(service).apply {
            text = "${money(perDist, cur)}/${if (isKm) "km" else "mi"} · ${money(scored.perHourCents, cur)}/hr" +
                o.distanceKm?.let {
                    " · ${"%.1f".format(if (isKm) it else it * 0.621371)} ${if (isKm) "km" else "mi"}"
                }.orEmpty() +
                o.durationMin?.let { " · ${it.toInt()} min" }.orEmpty() +
                if (scored.reasons.isNotEmpty()) "\n${scored.reasons.joinToString(", ")}" else ""
            setTextColor(Color.parseColor("#D7DBE4"))
            textSize = 12.5f
            setPadding(0, 8.dp(), 0, 0)
        }
        container.addView(title)
        container.addView(stats)

        if (o.acceptNode != null || o.declineNode != null) {
            val row = LinearLayout(service).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 10.dp(), 0, 0)
            }
            fun pill(text: String, color: Int, act: Action) = Button(service).apply {
                this.text = text
                textSize = 12f
                setTextColor(Color.WHITE)
                isAllCaps = false
                background = GradientDrawable().apply {
                    cornerRadius = 18.dp().toFloat()
                    setColor(color)
                }
                setOnClickListener { onAction(act) }
            }
            o.declineNode?.let {
                row.addView(pill("Rechazar", Color.parseColor("#4A5260"), Action.DECLINE))
            }
            o.acceptNode?.let {
                row.addView(
                    pill("Aceptar", Color.parseColor("#0B8A80"), Action.ACCEPT),
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ).apply { marginStart = 8.dp() },
                )
            }
            container.addView(row)
        }

        // Propio temporizador de expiración de la oferta, si la tarjeta mostraba uno ("0:15", "12s").
        if (autoAcceptSec <= 0 && o.expiresInSec != null) {
            expiry = TextView(service).apply {
                text = "Expira en ${o.expiresInSec}s"
                setTextColor(Color.parseColor("#8E8E93"))
                textSize = 12f
                setPadding(0, 8.dp(), 0, 0)
            }
            container.addView(expiry)
            tickExpiry(o.expiresInSec) { onAction(Action.EXPIRED) }
        }

        // Franja de cuenta regresiva al estilo Mystro: la auto-aceptación se activa al llegar a 0,
        // tocarla cancela la acción y deja la oferta intacta.
        if (autoAcceptSec > 0) {
            countdown = TextView(service).apply {
                text = "Autoaceptando en ${autoAcceptSec}s · toca para cancelar"
                setTextColor(Color.parseColor("#8BC5FF"))
                textSize = 12.5f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(0, 8.dp(), 0, 0)
                setOnClickListener { onAction(Action.CANCEL_AUTO) }
            }
            container.addView(countdown)
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 24.dp()
        }

        try {
            wm.addView(container, params)
            view = container
        } catch (_: Exception) {
            // La capa flotante puede fallar si el servicio aún no está completamente vinculado.
        }
    }

    fun hide() {
        expiryTicker?.let { tickerHandler.removeCallbacks(it) }
        expiryTicker = null
        view?.let {
            try { wm.removeView(it) } catch (_: Exception) {}
        }
        view = null
        countdown = null
        expiry = null
    }

    enum class Action { ACCEPT, DECLINE, CANCEL_AUTO, EXPIRED }

    companion object {
        /** Comprueba si el servicio está realmente habilitado — utilizado por MainActivity. */
        fun isServiceEnabled(context: Context): Boolean {
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
            val services = am.getEnabledAccessibilityServiceList(
                android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_GENERIC,
            )
            return services.any {
                it.resolveInfo.serviceInfo.packageName == context.packageName
            }
        }
    }
}