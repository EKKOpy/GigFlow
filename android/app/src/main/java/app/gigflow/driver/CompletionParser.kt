package app.gigflow.driver

/**
 * Extractor de pantalla de viajes completados — la otra mitad del registro manos libres.
 *
 * Una vez que finaliza un envío/viaje, las aplicaciones de conductor muestran un resumen ("Ganaste",
 * "Tarifa del viaje", "Entrega completa") con una cifra de pago. Cuando GigFlow ve
 * esa pantalla, registra una ganancia automáticamente, para que el conductor nunca tenga que escribirla
 * manualmente. Con huella digital para evitar registros duplicados mientras la pantalla permanece abierta.
 *
 * La misma advertencia que en [OfferParser]: heurístico, dependiente del diseño y solo
 * *lee* la pantalla — el registro que crea es editable y eliminable.
 */
object CompletionParser {

    private val TIP_WORD = Regex(
        """tip|gratuit|borraval|trinkgeld|napiw|pourboire|propina|mancia|dricks|drikkepenge|juomarah|spropitn|bahş|чаєв""",
        RegexOption.IGNORE_CASE,
    )

    // Frases que solo aparecen en pantallas posteriores a viajes o resúmenes de ganancias —
    // Inglés primero, luego los idiomas en los que operan Wolt y foodora.
    private val COMPLETED = Regex(
        """you earned|you made|trip (fare|complete|ended|summary)|delivery complete|""" +
            """order (complete|delivered)|completed delivery|earnings? (today|summary)|""" +
            """total (earnings|payout)|payout|""" +
            """kiszállítva|teljesítve|keresett|jövedelem|verdient|abgeschlossen|""" +
            """zarobiłeś|zakończon|ukończon|vous avez gagné|livraison terminée|""" +
            """has ganado|entrega completada|leverans slutförd|du tjänade|""" +
            """levering fullført|du tjente|fuldført|toimitus valmis|ansaitsit|""" +
            """dokončen|vyděl|livrare finalizată|ai câștigat|teslimat tamamlandı|kazandın|""" +
            """завершено|заробили|consegna completata|hai guadagnato""",
        RegexOption.IGNORE_CASE,
    )

    // Palabras que marcan una pantalla *pendiente* (la oferta aún se está decidiendo) — nunca trates
    // esto como trabajo completado. Las raíces localizadas reflejan el conjunto de OfferParser.
    private val PENDING = Regex(
        """accept|decline|incoming|new (order|delivery|ride)|elfogad|elutasít|akzept|akcept|""" +
            """prija|přijm|przyjm|hyväks|avvis|godta|annehm|ablehn|refus|reddet""",
        RegexOption.IGNORE_CASE,
    )

    data class Completion(
        val payoutCents: Int,
        val tipCents: Int?,
        val currencySymbol: String,
        val fingerprint: String,
    )

    fun parse(
        pkg: String,
        nodes: List<OfferParser.FlatNode>,
        extra: Regex? = null,
        extraSymbol: String = "$",
    ): Completion? {
        val texts = nodes.map { it.text }
        if (texts.none { COMPLETED.containsMatchIn(it) }) return null
        if (texts.any { PENDING.containsMatchIn(it) }) return null

        data class M(val amount: Double, val currency: String, val isTip: Boolean)
        val money = nodes.mapNotNull { n ->
            OfferParser.moneyOf(n.text, extra, extraSymbol)?.let { (a, c) ->
                M(a, c, TIP_WORD.containsMatchIn(n.text))
            }
        }
        val payout = money.filter { !it.isTip && it.amount > 0.5 }
            .maxOfOrNull { it.amount } ?: return null
        if (payout > 5000) return null // absurdo — probablemente un artefacto de subcadena
        val currency = money.firstOrNull { it.amount == payout }?.currency ?: "$"

        val tip = money.filter { it.isTip }
            .maxOfOrNull { it.amount }

        return Completion(
            payoutCents = (payout * 100).toInt(),
            tipCents = tip?.let { (it * 100).toInt() },
            currencySymbol = currency,
            // Huella digital basada en intervalos de un minuto: la misma pantalla no puede volverse a registrar dentro
            // de ese intervalo, pero un nuevo completado (diferente monto u hora) sí puede.
            fingerprint = "$pkg:${(payout * 100).toInt()}:${System.currentTimeMillis() / 300_000}",
        )
    }
}