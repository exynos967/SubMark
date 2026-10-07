package io.github.submark.core.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.math.BigDecimal
import java.math.RoundingMode

class CurrencyConverterTest {

    private val c = CurrencyConverter(mapOf("EUR" to bd("0.9"), "JPY" to bd("150"), "CNY" to bd("7.2"), "BAD" to bd("0"), "NEG" to bd("-1")))

    @Test
    fun rate_usdIsPivot() {
        assertThat(c.rate("USD")).isEqualToIgnoringScale(BigDecimal.ONE)
        assertThat(CurrencyConverter(mapOf("USD" to bd("2"))).rate("USD")).isEqualToIgnoringScale(BigDecimal.ONE)
        assertThat(c.rate("EUR")).isEqualToIgnoringScale(bd("0.9"))
        assertThat(c.rate("XXX")).isNull()
        assertThat(c.rate("BAD")).isNull()
        assertThat(c.rate("NEG")).isNull()
    }

    @Test
    fun convert_viaUsd() {
        assertThat(c.convert(bd("100"), "USD", "EUR")).isEqualToIgnoringScale(bd("90"))
        assertThat(c.convert(bd("90"), "EUR", "USD")).isEqualToIgnoringScale(bd("100"))
        assertThat(c.convert(bd("90"), "EUR", "JPY")).isEqualToIgnoringScale(bd("15000"))
        assertThat(c.convert(bd("72"), "CNY", "JPY")).isEqualToIgnoringScale(bd("1500"))
    }

    @Test
    fun convert_sameCurrencyNeedsNoRate() {
        assertThat(c.convert(bd("12.34"), "XXX", "XXX")).isEqualTo(bd("12.34"))
    }

    @Test
    fun convert_missingOrInvalidRateIsNull() {
        assertThat(c.convert(bd("1"), "XXX", "EUR")).isNull()
        assertThat(c.convert(bd("1"), "EUR", "XXX")).isNull()
        assertThat(c.convert(bd("1"), "BAD", "USD")).isNull()
        assertThat(c.convert(bd("1"), "USD", "NEG")).isNull()
        assertThat(c.crossRate("EUR", "XXX")).isNull()
    }

    @Test
    fun crossRate() {
        assertThat(c.crossRate("EUR", "JPY")!!.setScale(4, RoundingMode.HALF_UP)).isEqualTo(bd("166.6667"))
        assertThat(c.crossRate("USD", "CNY")).isEqualToIgnoringScale(bd("7.2"))
        assertThat(c.crossRate("JPY", "JPY")).isEqualToIgnoringScale(BigDecimal.ONE)
    }

    @Test
    fun convert_roundTripIsStable() {
        val there = c.convert(bd("100"), "EUR", "JPY")!!
        val back = c.convert(there, "JPY", "EUR")!!
        assertThat(back.subtract(bd("100")).abs()).isLessThan(bd("1E-10"))
    }

    @Test
    fun usdRateFromDefault() {
        // Default currency EUR (0.9 per USD); user enters "1 EUR = 160 JPY" -> 144 JPY per USD.
        val r = CurrencyConverter.usdRateFromDefault(bd("160"), bd("0.9"))
        assertThat(r).isEqualToIgnoringScale(bd("144"))
        val conv = CurrencyConverter(mapOf("EUR" to bd("0.9"), "JPY" to r))
        assertThat(conv.convert(bd("1"), "EUR", "JPY")!!.setScale(8, RoundingMode.HALF_UP)).isEqualToIgnoringScale(bd("160"))
        // default = USD -> stored rate equals the entered value
        assertThat(CurrencyConverter.usdRateFromDefault(bd("7.2"), BigDecimal.ONE)).isEqualToIgnoringScale(bd("7.2"))
    }
}
