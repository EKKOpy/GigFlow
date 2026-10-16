package app.gigflow.driver

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Extractor heurístico de tarjetas de ofertas.
 *
 * Las aplicaciones de conductor cambian sus diseños constantemente y no publican contratos de
 * árboles de vistas, por lo que esto funciona intencionalmente a partir de *patrones textuales* en lugar
 * de identificadores de vista: encuentra cifras de dinero, texto de distancia y duración, y los
 * controles de aceptación/rechazo. Optimizado por aplicación cuando existe un anclaje confiable.
 */
object OfferParser {

    // Dinero: prefijado con símbolo/código ($12.50, €8.90) o sufijado con código (850 Ft,
    // 12,50 EUR). Los conductores en Europa reciben tarjetas en Ft/kr/zł — un análisis solo para
    // $ significa que nunca se detecta nada. Distingue estrictamente mayúsculas de minúsculas: la "ft"
    // minúscula son pies, no florines. La terminación (?![\p{L}\p{N}]) en lugar de \b mantiene
    // haciendo coincidir los tokens que terminan en ł/č/₺, y se mantiene dentro de lo que el
    // motor de expresiones regulares ICU de Android puede compilar (sin (?U), sin lookbehind).
    val MONEY = Regex(
        "([$€£₺₴]|USD|EUR|GBP)\\s*(\\d(?:[\\d., '\\u00A0]{0,12}\\d)?)|" +
            "(\\d(?:[\\d., '\\u00A0]{0,12}\\d)?)\\s*" +
            "(Ft|HUF|huf|EUR|eur|kr|Kr|KR|SEK|NOK|DKK|PLN|pln|zł|Zł|RON|ron|" +
            "lei|CZK|czk|Kč|TL|TRY|uah|UAH|₺|₴|USD|usd)(?![\\p{L}\\p{N}])",
    )
    private val CURRENCY_WORDS = mapOf(
        "$" to "$", "€" to "€", "£" to "£",
        "ft" to "Ft", "huf" to "Ft", "eur" to "€", "gbp" to "£",
        "kr" to "kr", "sek" to "kr", "nok" to "kr", "dkk" to "kr",
        "pln" to "zł", "zł" to "zł", "ron" to "lei", "lei" to "lei",
        "czk" to "Kč", "kč" to "Kč", "tl" to "₺", "try" to "₺", "₺" to "₺",
        "uah" to "₴", "₴" to "₴", "usd" to "$",
    )

    /**
     * Análisis de montos seguro para la configuración regional: "12,50"→12.5, "1.850"→1850 (3 o más dígitos después
     * del último separador = miles), "2 340"→2340, "1,234.56"→1234.56.
     */
    private fun parseAmount(raw: String): Double? {
        val s = raw.replace(" ", "").replace(" ", "").replace("'", "")
        val lastSep = maxOf(s.lastIndexOf('.'), s.lastIndexOf(','))
        val normalized = if (lastSep >= 0 && s.length - lastSep - 1 in 1..2) {
            s.substring(0, lastSep).filter { it.isDigit() } + "." + s.substring(lastSep + 1)
        } else {
            s.filter { it.isDigit() }
        }
        return normalized.toDoubleOrNull()
    }

    /**
     * Coincidencia de dinero para la moneda *configurada* por el usuario, cubriendo símbolos
     * que el conjunto integrado no conoce (R$, ₩, "KSh", …). Tanto el símbolo como
     * el código ISO coinciden, como prefijo o sufijo. Almacenado en caché — construir una expresión regular
     * en cada análisis de 350 ms sería ineficiente.
     */
    private var extraKey = ""
    private var extraRe: Regex? = null

    /** Códigos ISO que también son palabras cotidianas — "TOP 100 drivers" no debe
     *  analizarse como 100 pa'angas. El símbolo sigue coincidiendo para estas monedas. */
    private val WORDY_CODES = setOf(
        "ALL", "AND", "ARE", "BAM", "BOB", "BUT", "CAN", "FOR", "GEL", "GET",
        "HAS", "HIT", "HOT", "MAD", "MAN", "NEW", "NOW", "OFF", "ONE", "PAY",
        "PEN", "PER", "RON", "SAR", "SEE", "SOS", "TOP", "TRY", "TWO", "USE",
        "VES", "WON", "YER", "CUSTOM",
    )

