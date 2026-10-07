package io.github.submark.core.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class StoredValueCalculatorTest {

    private val fee = bd("10")

    @Test
    fun status() {
        assertThat(StoredValueCalculator.status(bd("-0.01"), fee)).isEqualTo(StoredValueStatus.DEBT)
        assertThat(StoredValueCalculator.status(bd("0"), fee)).isEqualTo(StoredValueStatus.ZERO)
        assertThat(StoredValueCalculator.status(bd("0.00"), fee)).isEqualTo(StoredValueStatus.ZERO)
        assertThat(StoredValueCalculator.status(bd("0.01"), fee)).isEqualTo(StoredValueStatus.EMPTY)
        assertThat(StoredValueCalculator.status(bd("9.99"), fee)).isEqualTo(StoredValueStatus.EMPTY)
        assertThat(StoredValueCalculator.status(bd("10"), fee)).isEqualTo(StoredValueStatus.LOW)
        assertThat(StoredValueCalculator.status(bd("19.99"), fee)).isEqualTo(StoredValueStatus.LOW)
        assertThat(StoredValueCalculator.status(bd("20"), fee)).isEqualTo(StoredValueStatus.SUFFICIENT)
        assertThat(StoredValueCalculator.status(bd("20.00"), bd("10.00"))).isEqualTo(StoredValueStatus.SUFFICIENT)
    }

    @Test
    fun status_zeroFee() {
        assertThat(StoredValueCalculator.status(bd("5"), bd("0"))).isEqualTo(StoredValueStatus.SUFFICIENT)
        assertThat(StoredValueCalculator.status(bd("0"), bd("0"))).isEqualTo(StoredValueStatus.ZERO)
        assertThat(StoredValueCalculator.status(bd("-5"), bd("0"))).isEqualTo(StoredValueStatus.DEBT)
    }

    @Test
    fun payableCycles() {
        assertThat(StoredValueCalculator.payableCycles(bd("25"), fee)).isEqualTo(2)
        assertThat(StoredValueCalculator.payableCycles(bd("20"), fee)).isEqualTo(2)
        assertThat(StoredValueCalculator.payableCycles(bd("9.99"), fee)).isEqualTo(0)
        assertThat(StoredValueCalculator.payableCycles(bd("-5"), fee)).isEqualTo(0)
        assertThat(StoredValueCalculator.payableCycles(bd("100"), bd("0"))).isNull()
        assertThat(StoredValueCalculator.payableCycles(bd("100"), bd("-1"))).isNull()
        assertThat(StoredValueCalculator.payableCycles(bd("100"), bd("3"))).isEqualTo(33)
    }

    @Test
    fun debtCycles() {
        assertThat(StoredValueCalculator.debtCycles(bd("-25"), fee)).isEqualTo(3)
        assertThat(StoredValueCalculator.debtCycles(bd("-20"), fee)).isEqualTo(2)
        assertThat(StoredValueCalculator.debtCycles(bd("-0.01"), fee)).isEqualTo(1)
        assertThat(StoredValueCalculator.debtCycles(bd("0"), fee)).isEqualTo(0)
        assertThat(StoredValueCalculator.debtCycles(bd("5"), fee)).isEqualTo(0)
        assertThat(StoredValueCalculator.debtCycles(bd("-5"), bd("0"))).isEqualTo(0)
    }
}
