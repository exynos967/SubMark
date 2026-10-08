package io.github.submark.feature.money

import com.google.common.truth.Truth.assertThat
import io.github.submark.core.domain.CurrencyConverter
import io.github.submark.core.model.MemberStatus
import io.github.submark.core.model.SharedMember
import io.github.submark.core.model.SplitMode
import io.github.submark.feature.money.ui.shared.SplitPreviewCalculator
import io.github.submark.feature.money.ui.shared.SplitWarning
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

class SplitPreviewCalculatorTest {
    private val converter = CurrencyConverter(mapOf("USD" to BigDecimal.ONE))

    private fun member(
        id: String, name: String, status: MemberStatus = MemberStatus.ACTIVE, isCreator: Boolean = false,
        ratio: BigDecimal? = null, fixed: BigDecimal? = null, currency: String? = null,
    ) = SharedMember(
        id = id, subscriptionId = "s", name = name, status = status, isCreator = isCreator,
        ratioPercent = ratio, fixedAmount = fixed, paymentCurrencyCode = currency,
        joinedAt = LocalDate.EPOCH, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH,
    )

    private val total = BigDecimal("30.00")

    @Test
    fun `equal split divides evenly with remainder on creator`() {
        val m1 = member("m1", "Alice", isCreator = true)
        val m2 = member("m2", "Bob")
        val m3 = member("m3", "Carol")
        val preview = SplitPreviewCalculator.compute(total, "USD", SplitMode.EQUAL, listOf(m1, m2, m3), converter)
        val alice = preview.shares.first { it.member.id == "m1" }
        val bob = preview.shares.first { it.member.id == "m2" }
        val carol = preview.shares.first { it.member.id == "m3" }
        assertThat(alice.share.compareTo(BigDecimal("10.00"))).isEqualTo(0)
        assertThat(bob.share.compareTo(BigDecimal("10.00"))).isEqualTo(0)
        assertThat(carol.share.compareTo(BigDecimal("10.00"))).isEqualTo(0)
    }

    @Test
    fun `equal split no members gives warning`() {
        val preview = SplitPreviewCalculator.compute(total, "USD", SplitMode.EQUAL, emptyList(), converter)
        assertThat(preview.warnings).contains(SplitWarning.NO_ACTIVE_MEMBERS)
        assertThat(preview.warnings).contains(SplitWarning.NO_CREATOR)
    }

    @Test
    fun `ratios must add to 100 percent`() {
        val m1 = member("m1", "A", isCreator = true, ratio = BigDecimal(50))
        val m2 = member("m2", "B", ratio = BigDecimal(50))
        assertThat(SplitPreviewCalculator.compute(total, "USD", SplitMode.RATIO, listOf(m1, m2), converter).warnings).isEmpty()

        val bad = member("m1", "A", ratio = BigDecimal(60))
        val preview = SplitPreviewCalculator.compute(total, "USD", SplitMode.RATIO, listOf(bad), converter)
        assertThat(preview.warnings).contains(SplitWarning.RATIO_MISMATCH)
    }

    @Test
    fun `ratio shares follow the percentages`() {
        val m1 = member("m1", "A", isCreator = true, ratio = BigDecimal(75))
        val m2 = member("m2", "B", ratio = BigDecimal(25))
        val preview = SplitPreviewCalculator.compute(total, "USD", SplitMode.RATIO, listOf(m1, m2), converter)
        assertThat(preview.shares.first { it.member == m1 }.share.compareTo(BigDecimal("22.50"))).isEqualTo(0)
        assertThat(preview.shares.first { it.member == m2 }.share.compareTo(BigDecimal("7.50"))).isEqualTo(0)
    }

    @Test
    fun `creator pays gets everything`() {
        val m1 = member("m1", "A", isCreator = true)
        val m2 = member("m2", "B")
        val preview = SplitPreviewCalculator.compute(total, "USD", SplitMode.CREATOR_PAYS, listOf(m1, m2), converter)
        assertThat(preview.shares.first { it.member == m2 }.share.signum()).isEqualTo(0)
    }

    @Test
    fun `fixed amount mismatch shows excess or remaining`() {
        val over = listOf(member("m1", "A", fixed = BigDecimal(20)), member("m2", "B", fixed = BigDecimal(15)))
        assertThat(SplitPreviewCalculator.compute(total, "USD", SplitMode.FIXED_AMOUNT, over, converter).warnings).contains(SplitWarning.FIXED_EXCESS)
        val under = listOf(member("m1", "A", fixed = BigDecimal(10)), member("m2", "B", fixed = BigDecimal(5)))
        assertThat(SplitPreviewCalculator.compute(total, "USD", SplitMode.FIXED_AMOUNT, under, converter).warnings).contains(SplitWarning.FIXED_REMAINING)
    }

    @Test
    fun `inactive members are excluded from shares`() {
        val active = member("m1", "A")
        val inactive = member("m2", "B", status = MemberStatus.INACTIVE)
        val preview = SplitPreviewCalculator.compute(total, "USD", SplitMode.EQUAL, listOf(active, inactive), converter)
        assertThat(preview.shares.first { it.member == inactive }.share.signum()).isEqualTo(0)
    }
}
