package io.github.submark.core.model

/** Preset payment methods. Names are English fallbacks; the UI localizes them by [PaymentMethod.id]. */
object SystemPaymentMethods {
    const val NONE = "pm_none"
    const val ALIPAY = "pm_alipay"
    const val APPLE_PAY = "pm_apple_pay"
    const val BANK_CARD = "pm_bank_card"
    const val CASH = "pm_cash"
    const val CREDIT_CARD = "pm_credit_card"
    const val GOOGLE_PAY = "pm_google_pay"
    const val PAYPAL = "pm_paypal"
    const val WECHAT_PAY = "pm_wechat_pay"

    val defaults: List<PaymentMethod> = listOf(
        Triple(NONE, "None", "block"),
        Triple(ALIPAY, "Alipay", "account_balance_wallet"),
        Triple(APPLE_PAY, "Apple Pay", "phone_iphone"),
        Triple(BANK_CARD, "Bank Card", "credit_card"),
        Triple(CASH, "Cash", "payments"),
        Triple(CREDIT_CARD, "Credit Card", "credit_score"),
        Triple(GOOGLE_PAY, "Google Pay", "contactless"),
        Triple(PAYPAL, "PayPal", "account_balance"),
        Triple(WECHAT_PAY, "WeChat Pay", "chat"),
    ).mapIndexed { index, (id, name, icon) -> PaymentMethod(id = id, name = name, iconValue = icon, isSystem = true, sortOrder = index) }
}
