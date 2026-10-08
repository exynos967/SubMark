package io.github.submark.feature.money.ui.shared

import io.github.submark.core.domain.CurrencyConverter
import io.github.submark.core.domain.SplitCalculator
import io.github.submark.core.model.MemberStatus
import io.github.submark.core.model.SharedMember
import io.github.submark.core.model.SplitMode
import java.math.BigDecimal
import java.math.RoundingMode

enum class SplitWarning { NO_ACTIVE_MEMBERS, NO_CREATOR, RATIO_MISMATCH, FIXED_EXCESS, FIXED_REMAINING, FIXED_RATE_MISSING }

data class MemberShare(val member: SharedMember, val share: BigDecimal)

data class SplitPreview(
    val mode: SplitMode,
    val total: BigDecimal,
    val shares: List<MemberShare>,
    val activeCount: Int,
    /** RATIO: sum of active members' ratios. */
    val ratioTotal: BigDecimal,
    /** FIXED_AMOUNT: active fixed amounts minus the price (positive = excess, negative = remaining). */
    val fixedDifference: BigDecimal,
    val warnings: List<SplitWarning>,
)

/**
 * Per-member shares of one cycle in the subscription currency, via [SplitCalculator]. Fixed amounts kept in a
 * member's own payment currency are converted first; members whose amount can't be converted count as zero.
 */
object SplitPreviewCalculator {

    fun compute(
        total: BigDecimal,
        subscriptionCurrency: String,
        mode: SplitMode,
        members: List<SharedMember>,
        converter: CurrencyConverter,
    ): SplitPreview {
        var rateMissing = false
        val normalized = if (mode == SplitMode.FIXED_AMOUNT) {
            members.map { m ->
                val code = m.paymentCurrencyCode
                val fixed = m.fixedAmount
                if (fixed == null || code == null || code == subscriptionCurrency) {
                    m
                } else {
                    val converted = converter.convert(fixed, code, subscriptionCurrency)?.setScale(2, RoundingMode.HALF_UP)
                    if (converted == null && m.status == MemberStatus.ACTIVE) rateMissing = true
                    m.copy(fixedAmount = converted ?: BigDecimal.ZERO)
                }
            }
        } else {
            members
        }
        val shares = SplitCalculator.shares(total, mode, normalized)
        val active = members.count { it.status == MemberStatus.ACTIVE }
        val ratioTotal = SplitCalculator.ratioSum(members)
        val difference = SplitCalculator.fixedAmountDifference(total, normalized)
        val warnings = buildList {
            if (active == 0) add(SplitWarning.NO_ACTIVE_MEMBERS)
            if (members.none { it.isCreator }) add(SplitWarning.NO_CREATOR)
            if (active > 0 && mode == SplitMode.RATIO && !SplitCalculator.isRatioValid(members)) add(SplitWarning.RATIO_MISMATCH)
            if (active > 0 && mode == SplitMode.FIXED_AMOUNT) {
                if (difference.signum() > 0) add(SplitWarning.FIXED_EXCESS)
                if (difference.signum() < 0) add(SplitWarning.FIXED_REMAINING)
                if (rateMissing) add(SplitWarning.FIXED_RATE_MISSING)
            }
        }
        return SplitPreview(
            mode = mode,
            total = total,
            shares = members.map { MemberShare(it, shares[it.id] ?: BigDecimal.ZERO) },
            activeCount = active,
            ratioTotal = ratioTotal,
            fixedDifference = difference,
            warnings = warnings,
        )
    }
}
