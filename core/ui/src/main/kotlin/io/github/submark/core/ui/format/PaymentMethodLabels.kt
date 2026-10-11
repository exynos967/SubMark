package io.github.submark.core.ui.format

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.submark.core.model.PaymentMethod
import io.github.submark.core.model.SystemPaymentMethods
import io.github.submark.core.ui.R

/** Preset payment methods are stored with English names; show them localized. */
@Composable
fun PaymentMethod.localizedName(): String = when (id) {
    SystemPaymentMethods.NONE -> stringResource(R.string.ui_pm_none)
    SystemPaymentMethods.ALIPAY -> stringResource(R.string.ui_pm_alipay)
    SystemPaymentMethods.APPLE_PAY -> stringResource(R.string.ui_pm_apple_pay)
    SystemPaymentMethods.BANK_CARD -> stringResource(R.string.ui_pm_bank_card)
    SystemPaymentMethods.CASH -> stringResource(R.string.ui_pm_cash)
    SystemPaymentMethods.CREDIT_CARD -> stringResource(R.string.ui_pm_credit_card)
    SystemPaymentMethods.GOOGLE_PAY -> stringResource(R.string.ui_pm_google_pay)
    SystemPaymentMethods.PAYPAL -> stringResource(R.string.ui_pm_paypal)
    SystemPaymentMethods.WECHAT_PAY -> stringResource(R.string.ui_pm_wechat_pay)
    else -> name
}
