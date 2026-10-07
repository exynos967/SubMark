package io.github.submark.core.domain

import com.google.common.truth.Truth.assertThat
import io.github.submark.core.model.MemberStatus
import io.github.submark.core.model.SplitMode
import org.junit.Test
import java.math.BigDecimal

class SplitCalculatorTest {

    private fun Map<String, BigDecimal>.total(): BigDecimal = values.fold(BigDecimal.ZERO, BigDecimal::add)

    @Test
    fun equal_remainderOnCreator() {
        val members = listOf(member("a"), member("me", creator = true), member("b"))
        val s = SplitCalculator.shares(bd("10"), SplitMode.EQUAL, members)
        assertThat(s["me"]).isEqualTo(bd("3.34"))
        assertThat(s["a"]).isEqualTo(bd("3.33"))
        assertThat(s["b"]).isEqualTo(bd("3.33"))
        assertThat(s.total()).isEqualToIgnoringScale(bd("10"))
    }

    @Test
    fun equal_noCreator_remainderOnFirstActive() {
        val members = listOf(member("x", status = MemberStatus.INACTIVE), member("a"), member("b"), member("c"))
        val s = SplitCalculator.shares(bd("10"), SplitMode.EQUAL, members)
        assertThat(s["x"]).isEqualToIgnoringScale(BigDecimal.ZERO)
        assertThat(s["a"]).isEqualTo(bd("3.34"))
        assertThat(s.total()).isEqualToIgnoringScale(bd("10"))
    }

    @Test
    fun equal_inactiveAndPendingExcluded() {
        val members = listOf(
            member("me", creator = true),
            member("a"),
            member("i", status = MemberStatus.INACTIVE),
            member("p", status = MemberStatus.PENDING),
        )
        val s = SplitCalculator.shares(bd("9"), SplitMode.EQUAL, members)
        assertThat(s.keys).containsExactly("me", "a", "i", "p")
        assertThat(s["me"]).isEqualToIgnoringScale(bd("4.5"))
        assertThat(s["a"]).isEqualToIgnoringScale(bd("4.5"))
        assertThat(s["i"]).isEqualToIgnoringScale(BigDecimal.ZERO)
        assertThat(s["p"]).isEqualToIgnoringScale(BigDecimal.ZERO)
    }

    @Test
    fun equal_sharesAlwaysSumToTotal() {
        val totals = listOf("0.01", "0.05", "1", "9.99", "10", "100", "33.33", "1234.57")
        for (t in totals) for (n in 1..9) {
            val members = (0 until n).map { member("m$it", creator = it == n / 2) }
            val s = SplitCalculator.shares(bd(t), SplitMode.EQUAL, members)
            assertThat(s.total()).isEqualToIgnoringScale(bd(t))
            s.values.forEach { assertThat(it.signum()).isAtLeast(0) }
        }
    }

    @Test
    fun ratio_validPutsRemainderOnCreator() {
        val members = listOf(member("me", creator = true, ratio = "50"), member("a", ratio = "30"), member("b", ratio = "20"))
        val s = SplitCalculator.shares(bd("9.99"), SplitMode.RATIO, members)
        assertThat(s["a"]).isEqualTo(bd("2.99"))
        assertThat(s["b"]).isEqualTo(bd("1.99"))
        assertThat(s["me"]).isEqualTo(bd("5.01"))
        assertThat(s.total()).isEqualToIgnoringScale(bd("9.99"))
    }

    @Test
    fun ratio_thirds() {
        val members = listOf(member("me", creator = true, ratio = "33.34"), member("a", ratio = "33.33"), member("b", ratio = "33.33"))
        val s = SplitCalculator.shares(bd("10"), SplitMode.RATIO, members)
        assertThat(s.total()).isEqualToIgnoringScale(bd("10"))
        assertThat(s["a"]).isEqualTo(bd("3.33"))
    }

    @Test
    fun ratio_invalidLeavesRawShares() {
        val members = listOf(member("me", creator = true, ratio = "50"), member("a", ratio = "40"))
        val s = SplitCalculator.shares(bd("100"), SplitMode.RATIO, members)
        assertThat(s["me"]).isEqualToIgnoringScale(bd("50"))
        assertThat(s["a"]).isEqualToIgnoringScale(bd("40"))
        assertThat(SplitCalculator.isRatioValid(members)).isFalse()
    }

    @Test
    fun ratio_missingRatioCountsAsZero() {
        val members = listOf(member("me", creator = true, ratio = "100"), member("a"))
        val s = SplitCalculator.shares(bd("10"), SplitMode.RATIO, members)
        assertThat(s["me"]).isEqualToIgnoringScale(bd("10"))
        assertThat(s["a"]).isEqualToIgnoringScale(BigDecimal.ZERO)
    }

