package app.gigflow.driver

import android.graphics.Rect

/** Un nodo del árbol de vistas de la aplicación de destino que podríamos querer tocar. */
data class NodeRef(
    val bounds: Rect,
    val label: String,
)

/** Una oferta extraída de la pantalla de una aplicación de conductor. */
data class DetectedOffer(
    val appPackage: String,
    val payoutCents: Int,
    val tipCents: Int?,
    val distanceKm: Double?,
    val durationMin: Double?,
    val extraDistanceKm: Double?, // ej. "distancia de entrega" frente a "distancia del viaje"
    val seenAt: Long = System.currentTimeMillis(),
    val acceptNode: NodeRef?,
    val declineNode: NodeRef?,
    val rawTexts: List<String>,
    /** Verdadero para trabajo programado o de reservación (ej. "Reservado", "Programado")
     * en lugar de un aviso en directo bajo demanda. */
    val isReservation: Boolean = false,
    /** Símbolo de visualización detectado en la tarjeta — "$", "Ft", "€", … */
    val currencySymbol: String = "$",
    /** Segundos que la oferta le da al conductor para decidir, si se muestra en la tarjeta. */
    val expiresInSec: Int? = null,
) {
    /** Clave semiestable para la deduplicación dentro de una ventana breve. */
    val fingerprint: String
        get() = "$appPackage|$payoutCents|${distanceKm}|${durationMin}|${rawTexts.take(6).joinToString("|")}"
}

enum class Verdict { GOOD, MEH, BAD }

data class ScoredOffer(
    val offer: DetectedOffer,
    val perMileCents: Int?,
    val perHourCents: Int?,
    val verdict: Verdict,
    val reasons: List<String>,
)