    fun extraMoney(code: String, symbol: String): Regex? {
        val toks = listOf(symbol, code)
            .map { it.trim() }
            .filter {
                it.isNotEmpty() &&
                    it.lowercase() !in CURRENCY_WORDS &&
                    it.uppercase() !in WORDY_CODES
            }
            .distinct()
        if (toks.isEmpty()) return null
        val key = toks.joinToString("|")
        if (key == extraKey) return extraRe
        val alt = toks.joinToString("|") { Regex.escape(it) }
        val num = "(\\d(?:[\\d., '\\u00A0]{0,12}\\d)?)"
        // Anticipación (lookahead), no \b — \b no puede anclarse después de símbolos que no sean palabras (₺, ₩, R$)
        // por lo que "100 ₺" fallaría silenciosamente al final de la cadena.
        extraRe = Regex("(?:$alt)\\s*$num|$num\\s*(?:$alt)(?![\\p{L}\\p{N}])")
        extraKey = key
        return extraRe
    }

    /** Extrae el monto y el símbolo de visualización de un texto, o nulo. [extra] es la
     *  coincidencia de moneda configurada de [extraMoney]; sus coincidencias informan
     *  [extraSymbol] como la moneda. */
    fun moneyOf(text: String, extra: Regex? = null, extraSymbol: String = "$"): Pair<Double, String>? {
        MONEY.find(text)?.let { m ->
            val amount = parseAmount(m.groupValues[2].ifEmpty { m.groupValues[3] })
                ?: return@let
            val token = m.groupValues[1].ifEmpty { m.groupValues[4] }
            return amount to (CURRENCY_WORDS[token.lowercase()] ?: "$")
        }
        val m = extra?.find(text) ?: return null
        val amount = parseAmount(m.groupValues[1].ifEmpty { m.groupValues[2] })
            ?: return null
        return amount to extraSymbol
    }
    private val MILES = Regex("""(\d{1,3}(?:\.\d+)?)\s*(mi|miles?|mile)\b""", RegexOption.IGNORE_CASE)
    private val KM = Regex("""(\d{1,3}(?:\.\d+)?)\s*km\b""", RegexOption.IGNORE_CASE)
    // "min" cubre la mayoría de los idiomas europeos; las ofertas en húngaro muestran "perc" en su lugar.
    private val MINUTES = Regex(
        """(\d{1,3})\s*(minutes|minuten|minutter|minuter|minute|mins?|perc|percet|minut)\b""",
        RegexOption.IGNORE_CASE,
    )
    // "1 hr 20 min", "2 h", "1 óra 30 perc", "2 godz.", "3 timer"…
    private val HOURS_MIN = Regex(
        """(\d{1,2})\s*(h|hr|hrs|hour|hours|óra|godz|hod|tuntia|tim|timer)\b\.?\s*(\d{1,2})?\s*(min|perc|minut)?""",
        RegexOption.IGNORE_CASE,
    )
    // "tip" abarca la mayoría de los mercados; añade los que difieren (HU/DE/PL/FR/ES/IT/…).
    private val TIP_WORD = Regex(
        """tip|gratuit|borraval|trinkgeld|napiw|pourboire|propina|mancia|dricks|drikkepenge|juomarah|spropitn|bahş|чаєв""",
        RegexOption.IGNORE_CASE,
    )