    @Test
    fun ratioValidity() {
        fun ms(vararg r: String) = r.mapIndexed { i, v -> member("m$i", ratio = v) }
        assertThat(SplitCalculator.isRatioValid(ms("50", "50"))).isTrue()
        assertThat(SplitCalculator.isRatioValid(ms("50", "50.5"))).isTrue()
        assertThat(SplitCalculator.isRatioValid(ms("50", "49.5"))).isTrue()
        assertThat(SplitCalculator.isRatioValid(ms("50", "50.6"))).isFalse()
        assertThat(SplitCalculator.isRatioValid(ms("50", "40"))).isFalse()
        assertThat(SplitCalculator.isRatioValid(emptyList())).isFalse()
        // inactive members' ratios are not counted
        val withInactive = ms("50", "50") + member("x", status = MemberStatus.INACTIVE, ratio = "30")
        assertThat(SplitCalculator.ratioSum(withInactive)).isEqualToIgnoringScale(bd("100"))
        assertThat(SplitCalculator.isRatioValid(withInactive)).isTrue()
    }

    @Test
    fun fixedAmount() {
        val members = listOf(
            member("me", creator = true, fixed = "6"),
            member("a", fixed = "5"),
            member("x", status = MemberStatus.INACTIVE, fixed = "100"),
            member("n"),
        )
        val s = SplitCalculator.shares(bd("10"), SplitMode.FIXED_AMOUNT, members)
        assertThat(s["me"]).isEqualToIgnoringScale(bd("6"))
        assertThat(s["a"]).isEqualToIgnoringScale(bd("5"))
        assertThat(s["x"]).isEqualToIgnoringScale(BigDecimal.ZERO)
        assertThat(s["n"]).isEqualToIgnoringScale(BigDecimal.ZERO)
        assertThat(SplitCalculator.fixedAmountDifference(bd("10"), members)).isEqualToIgnoringScale(bd("1"))
        assertThat(SplitCalculator.fixedAmountDifference(bd("12"), members)).isEqualToIgnoringScale(bd("-1"))
        assertThat(SplitCalculator.fixedAmountDifference(bd("11"), members)).isEqualToIgnoringScale(BigDecimal.ZERO)
    }

    @Test
    fun creatorPays() {
        val members = listOf(member("a"), member("me", creator = true), member("b"))
        val s = SplitCalculator.shares(bd("10"), SplitMode.CREATOR_PAYS, members)
        assertThat(s["me"]).isEqualToIgnoringScale(bd("10"))
        assertThat(s["a"]).isEqualToIgnoringScale(BigDecimal.ZERO)
        assertThat(s["b"]).isEqualToIgnoringScale(BigDecimal.ZERO)
        val noActiveCreator = listOf(member("me", creator = true, status = MemberStatus.INACTIVE), member("a"))
        val t = SplitCalculator.shares(bd("10"), SplitMode.CREATOR_PAYS, noActiveCreator)
        assertThat(t["a"]).isEqualToIgnoringScale(bd("10"))
        assertThat(t["me"]).isEqualToIgnoringScale(BigDecimal.ZERO)
    }

    @Test
    fun noActiveMembers_allZero() {
        val members = listOf(member("me", creator = true, status = MemberStatus.INACTIVE), member("p", status = MemberStatus.PENDING))
        SplitMode.entries.forEach { mode ->
            val s = SplitCalculator.shares(bd("10"), mode, members)
            assertThat(s.keys).containsExactly("me", "p")
            s.values.forEach { assertThat(it).isEqualToIgnoringScale(BigDecimal.ZERO) }
        }
        assertThat(SplitCalculator.shares(bd("10"), SplitMode.EQUAL, emptyList())).isEmpty()
    }

    @Test
    fun equalRatios() {
        val three = SplitCalculator.equalRatios(listOf(member("a"), member("b"), member("c")))
        assertThat(three["a"]).isEqualTo(bd("33.34"))
        assertThat(three["b"]).isEqualTo(bd("33.33"))
        assertThat(three.total()).isEqualToIgnoringScale(bd("100"))
        for (n in 1..12) {
            val r = SplitCalculator.equalRatios((0 until n).map { member("m$it") })
            assertThat(r).hasSize(n)
            assertThat(r.total()).isEqualToIgnoringScale(bd("100"))
        }
        val withInactive = SplitCalculator.equalRatios(listOf(member("a"), member("x", status = MemberStatus.INACTIVE), member("b")))
        assertThat(withInactive.keys).containsExactly("a", "b")
        assertThat(withInactive["a"]).isEqualToIgnoringScale(bd("50"))
        assertThat(SplitCalculator.equalRatios(listOf(member("x", status = MemberStatus.PENDING)))).isEmpty()
    }

    @Test
    fun equalRatios_feedRatioModeValidly() {
        val members = (0 until 7).map { member("m$it", creator = it == 0) }
        val ratios = SplitCalculator.equalRatios(members)
        val withRatios = members.map { it.copy(ratioPercent = ratios[it.id]) }
        assertThat(SplitCalculator.isRatioValid(withRatios)).isTrue()
        val s = SplitCalculator.shares(bd("19.99"), SplitMode.RATIO, withRatios)
        assertThat(s.total()).isEqualToIgnoringScale(bd("19.99"))
    }
}
