package app.gigflow.driver.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import app.gigflow.driver.ui.Ios.Palette
import app.gigflow.driver.ui.Ios.dp

/**
 * Contenedor de pantalla al estilo iOS: título grande que se contrae en un
 * encabezado flotante compacto a medida que el contenido se desplaza por debajo.
 */
class Screen(ctx: Context, private val p: Palette, title: String) : FrameLayout(ctx) {

    val column = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
    private val scroll = ScrollView(ctx).apply {
        isVerticalScrollBarEnabled = false
        overScrollMode = View.OVER_SCROLL_NEVER
    }
    private val largeTitle = TextView(ctx).apply {
        text = title
        textSize = Ios.T_LARGE
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(p.label)
        setPadding(dp(ctx, 16f), dp(ctx, 8f), dp(ctx, 16f), dp(ctx, 4f))
    }
    private val miniBar = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = android.view.Gravity.CENTER
        setPadding(0, dp(ctx, 10f), 0, dp(ctx, 10f))
        alpha = 0f
    }
    private val miniTitle = TextView(ctx).apply {
        text = title
        textSize = Ios.T_HEADLINE
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(p.label)
    }

    init {
        val inner = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            addView(largeTitle)
            addView(column)
            setPadding(0, 0, 0, dp(ctx, 110f)) // deja espacio para la barra de pestañas flotante
        }
        scroll.addView(inner)
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        miniBar.addView(miniTitle)
        miniBar.background = Ios.stroke(0f,
            Color.argb(0, 0, 0, 0), Color.TRANSPARENT, ctx)
        addView(miniBar, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        // desvanece el título mini una vez que el título grande desaparece al desplazar
        scroll.viewTreeObserver.addOnScrollChangedListener {
            val collapsed = scroll.scrollY > dp(ctx, 44f)
            miniTitle.animate().cancel()
            miniTitle.animate().alpha(if (collapsed) 1f else 0f)
                .setDuration(Ios.ANIM_FAST).start()
            miniBar.setBackgroundColor(
                if (collapsed) Color.argb(200, Color.red(p.groupedBg), Color.green(p.groupedBg), Color.blue(p.groupedBg))
                else Color.TRANSPARENT,
            )
        }
        setBackgroundColor(p.groupedBg)
    }

    /** Entrada escalonada para el contenido recién construido. */
    fun animateIn() {
        for (i in 0 until column.childCount) {
            val v = column.getChildAt(i)
            v.alpha = 0f
            v.translationY = dp(context, 14f).toFloat()
            v.animate().alpha(1f).translationY(0f)
                .setStartDelay((i * 35).toLong().coerceAtMost(280))
                .setDuration(Ios.ANIM_MS)
                .setInterpolator(Ios.EASE_OUT)
                .start()
        }
    }
}