    // Palabras de aceptación en los mercados en los que Wolt y foodora realmente se localizan.
    // Las raíces de palabras mantienen la tabla compacta ("Elfogadás/Elfogadom" en húngaro,
    // "Akceptuj/Przyjmij" en polaco...). "swipe" detecta la etiqueta del control deslizante de Flex.
    private val ACCEPT_WORDS = Regex(
        """accept|confirm|match me|claim|grab it|start delivery|swipe|""" +
            """accepter|aceptar|aceitar|acceptă|akzept|aksep|annehm|akcept|przyjm|přijm|prija|""" +
            """elfogad|átvett|átvesz|hyväks|godkänn|godkjenn|godta|kabul|zaprim|prejmi|""" +
            """прийма|прийнят|приеми|acceptez|pastāst|patvirt""",
        RegexOption.IGNORE_CASE,
    )
    // "Schedule"/"Book" = reclamar un bloque *reservado* (Amazon Flex) — solo
    // se aplica en pantallas que ya hemos marcado como reservas, para que la pestaña de navegación
    // Horario no se confunda con una opción de aceptación.
    private val RESERVE_ACCEPT = Regex("""schedule|book|reserve|foglal|boka""", RegexOption.IGNORE_CASE)
    private val DECLINE_WORDS = Regex(
        """decline|\bpass\b|reject|no thanks|skip|close|refus|rechaz|recus|ablehn|avvis|afvis|""" +
            """odmítn|odmiet|odrzu|hylk|elutasít|visszautasít|reddet|atmest|noraid|""" +
            """відхил|отказ|norite|refuza""",
        RegexOption.IGNORE_CASE,
    )
    private val TIMER_HINTS = Regex("""^\d{1,2}$|seconds|másodperc|sekund""", RegexOption.IGNORE_CASE)
    // Cuenta regresiva de expiración de la oferta: estilo "0:12", "12s", "12 sec", "expires in 12".
    private val EXPIRY = Regex(
        """^(\d{1,2}):(\d{2})$|^(\d{1,2})\s*(s|sec|seconds?|mp)\b|expires? in (\d{1,2})""",
        RegexOption.IGNORE_CASE,
    )
    private val RESERVATION = Regex(
        """reserv|scheduled|foglal|ütemez|rezerv|planowan|zaplanowan|bokad|planlagt|varattu|забронь|зарезерв""",
        RegexOption.IGNORE_CASE,
    )

    /** Aplicaciones en las que toda la tarjeta de solicitud acepta al tocarla — sin botón etiquetado.
     *  Documentación de Uber: "toca en cualquier parte de la barra negra"; Lyft: "toca en cualquier parte". */
    private val TAP_ANYWHERE_PKGS = setOf("com.uber.driver", "com.lyft.driver")

    data class FlatNode(
        val text: String,
        val viewId: String?,
        val bounds: Rect,
        val clickable: Boolean,
    )

    fun flatten(root: AccessibilityNodeInfo): List<FlatNode> {
        val out = ArrayList<FlatNode>(64)
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.add(root)
        while (stack.isNotEmpty()) {
            val n = stack.removeFirst()
            val t = n.text?.toString()?.trim().orEmpty()
            val cd = n.contentDescription?.toString()?.trim().orEmpty()
            val label = if (t.isNotEmpty()) t else cd
            if (label.isNotEmpty()) {
                val r = Rect()
                n.getBoundsInScreen(r)
                out.add(FlatNode(label, n.viewIdResourceName, r, n.isClickable))
            }
            for (i in 0 until n.childCount) {
                n.getChild(i)?.let(stack::add)
            }
        }
        return out
    }

