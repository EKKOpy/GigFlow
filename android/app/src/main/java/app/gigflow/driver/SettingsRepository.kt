package app.gigflow.driver

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("gigflow_prefs", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean("enabled", true)
        set(v) = prefs.edit().putBoolean("enabled", v).apply()

    var autoAccept: Boolean
        get() = prefs.getBoolean("auto_accept", false)
        set(v) = prefs.edit().putBoolean("auto_accept", v).apply()

    var autoDecline: Boolean
        get() = prefs.getBoolean("auto_decline", false)
        set(v) = prefs.edit().putBoolean("auto_decline", v).apply()

    var minPerMileCents: Int
        get() = prefs.getInt("min_per_mile", 150)
        set(v) = prefs.edit().putInt("min_per_mile", v).apply()

    var minPerHourCents: Int
        get() = prefs.getInt("min_per_hour", 2000)
        set(v) = prefs.edit().putInt("min_per_hour", v).apply()

    var minPayoutCents: Int
        get() = prefs.getInt("min_payout", 400)
        set(v) = prefs.edit().putInt("min_payout", v).apply()

    var maxDistanceKm: Double
        get() = prefs.getFloat("max_distance_km", 40f).toDouble()
        set(v) = prefs.edit().putFloat("max_distance_km", v.toFloat()).apply()

    /**
     * Moneda como código ISO-4217 ("USD", "HUF") — los símbolos por sí solos chocan
     * ("kr" es SEK/NOK/DKK/ISK, "$" abarca una docena de monedas), por lo que el código es
     * la fuente de verdad y el símbolo se deriva de [Currencies].
     * "CUSTOM" = un símbolo escrito a mano que el registro no conoce.
     */
    var currencyCode: String
        get() = prefs.getString("currency_code", null) ?: run {
            // Migrar la antigua preferencia de solo símbolo: los símbolos conocidos se mapean a códigos,
            // cualquier cosa no reconocida se convierte en un símbolo personalizado.
            val sym = prefs.getString("currency_symbol", "$") ?: "$"
            (Currencies.forSymbol(sym)?.code ?: "CUSTOM").also {
                prefs.edit().putString("currency_code", it).apply()
            }
        }
        set(v) = prefs.edit().putString("currency_code", v).apply()

    /** Símbolo de visualización — derivado del código o de la cadena personalizada. */
    var currencySymbol: String
        get() = Currencies.forCode(currencyCode)?.symbol
            ?: prefs.getString("currency_symbol", "$") ?: "$"
        set(v) = prefs.edit()
            .putString("currency_symbol", v.trim().ifEmpty { "$" })
            .putString("currency_code",
                Currencies.forSymbol(v.trim())?.code ?: "CUSTOM")
            .apply()

    /** Unidad de visualización: "MI" o "KM". */
    var distanceUnit: String
        get() = prefs.getString("distance_unit", "MI") ?: "MI"
        set(v) = prefs.edit().putString("distance_unit", v).apply()

    /** Segundos que la superposición de veredicto permanece en pantalla; 0 = hasta que la tarjeta desaparezca. */
    var overlaySeconds: Int
        get() = prefs.getInt("overlay_seconds", 0)
        set(v) = prefs.edit().putInt("overlay_seconds", v).apply()

    /** Respuesta háptica en toques/alternancias. */
    var haptics: Boolean
        get() = prefs.getBoolean("haptics", true)
        set(v) = prefs.edit().putBoolean("haptics", v).apply()

    /** Decir los veredictos de las ofertas en voz alta — alertas de voz al estilo Mystro. */
    var voiceAlerts: Boolean
        get() = prefs.getBoolean("voice_alerts", false)
        set(v) = prefs.edit().putBoolean("voice_alerts", v).apply()

    /** Segundos antes de que se active la auto-aceptación; el usuario puede cancelar desde la superposición. */
    var autoAcceptDelaySec: Int
        get() = prefs.getInt("auto_accept_delay", 5)
        set(v) = prefs.edit().putInt("auto_accept_delay", v).apply()

    /**
     * Iniciar el seguimiento de turnos por GPS automáticamente cuando una aplicación de conductor supervisada pasa
     * a primer plano, y detenerlo después de un tiempo alejado de ellas. Aún necesita
     * permiso de ubicación; sin él, esto no hace nada de forma silenciosa.
     */
    var autoTrackShift: Boolean
        get() = prefs.getBoolean("auto_track_shift", false)
        set(v) = prefs.edit().putBoolean("auto_track_shift", v).apply()

    /** Registrar automáticamente las ganancias desde las pantallas de resumen posteriores al viaje. */
    var autoLogEarnings: Boolean
        get() = prefs.getBoolean("auto_log_earnings", true)
        set(v) = prefs.edit().putBoolean("auto_log_earnings", v).apply()

    /** Si la cuenta regresiva de auto-aceptación también se activa en trabajo programado o reservado. */
    var autoAcceptReservations: Boolean
        get() = prefs.getBoolean("auto_accept_reservations", true)
        set(v) = prefs.edit().putBoolean("auto_accept_reservations", v).apply()

    // ---- Anulaciones de reglas por aplicación (filtros por servicio de Mystro) ----

    private fun overrides(): JSONObject =
        JSONObject(prefs.getString("platform_rules", "{}") ?: "{}")

    /** Objeto de anulación almacenado para un paquete, o nulo para heredar las globales. */
    fun platformOverride(pkg: String): JSONObject? =
        overrides().optJSONObject(pkg)

    fun setPlatformOverride(pkg: String, o: JSONObject) {
        prefs.edit().putString("platform_rules",
            overrides().put(pkg, o).toString()).apply()
    }

    fun clearPlatformOverride(pkg: String) {
        // JSONObject.remove() devuelve el valor eliminado (nulo si está ausente) —
        // modifica el objeto, no conviertas el resultado de nuevo en cadena.
        val o = overrides()
        o.remove(pkg)
        prefs.edit().putString("platform_rules", o.toString()).apply()
    }

    /** Reglas globales combinadas con las anulaciones del paquete, si existen. */
    fun rulesFor(pkg: String): RulePrefs {
        val base = rules()
        val o = platformOverride(pkg) ?: return base
        return base.copy(
            autoAccept = o.optBoolean("autoAccept", base.autoAccept),
            autoDecline = o.optBoolean("autoDecline", base.autoDecline),
            minPerMileCents = if (o.has("minPerMileCents")) o.getInt("minPerMileCents") else base.minPerMileCents,
            minPerHourCents = if (o.has("minPerHourCents")) o.getInt("minPerHourCents") else base.minPerHourCents,
            minPayoutCents = if (o.has("minPayoutCents")) o.getInt("minPayoutCents") else base.minPayoutCents,
            maxDistanceKm = if (o.has("maxDistanceKm")) o.getDouble("maxDistanceKm") else base.maxDistanceKm,
        )
    }

    fun rules() = RulePrefs(
        enabled = enabled,
        autoAccept = autoAccept,
        autoDecline = autoDecline,
        minPerMileCents = minPerMileCents,
        minPerHourCents = minPerHourCents,
        minPayoutCents = minPayoutCents,
        maxDistanceKm = maxDistanceKm,
        overlaySeconds = overlaySeconds,
    )
}