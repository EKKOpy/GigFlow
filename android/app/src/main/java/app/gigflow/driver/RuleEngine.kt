package app.gigflow.driver

/**
 * Motor de reglas de ofertas — evalúa una oferta analizada frente a los umbrales
 * del conductor y produce un veredicto junto con razones legibles por humanos.
 * Los umbrales son independientes de la moneda (se comparan en centavos de cualquier
 * moneda en la que viniera la oferta).
 */
data class RulePrefs(
    val enabled: Boolean = true,
    val autoAccept: Boolean = false,
    val autoDecline: Boolean = false,
    val minPerMileCents: Int = 150,
    val minPerHourCents: Int = 2000,
    val minPayoutCents: Int = 400,
    val maxDistanceKm: Double = 40.0,
    val overlaySeconds: Int = 0, // 0 = hasta que la oferta desaparezca
)

object RuleEngine {

    fun score(offer: DetectedOffer, prefs: RulePrefs): ScoredOffer {
        val perMile = offer.distanceKm
            ?.takeIf { it > 0 }
            ?.let { (offer.payoutCents / (it / 1.609344)).toInt() } // $/milla
        val perHour = offer.durationMin
            ?.takeIf { it > 0 }
            ?.let { (offer.payoutCents * 60.0 / it).toInt() }

        val reasons = mutableListOf<String>()
        var score = 0
        var checks = 0

        if (perMile != null) {
            checks++
            if (perMile >= prefs.minPerMileCents) score++ else reasons.add("por debajo del objetivo por distancia")
        }
        if (perHour != null) {
            checks++
            if (perHour >= prefs.minPerHourCents) score++ else reasons.add("por debajo del objetivo por hora")
        }
        checks++
        if (offer.payoutCents >= prefs.minPayoutCents) score++ else reasons.add("por debajo del pago mínimo")
        if (offer.distanceKm != null) {
            checks++
            if (offer.distanceKm <= prefs.maxDistanceKm) score++ else reasons.add("demasiado lejos")
        }

        val verdict = when {
            checks == 0 -> Verdict.MEH
            score == checks -> Verdict.GOOD
            score >= checks / 2 -> Verdict.MEH
            else -> Verdict.BAD
        }

        return ScoredOffer(offer, perMile, perHour, verdict, reasons)
    }
}