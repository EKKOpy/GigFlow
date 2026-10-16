package app.gigflow.driver

/**
 * Registro de monedas — cada moneda que ofrece el selector, con las reglas
 * de visualización que cada una necesita:
 *
 *  - [suffix]: "1 850 Ft" en lugar de "Ft1850" (la mayoría de las monedas
 *    de Europa Central y Oriental, Nórdicas y Árabes van después del monto).
 *  - [zeroDecimal]: nadie escribe "¥1,234.00" o "1 850.00 Ft" — unidades completas.
 *  - [symbolSpace]: "CHF 12.50", "Rp 50.000" — los símbolos formados por letras llevan un espacio.
 *  - [spoken]: nombre de texto a voz (TTS) para que las alertas de voz digan "forints" (forintos), no "F T".
 *
 * El analizador aún coincide con el conjunto de símbolos integrados en pantalla; cualquier cosa
 * que no esté integrada se coincide dinámicamente con la moneda configurada por el usuario
 * (ver OfferParser.extraMoney).
 */
object Currencies {

    data class Entry(
        val code: String,        // ISO 4217
        val symbol: String,      // símbolo de visualización
        val name: String,
        val spoken: String,
        val suffix: Boolean = false,
        val zeroDecimal: Boolean = false,
        val symbolSpace: Boolean = false,
    )

    val ALL: List<Entry> = listOf(
        // Américas
        Entry("USD", "$", "Dólar estadounidense", "dólares"),
        Entry("CAD", "CA$", "Dólar canadiense", "dólares"),
        Entry("MXN", "MX$", "Peso mexicano", "pesos"),
        Entry("BRL", "R$", "Real brasileño", "reales"),
        Entry("ARS", "AR$", "Peso argentino", "pesos", zeroDecimal = true),
        Entry("CLP", "CL$", "Peso chileno", "pesos", zeroDecimal = true),
        Entry("COP", "CO$", "Peso colombiano", "pesos", zeroDecimal = true),
        Entry("PEN", "S/", "Sol peruano", "soles"),
        Entry("UYU", "UY$", "Peso uruguayo", "pesos"),
        Entry("PYG", "₲", "Guaraní paraguayo", "guaraníes", suffix = true, zeroDecimal = true),
        Entry("BOB", "Bs", "Boliviano boliviano", "bolivianos", symbolSpace = true),
        Entry("XCD", "EC$", "Dólar del Caribe Oriental", "dólares"),
        Entry("JMD", "J$", "Dólar jamaicano", "dólares"),
        Entry("TTD", "TT$", "Dólar de Trinidad y Tobago", "dólares"),
        Entry("GTQ", "Q", "Quetzal guatemalteco", "quetzales"),
        Entry("DOP", "RD$", "Peso dominicano", "pesos"),
        Entry("CRC", "₡", "Colón costarricense", "colones", zeroDecimal = true),
        // Europa
        Entry("EUR", "€", "Euro", "euros"),
        Entry("GBP", "£", "Libra esterlina", "libras"),
        Entry("CHF", "CHF", "Franco suizo", "francos", symbolSpace = true),
        Entry("HUF", "Ft", "Forinto húngaro", "forintos", suffix = true, zeroDecimal = true),
        Entry("PLN", "zł", "Złoty polaco", "zlotys", suffix = true),
        Entry("CZK", "Kč", "Corona checa", "coronas", suffix = true),
        Entry("RON", "lei", "Leu rumano", "leis", suffix = true),
        Entry("BGN", "лв", "Lev búlgaro", "levs", suffix = true),
        Entry("RSD", "дин", "Dinar serbio", "dinares", suffix = true),
        Entry("HRK", "kn", "Kuna croata", "kunas", suffix = true),
        Entry("SEK", "kr", "Corona sueca", "coronas", suffix = true),
        Entry("NOK", "kr", "Corona noruega", "coronas", suffix = true),
        Entry("DKK", "kr", "Corona danesa", "coronas", suffix = true),
        Entry("ISK", "kr", "Corona islandesa", "coronas", suffix = true, zeroDecimal = true),
        Entry("UAH", "₴", "Grivna ucraniana", "grivnas", suffix = true),
        Entry("RUB", "₽", "Rublo ruso", "rublos", suffix = true),
        Entry("TRY", "₺", "Lira turca", "liras", suffix = true),
        Entry("MDL", "L", "Leu moldavo", "leis", suffix = true),
        Entry("MKD", "ден", "Denar macedonio", "denares", suffix = true),
        Entry("ALL", "L", "Lek albanés", "leks", suffix = true),
        Entry("BAM", "KM", "Marco convertible de Bosnia-Herzegovina", "marcos convertibles", suffix = true),
        Entry("GEL", "₾", "Lari georgiano", "laris", suffix = true),
        Entry("AMD", "֏", "Dram armenio", "drams", suffix = true),
        Entry("AZN", "₼", "Manat azerbaiyano", "manats", suffix = true),
        Entry("KZT", "₸", "Tenge kazajo", "tenges", suffix = true),
        Entry("UZS", "soʻm", "Som uzbeko", "soms", suffix = true, zeroDecimal = true),
        Entry("KGS", "сом", "Som kirguís", "soms", suffix = true),
        // Medio Oriente y África
        Entry("ILS", "₪", "Nuevo shequel israelí", "shequels"),
        Entry("AED", "د.إ", "Dirham de los Emiratos Árabes Unidos", "dirhams", suffix = true),
        Entry("SAR", "﷼", "Riyal saudí", "riyales", suffix = true),
        Entry("QAR", "QR", "Riyal catarí", "riyales", suffix = true),
        Entry("KWD", "KD", "Dinar kuwaití", "dinares", suffix = true),
        Entry("BHD", "BD", "Dinar bareiní", "dinares", suffix = true),
        Entry("JOD", "JD", "Dinar jordano", "dinares", suffix = true),
        Entry("LBP", "L£", "Libra libanesa", "libras", suffix = true),
        Entry("EGP", "E£", "Libra egipcia", "libras"),
        Entry("MAD", "DH", "Dirham marroquí", "dirhams", symbolSpace = true),
        Entry("DZD", "DA", "Dinar argelino", "dinares", suffix = true),
        Entry("TND", "DT", "Dinar tunecino", "dinares", suffix = true),
        Entry("ZAR", "R", "Rand sudafricano", "rands"),
        Entry("NGN", "₦", "Naira nigeriana", "nairas"),
        Entry("KES", "KSh", "Chelín keniano", "chelines", symbolSpace = true),
        Entry("GHS", "₵", "Cedi ghanés", "cedis"),
        Entry("UGX", "USh", "Chelín ugandés", "chelines", symbolSpace = true, zeroDecimal = true),
        Entry("TZS", "TSh", "Chelín tanzano", "chelines", symbolSpace = true, zeroDecimal = true),
        Entry("XOF", "CFA", "Franco CFA de África Occidental", "francos CFA", suffix = true, zeroDecimal = true),
        Entry("XAF", "FCFA", "Franco CFA de África Central", "francos CFA", suffix = true, zeroDecimal = true),
        // Asia-Pacífico
        Entry("INR", "₹", "Rupia india", "rupias"),
        Entry("PKR", "₨", "Rupia pakistaní", "rupias"),
        Entry("BDT", "৳", "Taka bangladesí", "takas"),
        Entry("LKR", "Rs", "Rupia de Sri Lanka", "rupias", symbolSpace = true),
        Entry("NPR", "रू", "Rupia nepalí", "rupias"),
        Entry("JPY", "¥", "Yen japonés", "yenes", zeroDecimal = true),
        Entry("CNY", "¥", "Yuan chino", "yuanes"),
        Entry("KRW", "₩", "Won surcoreano", "wons", zeroDecimal = true),
        Entry("HKD", "HK$", "Dólar de Hong Kong", "dólares"),
        Entry("TWD", "NT$", "Nuevo dólar taiwanés", "dólares"),
        Entry("SGD", "S$", "Dólar de Singapur", "dólares"),
        Entry("MYR", "RM", "Ringgit malayo", "ringgits", symbolSpace = true),
        Entry("THB", "฿", "Baht tailandés", "bahts"),
        Entry("VND", "₫", "Dong vietnamita", "dongs", suffix = true, zeroDecimal = true),
        Entry("PHP", "₱", "Peso filipino", "pesos"),
        Entry("IDR", "Rp", "Rupia indonesia", "rupias", symbolSpace = true, zeroDecimal = true),
        Entry("AUD", "A$", "Dólar australiano", "dólares"),
        Entry("NZD", "NZ$", "Dólar neozelandés", "dólares"),
        Entry("MNT", "₮", "Tögrög mongol", "tögrögs", suffix = true),
    )

