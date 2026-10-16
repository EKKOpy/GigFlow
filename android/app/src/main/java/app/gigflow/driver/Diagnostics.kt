package app.gigflow.driver

import android.content.Context
import org.json.JSONObject

/**
 * Instantáneas de diagnóstico por aplicación — lo último que vio el asistente en cada aplicación
 * de conductor vigilada. Esta es la interfaz de depuración orientada al usuario: cuando una pantalla parece
 * una oferta pero no se analiza correctamente, guardamos los textos sin procesar para que el conductor pueda
 * revisar "por qué no pasó nada" en lugar de adivinar.
 *
 * Se almacena en SharedPreferences como un objeto JSON indexado por el nombre del paquete.
 */
object Diagnostics {

    private const val FILE = "gigflow_diag"
    private const val KEY = "snaps"
    private const val MAX_TEXTS = 60

    // Limitar las escrituras repetitivas de "inactivo" (idle) — solo se guardan los resultados interesantes
    // y las instantáneas desactualizadas, no cada evento de accesibilidad.
    private const val IDLE_WRITE_MS = 30_000L

    data class Snap(
        val at: Long,
        val outcome: String, // "offer" (oferta) | "miss" (fallo) | "idle" (inactivo)
        val texts: List<String>,
    )

    fun record(ctx: Context, pkg: String, outcome: String, texts: List<String>) {
        val prefs = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val all = JSONObject(prefs.getString(KEY, "{}") ?: "{}")
        val prev = all.optJSONObject(pkg)
        val joined = texts.take(MAX_TEXTS).joinToString("\n")
        // Omitir escrituras repetidas: el estado inactivo está limitado en frecuencia, y un resultado sin cambios
        // con textos idénticos no necesita volverse a persistir en cada fotograma. Una instantánea
        // almacenada vacía (la aplicación seguía en su pantalla de presentación) nunca bloquea el contenido
        // real, o los primeros 30 segundos de cada inicio parecerían rotos.
        if (prev != null &&
            System.currentTimeMillis() - prev.optLong("at") < IDLE_WRITE_MS &&
            (prev.optString("texts").isNotEmpty() || joined.isEmpty()) &&
            (outcome == "idle" ||
                (prev.optString("outcome") == outcome && prev.optString("texts") == joined))
        ) return
        val o = JSONObject()
            .put("at", System.currentTimeMillis())
            .put("outcome", outcome)
            .put("texts", joined)
        prefs.edit().putString(KEY, all.put(pkg, o).toString()).apply()
    }

    fun get(ctx: Context, pkg: String): Snap? {
        val all = JSONObject(
            ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
                .getString(KEY, "{}") ?: "{}",
        )
        val o = all.optJSONObject(pkg) ?: return null
        return Snap(
            at = o.optLong("at"),
            outcome = o.optString("outcome", "idle"),
            texts = o.optString("texts").split("\n").filter { it.isNotBlank() },
        )
    }

    fun clear(ctx: Context) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }
}