    fun parse(
        pkg: String,
        nodes: List<FlatNode>,
        extra: Regex? = null,
        extraSymbol: String = "$",
    ): DetectedOffer? {
        val texts = nodes.map { it.text }

        // Recopila cifras de dinero con su contexto. El total de la tarjeta suele ser
        // el monto independiente más grande; las líneas de "incl. propina" se convierten en propinas.
        data class Money(val node: FlatNode, val amount: Double, val currency: String, val raw: String)
        val moneyNodes = nodes.mapNotNull { n ->
            moneyOf(n.text, extra, extraSymbol)?.let { (amount, cur) -> Money(n, amount, cur, n.text) }
        }
        if (moneyNodes.isEmpty()) return null

        val payout = moneyNodes
            .filter { it.amount > 0.5 && !TIP_WORD.containsMatchIn(it.raw) }
            .maxOfOrNull { it.amount } ?: return null
        val payoutMoney = moneyNodes.firstOrNull { it.amount == payout }
        val currency = payoutMoney?.currency ?: "$"

        val tip = moneyNodes
            .filter { TIP_WORD.containsMatchIn(it.raw) }
            .maxOfOrNull { it.amount }

        // Distancias: toma la más plausible más grande (distancia total del viaje).
        val distanceKm = texts.mapNotNull { t ->
            MILES.find(t)?.groupValues?.get(1)?.toDoubleOrNull()?.times(1.609344)
                ?: KM.find(t)?.groupValues?.get(1)?.toDoubleOrNull()
        }.filter { it in 0.2..500.0 }.maxOrNull()

        // Duración: ignora los temporizadores de cuenta regresiva (enteros pequeños, "segundos").
        val durationMin = texts.mapNotNull { t ->
            if (TIMER_HINTS.containsMatchIn(t)) return@mapNotNull null
            HOURS_MIN.find(t)?.let { m ->
                val h = m.groupValues[1].toDouble()
                val mnt = m.groupValues.getOrNull(2)?.toDoubleOrNull() ?: 0.0
                h * 60 + mnt
            } ?: MINUTES.find(t)?.groupValues?.get(1)?.toDoubleOrNull()
        }.filter { it in 1.0..600.0 }.maxOrNull()

        // Cuenta regresiva de expiración — estilo "0:15" / "12s", Mystro la muestra avanzando.
        val expiresInSec = texts.mapNotNull { t ->
            EXPIRY.find(t.trim())?.let { m ->
                val mm = m.groupValues[1].toIntOrNull()
                if (mm != null) mm * 60 + (m.groupValues[2].toIntOrNull() ?: 0)
                else (m.groupValues[3].ifEmpty { m.groupValues[5] }).toIntOrNull()
            }
        }.filter { it in 1..300 }.minOrNull() // Los lotes de Instacart duran ~4 min

        // Se necesita al menos el pago + uno de distancia o duración para considerarlo una oferta.
        if (distanceKm == null && durationMin == null) return null

        fun nodeMatching(re: Regex, clickableOnly: Boolean): NodeRef? =
            nodes.firstOrNull { n ->
                re.containsMatchIn(n.text) && (!clickableOnly || n.clickable) && n.bounds.width() > 0
            }?.let { NodeRef(it.bounds, it.text) }

        // Uber/Lyft aceptan la solicitud cuando se toca *en cualquier parte* de la tarjeta —
        // no hay un control de aceptación etiquetado, por lo que la opción es la tarjeta
        // misma: el nodo interactivo más pequeño que contiene el texto del pago.
        fun tapAnywhereAccept(payoutNode: FlatNode?): NodeRef? {
            if (pkg !in TAP_ANYWHERE_PKGS) return null
            val m = payoutNode ?: return null
            val cx = m.bounds.centerX(); val cy = m.bounds.centerY()
            return nodes
                .filter { it.clickable && it.bounds.contains(cx, cy) }
                .minByOrNull { it.bounds.width() * it.bounds.height() }
                ?.let { NodeRef(it.bounds, it.text) }
        }

        val isReservation = texts.any { RESERVATION.containsMatchIn(it) }

        val accept = nodeMatching(ACCEPT_WORDS, clickableOnly = true)
            ?: nodeMatching(ACCEPT_WORDS, clickableOnly = false)
            ?: if (isReservation && pkg == "com.amazon.rabbit") {
                nodeMatching(RESERVE_ACCEPT, clickableOnly = true)
                    ?: nodeMatching(RESERVE_ACCEPT, clickableOnly = false)
            } else null
            ?: tapAnywhereAccept(payoutMoney?.node)
        val decline = nodeMatching(DECLINE_WORDS, clickableOnly = true)
            ?: nodeMatching(DECLINE_WORDS, clickableOnly = false)

        return DetectedOffer(
            appPackage = pkg,
            payoutCents = (payout * 100).toInt(),
            tipCents = tip?.let { (it * 100).toInt() },
            distanceKm = distanceKm?.let { Math.round(it * 10) / 10.0 },
            durationMin = durationMin,
            extraDistanceKm = null,
            acceptNode = accept,
            declineNode = decline,
            rawTexts = texts,
            currencySymbol = currency,
            expiresInSec = expiresInSec,
            isReservation = isReservation,
        )
    }
}