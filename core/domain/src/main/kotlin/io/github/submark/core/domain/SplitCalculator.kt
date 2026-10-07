package io.github.submark.core.domain

import io.github.submark.core.model.MemberStatus
import io.github.submark.core.model.SharedMember
import io.github.submark.core.model.SplitMode
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/** Per-member share of a shared subscription's per-cycle price. */
object SplitCalculator {
    private val MC = MathContext.DECIMAL64
    private val HUNDRED = BigDecimal(100)
    private val RATIO_TOLERANCE = BigDecimal("0.5")

    /**
     * Shares keyed by member id. Inactive/pending members get zero. EQUAL and RATIO round to [scale]
     * and put the rounding remainder on the creator (or first active member) so shares sum to [total].
     * FIXED_AMOUNT values are taken as-is (already in subscription currency).
     */
    fun shares(total: BigDecimal, mode: SplitMode, members: List<SharedMember>, scale: Int = 2): Map<String, BigDecimal> {
        val active = members.filter { it.status == MemberStatus.ACTIVE }
        val result = members.associate { it.id to BigDecimal.ZERO }.toMutableMap()
        if (active.isEmpty()) return result
        when (mode) {
            SplitMode.EQUAL -> {
                val each = total.divide(BigDecimal(active.size), scale, RoundingMode.DOWN)
                active.forEach { result[it.id] = each }
            }
            SplitMode.RATIO -> active.forEach {
                val ratio = it.ratioPercent ?: BigDecimal.ZERO
                result[it.id] = total.multiply(ratio, MC).divide(HUNDRED, scale, RoundingMode.DOWN)
            }
            SplitMode.FIXED_AMOUNT -> active.forEach { result[it.id] = it.fixedAmount ?: BigDecimal.ZERO }
            SplitMode.CREATOR_PAYS -> {
                val payer = active.firstOrNull { it.isCreator } ?: active.first()
                result[payer.id] = total
            }
        }
        if (mode == SplitMode.EQUAL || mode == SplitMode.RATIO) {
            val remainder = total.setScale(scale, RoundingMode.HALF_UP) - active.sumOf { result.getValue(it.id) }
            val payer = active.firstOrNull { it.isCreator } ?: active.first()
            if (mode == SplitMode.EQUAL || ratioSum(active).subtract(HUNDRED).abs() <= RATIO_TOLERANCE) {
                result[payer.id] = result.getValue(payer.id) + remainder
            }
        }
        return result
    }

    fun ratioSum(members: List<SharedMember>): BigDecimal =
        members.filter { it.status == MemberStatus.ACTIVE }.sumOf { it.ratioPercent ?: BigDecimal.ZERO }

    fun isRatioValid(members: List<SharedMember>): Boolean = ratioSum(members).subtract(HUNDRED).abs() <= RATIO_TOLERANCE

    /** Positive = members pay more than the price ("excess"), negative = remaining. */
    fun fixedAmountDifference(total: BigDecimal, members: List<SharedMember>): BigDecimal =
        members.filter { it.status == MemberStatus.ACTIVE }.sumOf { it.fixedAmount ?: BigDecimal.ZERO } - total

    /** Equal ratios for active members, rounded to 2 decimals, remainder on the creator (or first active). */
    fun equalRatios(members: List<SharedMember>): Map<String, BigDecimal> {
        val active = members.filter { it.status == MemberStatus.ACTIVE }
        if (active.isEmpty()) return emptyMap()
        val each = HUNDRED.divide(BigDecimal(active.size), 2, RoundingMode.DOWN)
        val map = active.associate { it.id to each }.toMutableMap()
        val payer = active.firstOrNull { it.isCreator } ?: active.first()
        map[payer.id] = each + (HUNDRED - each.multiply(BigDecimal(active.size)))
        return map
    }
}
