package app.gigflow.driver.ui

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import app.gigflow.driver.*
import app.gigflow.driver.ui.Ios.Palette
import app.gigflow.driver.ui.Ios.dp
import org.json.JSONObject
import java.util.*

/**
 * Plan — bloques de trabajo semanales recurrentes: elige un día, establece una hora de inicio/fin
 * y una meta de ganancias opcional. Se guarda localmente.
 */
class PlanScreen(
    ctx: Context,
    private val p: Palette,
    private val db: LocalDb,
    private val settings: SettingsRepository,
) {
    val screen = Screen(ctx, p, "Horario")
    private val ctx: Context = ctx

    private fun money(cents: Int?) = Ios.money(cents, settings.currencySymbol)
    private var pickedDay = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 1 // 0=Dom
    private var dayBtns = mutableListOf<TextView>()

    private val dayNames = listOf("Dom", "Lun", "Mar", "Mié", "Jue", "Vie", "Sáb")

    fun refresh() {
        screen.column.removeAllViews()
        val c = screen.column

        c.addView(Sections.header(ctx, p, "Nuevo turno"))
        c.addView(formCard())
        c.addView(Sections.header(ctx, p, "Esta semana"))
        c.addView(listCard())
        c.addView(TextView(ctx).apply {
            text = "Los bloques recurrentes se repiten cada semana. Toca uno para eliminarlo."
            textSize = Ios.T_FOOTNOTE; setTextColor(p.label3)
            setPadding(dp(ctx, 20f), dp(ctx, 6f), dp(ctx, 16f), dp(ctx, 16f))
        })
        screen.animateIn()
    }

    private fun formCard(): View {
        val card = Sections.card(ctx, p)

        val (rTitle, title) = Sections.formField(ctx, p, "Etiqueta", "", numeric = false, hint = "Hora pico nocturna")
        card.addView(rTitle); card.addView(Sections.separator(ctx, p))

        // selector de día de la semana — 7 círculos con letras
        dayBtns.clear()
        card.addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(ctx, 10f), dp(ctx, 12f), dp(ctx, 10f), dp(ctx, 12f))
            dayNames.forEachIndexed { i, d ->
                val tv = TextView(ctx).apply {
                    text = d.take(1)
                    textSize = Ios.T_SUBHEAD
                    gravity = Gravity.CENTER
                    setTypeface(typeface, Typeface.BOLD)
                    setOnClickListener { pickedDay = i; paintDays() }
                }
                addView(tv, LinearLayout.LayoutParams(dp(ctx, 40f), dp(ctx, 40f))
                    .apply { setMargins(dp(ctx, 2f), 0, dp(ctx, 2f), 0) })
                dayBtns.add(tv)
            }
        })
        paintDays()

        card.addView(Sections.separator(ctx, p))
        val (rFrom, from) = Sections.formField(ctx, p, "Desde", "17:00", numeric = false, hint = "17:00")
        val (rTo, to) = Sections.formField(ctx, p, "Hasta", "21:00", numeric = false, hint = "21:00")
        val (rTarget, target) = Sections.formField(ctx, p, "Meta", "", hint = "opcional ${settings.currencySymbol}")
        card.addView(rFrom); card.addView(Sections.separator(ctx, p))
        card.addView(rTo); card.addView(Sections.separator(ctx, p))
        card.addView(rTarget); card.addView(Sections.separator(ctx, p))

        card.addView(LinearLayout(ctx).apply {
            setPadding(dp(ctx, 12f), dp(ctx, 12f), dp(ctx, 12f), dp(ctx, 12f))
            val btn = iosButton(ctx, p, "Añadir turno")
            addView(btn, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            btn.setOnClickListener {
                val s = parseTime(from.text.toString()) ?: return@setOnClickListener bad("Desde debe estar en formato HH:MM")
                val e = parseTime(to.text.toString()) ?: return@setOnClickListener bad("Hasta debe estar en formato HH:MM")
                if (e <= s) return@setOnClickListener bad("El fin debe ser posterior al inicio")
                add(title.text.toString().ifBlank { null }, s, e,
                    (target.text.toString().toDoubleOrNull()?.times(100))?.toInt())
            }
        })
        return card
    }

    private fun paintDays() {
        dayBtns.forEachIndexed { i, tv ->
            val sel = i == pickedDay
            tv.background = if (sel) Ios.rounded(20f, p.tint, ctx) else Ios.rounded(20f, p.fill, ctx)
            tv.setTextColor(if (sel) android.graphics.Color.WHITE else p.label2)
        }
    }

    private fun parseTime(t: String): Int? {
        val m = Regex("""^\s*(\d{1,2})[:.]?(\d{2})?\s*$""").matchEntire(t) ?: return null
        val h = m.groupValues[1].toInt()
        val min = m.groupValues[2].toIntOrNull() ?: 0
        if (h > 23 || min > 59) return null
        return h * 60 + min
    }

    private fun bad(msg: String) { Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show() }

    private fun add(title: String?, startMin: Int, endMin: Int, targetCents: Int?) {
        val payload = JSONObject()
            .put("title", title)
            .put("dayOfWeek", pickedDay)
            .put("startMin", startMin)
            .put("endMin", endMin)
        targetCents?.let { payload.put("targetCents", it) }
        db.insertSchedule(payload)
        Toast.makeText(ctx, "Añadido", Toast.LENGTH_SHORT).show()
        refresh()
    }

    private fun fmt(min: Int) = "%d:%02d %s".format(
        ((min / 60 + 11) % 12) + 1, min % 60, if (min / 60 < 12) "AM" else "PM")

    private fun listCard(): View {
        val card = Sections.card(ctx, p)
        val scheds = db.schedules().sortedBy { (it.payload.optInt("dayOfWeek") + 6) % 7 * 1440 + it.payload.optInt("startMin") }
        if (scheds.isEmpty()) {
            card.addView(TextView(ctx).apply {
                text = "No hay turnos planificados — añade tu primer bloque arriba."
                textSize = Ios.T_SUBHEAD; setTextColor(p.label2)
                setPadding(dp(ctx, 16f), dp(ctx, 14f), dp(ctx, 16f), dp(ctx, 14f))
            })
            return card
        }
        scheds.forEachIndexed { i, s ->
            val pl = s.payload
            if (i > 0) card.addView(Sections.separator(ctx, p))
            card.addView(Sections.row(ctx, p,
                title = pl.optString("title").ifBlank {
                    dayNames.getOrElse(pl.optInt("dayOfWeek")) { "?" } + " turno"
                },
                subtitle = "${dayNames.getOrElse(pl.optInt("dayOfWeek")) { "?" }} · " +
                    "${fmt(pl.optInt("startMin"))} – ${fmt(pl.optInt("endMin"))}" +
                    (pl.optInt("targetCents").takeIf { it > 0 }
                        ?.let { " · meta ${money(it)}" } ?: ""),
                iconGlyph = "calendar", iconTint = p.tint,
                chevron = true,
            ) { confirmDelete(s) })
        }
        return card
    }

    private fun confirmDelete(s: LocalDb.Sched) {
        val sheet = IosSheet(ctx, p)
        val pl = s.payload
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(ctx, 20f), dp(ctx, 8f), dp(ctx, 20f), 0)
            addView(TextView(ctx).apply {
                text = pl.optString("title").ifBlank { dayNames.getOrElse(pl.optInt("dayOfWeek")) { "Turno" } }
                textSize = Ios.T_HEADLINE; setTypeface(typeface, Typeface.BOLD); setTextColor(p.label)
            })
            addView(TextView(ctx).apply {
                text = "${dayNames.getOrElse(pl.optInt("dayOfWeek")) { "" }} · ${fmt(pl.optInt("startMin"))} – ${fmt(pl.optInt("endMin"))}"
                textSize = Ios.T_FOOTNOTE; setTextColor(p.label2)
                setPadding(0, dp(ctx, 2f), 0, dp(ctx, 16f))
            })
        }
        sheet.add(col)
        val del = iosButton(ctx, p, "Eliminar turno", p.red)
        val cancel = iosButton(ctx, p, "Cancelar", p.fill)
        cancel.setTextColor(p.label)
        sheet.add(LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(ctx, 16f), 0, dp(ctx, 16f), 0)
            addView(del); addView(cancel)
            (cancel.layoutParams as? LinearLayout.LayoutParams)?.topMargin = dp(ctx, 8f)
        })
        del.setOnClickListener {
            sheet.dismiss()
            db.deleteSchedule(s.clientId)
            Toast.makeText(ctx, "Eliminado", Toast.LENGTH_SHORT).show()
            refresh()
        }
        cancel.setOnClickListener { sheet.dismiss() }
        sheet.show()
    }
}