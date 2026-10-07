package io.github.submark.core.data.currency

import io.github.submark.core.model.Currency
import java.util.Locale

/** Built-in fiat currencies (ISO 4217). Names are English; the UI may localize by code. */
object CurrencyCatalog {
    data class Entry(val code: String, val name: String, val symbol: String)

    val entries: List<Entry> = listOf(
        Entry("USD", "US Dollar", "$"),
        Entry("EUR", "Euro", "€"),
        Entry("GBP", "British Pound", "£"),
        Entry("JPY", "Japanese Yen", "¥"),
        Entry("CNY", "Chinese Yuan", "¥"),
        Entry("HKD", "Hong Kong Dollar", "HK$"),
        Entry("TWD", "New Taiwan Dollar", "NT$"),
        Entry("KRW", "South Korean Won", "₩"),
        Entry("SGD", "Singapore Dollar", "S$"),
        Entry("AUD", "Australian Dollar", "A$"),
        Entry("CAD", "Canadian Dollar", "C$"),
        Entry("NZD", "New Zealand Dollar", "NZ$"),
        Entry("CHF", "Swiss Franc", "CHF"),
        Entry("INR", "Indian Rupee", "₹"),
        Entry("IDR", "Indonesian Rupiah", "Rp"),
        Entry("MYR", "Malaysian Ringgit", "RM"),
        Entry("THB", "Thai Baht", "฿"),
        Entry("PHP", "Philippine Peso", "₱"),
        Entry("VND", "Vietnamese Dong", "₫"),
        Entry("TRY", "Turkish Lira", "₺"),
        Entry("RUB", "Russian Ruble", "₽"),
        Entry("UAH", "Ukrainian Hryvnia", "₴"),
        Entry("KZT", "Kazakhstani Tenge", "₸"),
        Entry("BRL", "Brazilian Real", "R$"),
        Entry("MXN", "Mexican Peso", "MX$"),
        Entry("ARS", "Argentine Peso", "AR$"),
        Entry("CLP", "Chilean Peso", "CLP$"),
        Entry("COP", "Colombian Peso", "COL$"),
        Entry("ZAR", "South African Rand", "R"),
        Entry("NGN", "Nigerian Naira", "₦"),
        Entry("EGP", "Egyptian Pound", "E£"),
        Entry("AED", "UAE Dirham", "AED"),
        Entry("SAR", "Saudi Riyal", "SAR"),
        Entry("ILS", "Israeli New Shekel", "₪"),
        Entry("PKR", "Pakistani Rupee", "Rs"),
        Entry("BDT", "Bangladeshi Taka", "৳"),
        Entry("SEK", "Swedish Krona", "kr"),
        Entry("NOK", "Norwegian Krone", "kr"),
        Entry("DKK", "Danish Krone", "kr"),
        Entry("PLN", "Polish Zloty", "zł"),
        Entry("CZK", "Czech Koruna", "Kč"),
        Entry("HUF", "Hungarian Forint", "Ft"),
        Entry("RON", "Romanian Leu", "lei"),
        Entry("ISK", "Icelandic Krona", "kr"),
    )

    /** Enabled on first launch besides the default currency. */
    val initiallyEnabled = listOf("USD", "EUR", "CNY", "GBP", "JPY")

    private val byCode = entries.associateBy { it.code }

    fun find(code: String): Entry? = byCode[code]

    /** Catalogue entry, or a JDK-described entry for valid ISO codes outside the catalogue. */
    fun describe(code: String): Entry? = byCode[code] ?: runCatching {
        val jdk = java.util.Currency.getInstance(code)
        Entry(code, jdk.getDisplayName(Locale.ENGLISH), jdk.getSymbol(Locale.ENGLISH))
    }.getOrNull()

    /** Currency of the device locale's country, falling back to USD. */
    fun localeCurrencyCode(locale: Locale = Locale.getDefault()): String =
        runCatching { java.util.Currency.getInstance(locale).currencyCode }.getOrNull()
            ?.takeIf { describe(it) != null } ?: "USD"

    fun toCurrency(entry: Entry, enabled: Boolean, sortOrder: Int) =
        Currency(
            code = entry.code, name = entry.name, symbol = entry.symbol, isEnabled = enabled, sortOrder = sortOrder,
            usdRate = if (entry.code == "USD") java.math.BigDecimal.ONE else null,
        )
}
