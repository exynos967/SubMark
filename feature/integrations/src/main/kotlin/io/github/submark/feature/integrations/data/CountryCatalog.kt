package io.github.submark.feature.integrations.data

import java.util.Locale

/** Storefront code (ISO alpha-2, as used by the iTunes APIs) + localized display name. */
data class Country(val code: String, val displayName: String)

/** ISO-3166 alpha-2 country list for region pickers; storefronts commonly used come preselected by convention. */
object CountryCatalog {
    /** Preselected storefronts for new price monitors (task brief). */
    val PRESELECTED = listOf("US", "CN", "JP", "HK", "TW", "TR", "NG", "GB", "DE")

    private val codes: List<String> =
        Locale.getISOCountries().toList().sortedBy { nameOf(it.lowercase(Locale.US)) }

    fun nameOf(code: String): String =
        Locale.Builder().setRegion(code.uppercase(Locale.US)).build().displayCountry.ifBlank { code.uppercase() }

    fun all(): List<Country> = codes.map { Country(it, nameOf(it)) }

    fun isValid(code: String): Boolean = codes.contains(code.uppercase(Locale.US))
}