    private val byCode = ALL.associateBy { it.code }
    private val bySymbol = mutableMapOf<String, Entry>().apply {
        ALL.forEach { putIfAbsent(it.symbol, it) }
    }

    fun forCode(code: String): Entry? = byCode[code]
    fun forSymbol(symbol: String): Entry? = bySymbol[symbol]

    /** Etiqueta de selector estilo "United States Dollar (USD)". */
    fun label(c: Entry) = "${c.symbol}  ·  ${c.name}"

    /**
     * Renderiza centavos con las convenciones del símbolo. Los símbolos desconocidos o personalizados vuelven
     * al formato con prefijo y espacio si tienen letras ("XYZ 12.50", o con el símbolo € si aplica).
     */
    fun format(cents: Int, symbol: String): String {
        val c = forSymbol(symbol)
        val v = cents / 100.0
        if (c == null) {
            return if (symbol.firstOrNull()?.isLetter() == true)
                "%s %,.2f".format(symbol, v)
            else "%s%,.2f".format(symbol, v)
        }
        return when {
            c.suffix && c.zeroDecimal -> "%,.0f %s".format(v, symbol)
            c.suffix -> "%,.2f %s".format(v, symbol)
            c.zeroDecimal -> "%s%,.0f".format(symbol, v)
            c.symbolSpace -> "%s %,.2f".format(symbol, v)
            else -> "%s%,.2f".format(symbol, v)
        }
    }

    /** Nombre hablado para TTS — "forints", "zloty", o el símbolo si es desconocido. */
    fun spoken(symbol: String): String = forSymbol(symbol)?.spoken ?: symbol

    /** Monto listo para voz: "12.50 dollars" / "1850 forints". */
    fun spokenAmount(cents: Int, symbol: String): String {
        val c = forSymbol(symbol)
        return if (c?.zeroDecimal == true)
            "%.0f %s".format(cents / 100.0, c.spoken)
        else "%.2f %s".format(cents / 100.0, c?.spoken ?: symbol)
    }
}