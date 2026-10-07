package io.github.submark.core.ui.format

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.math.BigDecimal

class MoneyInputTest {
    @Test fun `sanitize keeps digits and one separator`() {
        assertThat(MoneyInput.sanitize("12a.3.4")).isEqualTo("12.34")
        assertThat(MoneyInput.sanitize("12,345")).isEqualTo("12.34")
        assertThat(MoneyInput.sanitize("1.999", maxFractionDigits = 2)).isEqualTo("1.99")
        assertThat(MoneyInput.sanitize("1.5", maxFractionDigits = 0)).isEqualTo("15")
        assertThat(MoneyInput.sanitize("-5")).isEqualTo("5")
        assertThat(MoneyInput.sanitize("-5", allowNegative = true)).isEqualTo("-5")
        assertThat(MoneyInput.sanitize("5-", allowNegative = true)).isEqualTo("5")
    }

    @Test fun `parse handles comma and partial input`() {
        assertThat(MoneyInput.parse("3,5")).isEqualTo(BigDecimal("3.5"))
        assertThat(MoneyInput.parse("12.")).isEqualTo(BigDecimal("12"))
        assertThat(MoneyInput.parse(".")).isNull()
        assertThat(MoneyInput.parse("")).isNull()
    }

    @Test fun `validate reports errors`() {
        assertThat(MoneyInput.validate("")).isEqualTo(MoneyInputError.EMPTY)
        assertThat(MoneyInput.validate("abc")).isEqualTo(MoneyInputError.INVALID)
        assertThat(MoneyInput.validate("0")).isEqualTo(MoneyInputError.ZERO)
        assertThat(MoneyInput.validate("0", allowZero = true)).isNull()
        assertThat(MoneyInput.validate("-1")).isEqualTo(MoneyInputError.NEGATIVE)
        assertThat(MoneyInput.validate("-1", allowNegative = true)).isNull()
        assertThat(MoneyInput.validate("1000000000000")).isEqualTo(MoneyInputError.TOO_LARGE)
        assertThat(MoneyInput.validate("9.99")).isNull()
    }
}
