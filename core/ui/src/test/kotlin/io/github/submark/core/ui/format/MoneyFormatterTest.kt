package io.github.submark.core.ui.format

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.math.BigDecimal
import java.util.Locale

class MoneyFormatterTest {
    private fun fmt(
        v: String,
        code: String = "USD",
        symbol: String? = "$",
        locale: Locale = Locale.US,
        hide: Boolean = false,
        compact: Boolean = false,
        plus: Boolean = false,
    ) = MoneyFormatter.format(BigDecimal(v), code, symbol, locale, hide, compact, plus)

    @Test fun `formats with grouping and currency digits`() {
        assertThat(fmt("1234.5")).isEqualTo("$1,234.50")
        assertThat(fmt("0")).isEqualTo("$0.00")
        assertThat(fmt("1234.5", code = "JPY", symbol = "¥")).isEqualTo("¥1,235")
    }

    @Test fun `negative and plus sign go before the symbol`() {
        assertThat(fmt("-12.3")).isEqualTo("-$12.30")
        assertThat(fmt("5", plus = true)).isEqualTo("+$5.00")
        assertThat(fmt("0", plus = true)).isEqualTo("$0.00")
    }

    @Test fun `alphabetic symbols get a space`() {
        assertThat(fmt("9.5", code = "CHF", symbol = "CHF")).isEqualTo("CHF 9.50")
    }

    @Test fun `hide decimals truncates instead of rounding`() {
        assertThat(fmt("12.99", hide = true)).isEqualTo("$12")
        assertThat(fmt("-12.99", hide = true)).isEqualTo("-$12")
    }

    @Test fun `uses locale separators`() {
        assertThat(fmt("1234.5", code = "EUR", symbol = "€", locale = Locale.GERMANY)).isEqualTo("€1.234,50")
    }

    @Test fun `compact western units`() {
        assertThat(fmt("999", compact = true)).isEqualTo("$999.00")
        assertThat(fmt("1234", compact = true)).isEqualTo("$1.2K")
        assertThat(fmt("1000", compact = true)).isEqualTo("$1K")
        assertThat(fmt("2500000", compact = true)).isEqualTo("$2.5M")
        assertThat(fmt("999950", compact = true)).isEqualTo("$1M")
        assertThat(fmt("-1500", compact = true)).isEqualTo("-$1.5K")
    }

    @Test fun `compact chinese units`() {
        assertThat(fmt("12345", code = "CNY", symbol = "¥", locale = Locale.SIMPLIFIED_CHINESE, compact = true)).isEqualTo("¥1.2万")
        assertThat(fmt("123456789", code = "CNY", symbol = "¥", locale = Locale.SIMPLIFIED_CHINESE, compact = true)).isEqualTo("¥1.2亿")
        assertThat(fmt("9999", code = "CNY", symbol = "¥", locale = Locale.SIMPLIFIED_CHINESE, compact = true)).isEqualTo("¥9,999.00")
    }

    @Test fun `unknown codes fall back to code and two digits`() {
        assertThat(MoneyFormatter.fractionDigits("XYZ1")).isEqualTo(2)
        assertThat(fmt("3", code = "PTS", symbol = null)).isEqualTo("PTS 3.00")
    }
}
