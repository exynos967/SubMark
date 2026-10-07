package io.github.submark.core.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import io.github.submark.core.ui.format.MoneyFormatter
import io.github.submark.core.ui.format.MoneyInput
import io.github.submark.core.ui.format.MoneyInputError
import io.github.submark.core.ui.theme.SubMarkTheme
import java.math.BigDecimal
import java.util.Locale

/** App-wide money display preferences; provide once near the root (e.g. from the "hide decimals" setting). */
@Immutable
data class MoneyDisplayOptions(val hideDecimals: Boolean = false)

val LocalMoneyDisplayOptions = staticCompositionLocalOf { MoneyDisplayOptions() }

@Composable
@ReadOnlyComposable
fun currentLocale(): Locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()

/** [MoneyFormatter.format] with the current locale and [LocalMoneyDisplayOptions]. */
@Composable
@ReadOnlyComposable
fun formatMoney(
    amount: BigDecimal,
    currencyCode: String,
    symbol: String? = null,
    compact: Boolean = false,
    showPlusSign: Boolean = false,
    hideDecimals: Boolean = LocalMoneyDisplayOptions.current.hideDecimals,
): String = MoneyFormatter.format(
    amount = amount,
    currencyCode = currencyCode,
    symbol = symbol,
    locale = currentLocale(),
    hideDecimals = hideDecimals,
    compact = compact,
    showPlusSign = showPlusSign,
)

/**
 * Formatted amount. [colorBySign] paints positives with the success color and negatives with the error color.
 */
@Composable
fun MoneyText(
    amount: BigDecimal,
    currencyCode: String,
    modifier: Modifier = Modifier,
    symbol: String? = null,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    compact: Boolean = false,
    showPlusSign: Boolean = false,
    colorBySign: Boolean = false,
    hideDecimals: Boolean = LocalMoneyDisplayOptions.current.hideDecimals,
) {
    val resolvedColor = when {
        !colorBySign -> color
        amount.signum() > 0 -> SubMarkTheme.extendedColors.success
        amount.signum() < 0 -> MaterialTheme.colorScheme.error
        else -> color.takeOrElse { LocalContentColor.current }
    }
    Text(
        text = formatMoney(amount, currencyCode, symbol, compact, showPlusSign, hideDecimals),
        modifier = modifier,
        style = style,
        color = resolvedColor,
        maxLines = 1,
    )
}

private inline fun Color.takeOrElse(block: () -> Color): Color = if (this != Color.Unspecified) this else block()

/**
 * Decimal amount input. Filters keystrokes through [MoneyInput.sanitize] and shows validation errors
 * once the user has typed something (or always when [showErrorWhenEmpty]).
 *
 * @param value raw text state owned by the caller; parse with [MoneyInput.parse].
 */
@Composable
fun MoneyInputField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    currencySymbol: String? = null,
    maxFractionDigits: Int = 2,
    allowZero: Boolean = false,
    allowNegative: Boolean = false,
    showErrorWhenEmpty: Boolean = false,
    enabled: Boolean = true,
    imeAction: ImeAction = ImeAction.Done,
    onImeAction: (() -> Unit)? = null,
) {
    val error: MoneyInputError? = MoneyInput.validate(value, allowZero = allowZero, allowNegative = allowNegative)
        ?.takeIf { value.isNotEmpty() || showErrorWhenEmpty }
    OutlinedTextField(
        modifier = modifier,
        value = value,
        onValueChange = { onValueChange(MoneyInput.sanitize(it, maxFractionDigits, allowNegative)) },
        label = label?.let { { Text(it) } },
        prefix = currencySymbol?.let { { Text(it) } },
        isError = error != null,
        supportingText = error?.let { { Text(stringResource(it.messageRes)) } },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = imeAction),
        keyboardActions = if (onImeAction != null) KeyboardActions(onAny = { onImeAction() }) else KeyboardActions.Default,
    )
}

@Preview(showBackground = true)
@Composable
private fun MoneyPreview() {
    SubMarkTheme {
        Column {
            MoneyText(BigDecimal("1234.5"), "USD", symbol = "$")
            MoneyText(BigDecimal("-12"), "CNY", symbol = "¥", colorBySign = true)
            MoneyInputField(value = "12.5", onValueChange = {}, label = "Price", currencySymbol = "$")
        }
    }
}
