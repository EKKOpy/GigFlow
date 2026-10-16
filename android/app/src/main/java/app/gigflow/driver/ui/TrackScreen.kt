package app.gigflow.driver.ui

import android.app.Activity
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
import java.text.SimpleDateFormat
import java.util.*

/**
 * Rastrear — registro rápido de ganancias / gastos / kilometraje al estilo de formularios agrupados de iOS.
 * Toca un registro → hoja de acciones con Editar / Eliminar. Todo es local.
 */
class TrackScreen(
    ctx: Context,
    private val p: Palette,
    private val db: LocalDb,
    private val settings: SettingsRepository,
) {
    val screen = Screen(ctx, p, "Rastrear")
    private val ctx: Context = ctx

    private fun money(cents: Int?) = Ios.money(cents, settings.currencySymbol)
    private var kind = 0 // 0 ganancia, 1 gasto, 2 kilometraje
    private var editing: LocalDb.Row? = null

    fun refresh() {
        screen.column.removeAllViews()
        val c = screen.column

        // Selector segmentado
        c.addView(LinearLayout(ctx).apply {
            setPadding(dp(ctx, 16f), dp(ctx, 4f), dp(ctx, 16f), dp(ctx, 8f))
            addView(IosSegmented(ctx, p, listOf("Ganancia", "Gasto", "Kilometraje"), initial = kind).apply {
                onSelected = { kind = it; editing = null; refresh() }
            }, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        })

        c.addView(Sections.header(ctx, p, if (editing != null) "Editar registro" else when (kind) {
            0 -> "Nueva ganancia"; 1 -> "Nuevo gasto"; else -> "Nuevo kilometraje"
        }))
        val card = Sections.card(ctx, p)
        buildForm(card)
        c.addView(card)

        // Seguimiento automático de turnos — al estilo Gridwise: inicia un turno, conduce en
        // cualquier aplicación, el GPS acumula km y guarda un registro de kilometraje al detenerse.
        c.addView(Sections.header(ctx, p, "Seguimiento de turnos"))
        val shiftCard = Sections.card(ctx, p)
        val running = MileageTracker.running
        val km = MileageTracker.kmSoFar
        shiftCard.addView(Sections.row(ctx, p,
            title = if (running) "Turno en curso" else "Kilometraje automático",
            subtitle = when {
                running -> "${distText(km)} rastreados · sigue conduciendo"
                MileageTracker.hasLocationPermission(ctx) ->
                    "El GPS rastrea tu distancia mientras trabajas"
                else -> "Se necesita permiso de ubicación para rastrear la distancia"
            },
        ))
        shiftCard.addView(Sections.separator(ctx, p))
        shiftCard.addView(LinearLayout(ctx).apply {
            setPadding(dp(ctx, 16f), dp(ctx, 8f), dp(ctx, 16f), dp(ctx, 16f))
            val btn = iosButton(ctx, p,
                if (running) "Detener y guardar turno" else "Iniciar turno",
                tint = if (running) p.red else null)
            addView(btn, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            btn.setOnClickListener {
                if (running) {
                    ctx.stopService(MileageTracker.stopIntent(ctx))
                } else if (!MileageTracker.hasLocationPermission(ctx)) {
                    (ctx as Activity).requestPermissions(
                        arrayOf(
                            android.Manifest.permission.ACCESS_FINE_LOCATION,
                            android.Manifest.permission.ACCESS_COARSE_LOCATION,
                        ),
                        MainActivity.REQ_LOCATION,
                    )
                } else {
                    ctx.startForegroundService(MileageTracker.startIntent(ctx))
                }
                // El servicio cambia `running` de forma asíncrona — no repintes
                // ahora o el botón se dibujará desactualizado; actualiza una vez que haya comenzado.
                shiftCard.postDelayed({ refresh() }, 700)
            }
        })
        c.addView(shiftCard)
        c.addView(TextView(ctx).apply {
            text = "La distancia rastreada es una estimación — edita el registro creado si es necesario."
            textSize = Ios.T_FOOTNOTE; setTextColor(p.label2)
            setPadding(dp(ctx, 20f), dp(ctx, 6f), 0, 0)
        })

        c.addView(Sections.header(ctx, p, "Recientes"))
        val recentCard = Sections.card(ctx, p)
        val rows = db.all(8)
        if (rows.isEmpty()) {
            recentCard.addView(TextView(ctx).apply {
                text = "Los registros que añadas aparecerán aquí. Toca uno para editarlo o eliminarlo."
                textSize = Ios.T_SUBHEAD; setTextColor(p.label2)
                setPadding(dp(ctx, 16f), dp(ctx, 14f), dp(ctx, 16f), dp(ctx, 14f))
            })
        } else {
            rows.forEachIndexed { i, r ->
                if (i > 0) recentCard.addView(Sections.separator(ctx, p))
                recentCard.addView(recentRow(r))
            }
        }
        c.addView(recentCard)
        screen.animateIn()
    }

    private fun buildForm(card: LinearLayout) {
        val edit = editing
        val pl = edit?.payload
        when (kind) {
            0 -> { // Ganancia
                val (rAmount, amount) = Sections.formField(ctx, p, "Monto",
                    edit?.let { "%.2f".format(pl!!.optInt("amountCents") / 100.0) } ?: "", hint = "0.00")
                val (rTip, tip) = Sections.formField(ctx, p, "Propina",
                    edit?.let { "%.2f".format(pl!!.optInt("tipCents") / 100.0) } ?: "", hint = "0.00")
                val (rHours, hours) = Sections.formField(ctx, p, "Horas",
                    edit?.let { pl!!.optDouble("hours").takeIf { h -> h > 0 }?.toString() ?: "" } ?: "", hint = "opcional")
                card.addView(rAmount); card.addView(Sections.separator(ctx, p))
                card.addView(rTip); card.addView(Sections.separator(ctx, p))
                card.addView(rHours)
                addSaveRow(card, if (edit != null) "Guardar ganancia" else "Registrar ganancia", edit != null) {
                    val amt = amount.text.toString().toDoubleOrNull() ?: return@addSaveRow err("Introduce un monto")
                    save("earning", JSONObject()
                        .put("amountCents", (amt * 100).toInt())
                        .put("tipCents", ((tip.text.toString().toDoubleOrNull() ?: 0.0) * 100).toInt())
                        .put("hours", hours.text.toString().toDoubleOrNull() ?: 0.0))
                }
            }
            1 -> { // Gasto
                val (rAmount, amount) = Sections.formField(ctx, p, "Monto",
                    edit?.let { "%.2f".format(pl!!.optInt("amountCents") / 100.0) } ?: "", hint = "0.00")
                val (rNote, note) = Sections.formField(ctx, p, "Nota",
                    edit?.let { pl!!.optString("description") } ?: "", numeric = false, hint = "gasolina, peaje…")
                card.addView(rAmount); card.addView(Sections.separator(ctx, p))
                card.addView(rNote)
                addSaveRow(card, if (edit != null) "Guardar gasto" else "Registrar gasto", edit != null) {
                    val amt = amount.text.toString().toDoubleOrNull() ?: return@addSaveRow err("Introduce un monto")
                    save("expense", JSONObject()
                        .put("amountCents", (amt * 100).toInt())
                        .put("category", "OTHER")
                        .put("description", note.text.toString().ifBlank { null }))
                }
            }
            else -> { // Kilometraje — respeta la preferencia de unidad de distancia
                val (rDist, dist) = Sections.formField(ctx, p,
                    if (isKm()) "Kilómetros" else "Millas",
                    edit?.let { "%.1f".format(distDisplay(pl!!.optDouble("distanceKm"))) } ?: "", hint = "0.0")
                val (rFrom, from) = Sections.formField(ctx, p, "Desde",
                    edit?.let { pl!!.optString("startLocation") } ?: "", numeric = false, hint = "opcional")
                card.addView(rDist); card.addView(Sections.separator(ctx, p))
                card.addView(rFrom)
                addSaveRow(card, if (edit != null) "Guardar kilometraje" else "Registrar kilometraje", edit != null) {
                    val v = dist.text.toString().toDoubleOrNull() ?: return@addSaveRow err("Introduce una distancia")
                    save("mileage", JSONObject()
                        .put("distanceKm", if (isKm()) v else v * 1.60934)
                        .put("purpose", "WORK")
                        .put("startLocation", from.text.toString().ifBlank { null }))
                }
            }
        }
    }

    private fun addSaveRow(card: LinearLayout, label: String, isEdit: Boolean, onSave: () -> Unit) {
        card.addView(Sections.separator(ctx, p))
        card.addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(ctx, 12f), dp(ctx, 12f), dp(ctx, 12f), dp(ctx, 12f))
            if (isEdit) {
                val cancel = iosButton(ctx, p, "Cancelar", p.fill)
                cancel.setTextColor(p.label)
                addView(cancel, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    .apply { rightMargin = dp(ctx, 8f) })
                cancel.setOnClickListener { editing = null; refresh() }
            }
            val btn = iosButton(ctx, p, label)
            addView(btn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            btn.setOnClickListener { onSave() }
        })
    }

    /** Hoja de acciones para un registro — estilo iOS: detalles + Editar + Eliminar destructivo. */
    private fun showRecordSheet(r: LocalDb.Row) {
        val sheet = IosSheet(ctx, p)
        val pad = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(ctx, 20f), dp(ctx, 8f), dp(ctx, 20f), 0)
        }
        pad.addView(TextView(ctx).apply {
            text = when (r.type) {
                "earning" -> "Ganancia · " + money(r.payload.optInt("amountCents"))
                "expense" -> (r.payload.optString("description").ifBlank { "Gasto" }) +
                    " · " + money(r.payload.optInt("amountCents"))
                else -> "Kilometraje · " + distText(r.payload.optDouble("distanceKm"))
            }
            textSize = Ios.T_HEADLINE
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(p.label)
        })
        pad.addView(TextView(ctx).apply {
            text = SimpleDateFormat("EEEE, d 'de' MMM 'a las' h:mm a", Locale.US).format(Date(r.createdAt))
            textSize = Ios.T_FOOTNOTE
            setTextColor(p.label2)
            setPadding(0, dp(ctx, 2f), 0, dp(ctx, 16f))
        })
        sheet.add(pad)

        val editBtn = iosButton(ctx, p, "Editar")
        val delBtn = iosButton(ctx, p, "Eliminar", p.red)
        val cancel = iosButton(ctx, p, "Cancelar", p.fill)
        cancel.setTextColor(p.label)
        val btnCol = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(ctx, 16f), 0, dp(ctx, 16f), 0)
            listOf(editBtn, delBtn, cancel).forEach { b ->
                addView(b, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { bottomMargin = dp(ctx, 8f) })
            }
        }
        sheet.add(btnCol)

        editBtn.setOnClickListener {
            sheet.dismiss()
            editing = r
            kind = when (r.type) { "earning" -> 0; "expense" -> 1; else -> 2 }
            refresh()
        }
        delBtn.setOnClickListener {
            sheet.dismiss()
            db.delete(r.clientId)
            Toast.makeText(ctx, "Eliminado", Toast.LENGTH_SHORT).show()
            refresh()
        }
        cancel.setOnClickListener { sheet.dismiss() }
        sheet.show()
        sheet.onDismiss { }
    }

    private fun isKm() = settings.distanceUnit == "KM"
    private fun distDisplay(km: Double) = if (isKm()) km else km * 0.621371
    private fun distText(km: Double) =
        "%.1f %s".format(distDisplay(km), if (isKm()) "km" else "mi")

    private fun err(msg: String) {
        Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
    }

    private fun save(type: String, payload: JSONObject) {
        val edit = editing
        if (edit != null) {
            db.update(edit.clientId, type, payload)
            editing = null
            err("Actualizado")
        } else {
            db.insert(type, payload)
            err("Guardado")
        }
        refresh()
    }

    private fun recentRow(r: LocalDb.Row): View {
        val (label, amount, tint) = when (r.type) {
            "earning" -> Triple("Ganancia", "+${money(r.payload.optInt("amountCents"))}", p.green)
            "expense" -> Triple(r.payload.optString("description").ifBlank { "Gasto" },
                "-${money(r.payload.optInt("amountCents"))}", p.red)
            else -> Triple("Kilometraje", distText(r.payload.optDouble("distanceKm")), p.tint)
        }
        return Sections.row(ctx, p,
            title = label,
            subtitle = SimpleDateFormat("MMM d, h:mm a", Locale.US).format(Date(r.createdAt)),
            value = amount,
            iconGlyph = when (r.type) { "earning" -> "bolt"; "expense" -> "tag"; else -> "speed" },
            iconTint = tint,
            chevron = true,
        ) { showRecordSheet(r) }
    }
}