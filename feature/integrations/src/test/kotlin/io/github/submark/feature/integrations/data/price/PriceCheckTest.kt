package io.github.submark.feature.integrations.data.price

import com.google.common.truth.Truth.assertThat
import io.github.submark.core.model.PriceRecord
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

class PriceCheckTest {

    private fun record(price: String, region: String = "US", at: Instant = Instant.ofEpochSecond(0)) =
        PriceRecord(subscriptionId = "s", region = region, price = BigDecimal(price), currencyCode = "USD", checkedAt = at)

    @Test
    fun `drop percent only when strictly lower`() {
        assertThat(PriceCheck.dropPercent(BigDecimal("10"), BigDecimal("8"))).isWithin(0.01).of(20.0)
        assertThat(PriceCheck.dropPercent(BigDecimal("10"), BigDecimal("10"))).isNull()
        assertThat(PriceCheck.dropPercent(BigDecimal("10"), BigDecimal("12"))).isNull()
        assertThat(PriceCheck.dropPercent(BigDecimal("0"), BigDecimal("0"))).isNull()
    }

    @Test
    fun `stats over region history`() {
        // newest first
        val records = listOf(record("9", at = Instant.ofEpochSecond(300)), record("3", at = Instant.ofEpochSecond(200)), record("6", at = Instant.ofEpochSecond(100)))
        val stats = PriceCheck.stats("US", records)!!
        assertThat(stats.current).isEqualTo(BigDecimal("9"))
        assertThat(stats.lowest).isEqualTo(BigDecimal("3"))
        assertThat(stats.highest).isEqualTo(BigDecimal("9"))
        assertThat(stats.average).isEqualTo(BigDecimal("6.00"))
        assertThat(stats.recordCount).isEqualTo(3)
    }

    @Test
    fun `empty history yields no stats`() {
        assertThat(PriceCheck.stats("US", emptyList())).isNull()
    }
}
