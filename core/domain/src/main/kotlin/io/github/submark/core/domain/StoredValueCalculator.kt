package io.github.submark.core.domain

import java.math.BigDecimal
import java.math.RoundingMode

enum class StoredValueStatus { SUFFICIENT, LOW, EMPTY, ZERO, DEBT }

object StoredValueCalculator {
    /** LOW when the balance covers fewer than this many cycles. */
    private const val LOW_CYCLES = 2

    fun status(balance: BigDecimal, fee: BigDecimal): StoredValueStatus = when {
        balance.signum() < 0 -> StoredValueStatus.DEBT
        balance.signum() == 0 -> StoredValueStatus.ZERO
        fee.signum() > 0 && balance < fee -> StoredValueStatus.EMPTY
        fee.signum() > 0 && balance < fee.multiply(BigDecimal(LOW_CYCLES)) -> StoredValueStatus.LOW
        else -> StoredValueStatus.SUFFICIENT
    }

    /** Whole cycles the balance can still pay; null when the fee is zero (unlimited). */
    fun payableCycles(balance: BigDecimal, fee: BigDecimal): Long? =
        if (fee.signum() <= 0) null else maxOf(0L, balance.divide(fee, 0, RoundingMode.DOWN).toLong())

    /** Cycles needed to clear a debt. */
    fun debtCycles(balance: BigDecimal, fee: BigDecimal): Long =
        if (balance.signum() >= 0 || fee.signum() <= 0) 0 else balance.abs().divide(fee, 0, RoundingMode.UP).toLong()
}
