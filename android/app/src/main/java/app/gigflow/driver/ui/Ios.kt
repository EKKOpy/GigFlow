package app.gigflow.driver.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.animation.Interpolator

/**
 * Tokens de diseño inspirados en iOS para la aplicación complementaria. Reflejan la paleta
 * de sistemas de Apple (claro + oscuro), escala tipográfica, radios y curvas de animación — implementados
 * a mano en vistas de Android nativas, sin dependencias.
 */
object Ios {

    // --- Movimiento -------------------------------------------------------------
    // Resorte con amortiguación crítica simulada: ligero rebasamiento y luego estabilización, similar al
    // resorte por defecto de UIKit (relación de amortiguación ~0.8).
    val SPRING: Interpolator = Interpolator { t ->
        val s = 1.70158f * 1.3f
        val x = t - 1f
        x * x * ((s + 1f) * x + s) + 1f
    }
    val EASE_OUT: Interpolator = android.view.animation.DecelerateInterpolator(1.8f)
    const val ANIM_MS = 320L
    const val ANIM_FAST = 180L

    /** Interruptor global reflejado desde SettingsRepository.haptics. */
    @Volatile var hapticsEnabled = true

    fun View.haptic() {
        if (hapticsEnabled) performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    }

    fun View.pressable(scale: Float = 0.965f) {
        setOnTouchListener { v, e ->
            when (e.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    v.animate().scaleX(scale).scaleY(scale).alpha(0.85f)
                        .setDuration(ANIM_FAST).setInterpolator(EASE_OUT).start()
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    v.animate().scaleX(1f).scaleY(1f).alpha(1f)
                        .setDuration(ANIM_MS).setInterpolator(SPRING).start()
                }
            }
            false
        }
    }

    // --- Paleta -------------------------------------------------------------
    class Palette(val isDark: Boolean) {
        val bg: Int
        val groupedBg: Int // fondo detrás de listas agrupadas
        val card: Int      // secondarySystemGroupedBackground
        val card2: Int     // tertiarySystemGroupedBackground / fill
        val fill: Int      // relleno cuaternario (pista de elementos segmentados)
        val label: Int
        val label2: Int
        val label3: Int
        val separator: Int
        val tint: Int
        val green: Int
        val red: Int
        val orange: Int
        val yellow: Int
        val gray: Int
        val sheetHandle: Int

        init {
            if (isDark) {
                bg = Color.parseColor("#000000")
                groupedBg = Color.parseColor("#000000")
                card = Color.parseColor("#1C1C1E")
                card2 = Color.parseColor("#2C2C2E")
                fill = Color.parseColor("#3A3A3C")
                label = Color.parseColor("#FFFFFF")
                label2 = Color.parseColor("#99EBEBF5")
                label3 = Color.parseColor("#4DEBEBF5")
                separator = Color.parseColor("#545458")
                tint = Color.parseColor("#0A84FF")
                green = Color.parseColor("#30D158")
                red = Color.parseColor("#FF453A")
                orange = Color.parseColor("#FF9F0A")
                yellow = Color.parseColor("#FFD60A")
                gray = Color.parseColor("#8E8E93")
                sheetHandle = Color.parseColor("#5A5A5E")
            } else {
                bg = Color.parseColor("#FFFFFF")
                groupedBg = Color.parseColor("#F2F2F7")
                card = Color.parseColor("#FFFFFF")
                card2 = Color.parseColor("#F2F2F7")
                fill = Color.parseColor("#E9E9EB")
                label = Color.parseColor("#000000")
                label2 = Color.parseColor("#993C3C43")
                label3 = Color.parseColor("#4D3C3C43")
                separator = Color.parseColor("#3C3C4360")
                tint = Color.parseColor("#007AFF")
                green = Color.parseColor("#34C759")
                red = Color.parseColor("#FF3B30")
                orange = Color.parseColor("#FF9500")
                yellow = Color.parseColor("#FFCC00")
                gray = Color.parseColor("#8E8E93")
                sheetHandle = Color.parseColor("#C5C5C9")
            }
        }
    }

    fun palette(ctx: Context): Palette {
        val night = (ctx.resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        return Palette(night)
    }

    // --- Métricas --------------------------------------------------------------
    fun dp(ctx: Context, v: Float) = (v * ctx.resources.displayMetrics.density).toInt()
    fun sp(ctx: Context, v: Float) = v * ctx.resources.displayMetrics.scaledDensity

    const val R_CARD = 13f   // tarjeta agrupada de iOS
    const val R_PILL = 20f
    const val R_BUTTON = 12f
    const val R_SHEET = 14f

    // Escala tipográfica (sp) — estilos de texto de Apple
    const val T_LARGE = 34f
    const val T_TITLE2 = 22f
    const val T_HEADLINE = 17f
    const val T_BODY = 17f
    const val T_SUBHEAD = 15f
    const val T_FOOTNOTE = 13f
    const val T_CAPTION = 12f

    // --- Auxiliares de dibujo ------------------------------------------------------
    fun rounded(radiusDp: Float, color: Int, ctx: Context) = GradientDrawable().apply {
        cornerRadius = dp(ctx, radiusDp).toFloat()
        setColor(color)
    }

    fun stroke(radiusDp: Float, color: Int, strokeColor: Int, ctx: Context) = GradientDrawable().apply {
        cornerRadius = dp(ctx, radiusDp).toFloat()
        setColor(color)
        setStroke(dp(ctx, 0.5f).coerceAtLeast(1), strokeColor)
    }

    /** Fondo de tarjeta sombreado estilo iOS. */
    fun cardBg(p: Palette, ctx: Context) = GradientDrawable().apply {
        cornerRadius = dp(ctx, R_CARD).toFloat()
        setColor(p.card)
    }

    /** Renderizado de dinero compatible con moneda — la posición/decimales provienen del
     *  registro en [Currencies] ("1 850 Ft", "CHF 12.50", "¥1,200"). */
    fun money(cents: Int?, symbol: String = "$"): String =
        cents?.let { app.gigflow.driver.Currencies.format(it, symbol) } ?: "—"

    fun verdictColor(v: VerdictCompat, p: Palette) = when (v) {
        VerdictCompat.GOOD -> p.green
        VerdictCompat.MEH -> p.orange
        VerdictCompat.BAD -> p.red
    }

    /** Duplicado flexible del enumerador del lado del servicio para uso en la interfaz. */
    enum class VerdictCompat { GOOD, MEH, BAD }
}