package io.github.submark.core.data.seed

import io.github.submark.core.model.Category
import io.github.submark.core.model.IconType
import io.github.submark.core.model.PaymentMethod
import io.github.submark.core.model.SystemCategory

/** Preset categories with stable ids. Names are null so the UI shows the localized preset name. */
object SystemCategories {
    fun idOf(key: SystemCategory) = "cat_${key.name.lowercase()}"

    val OTHER_ID = idOf(SystemCategory.OTHER)

    private val style = mapOf(
        SystemCategory.VIDEO to ("movie" to "#E53935"),
        SystemCategory.MUSIC to ("music" to "#8E24AA"),
        SystemCategory.ENTERTAINMENT to ("theater" to "#FB8C00"),
        SystemCategory.GAMING to ("gamepad" to "#3949AB"),
        SystemCategory.PRODUCTIVITY to ("work" to "#1E88E5"),
        SystemCategory.UTILITY to ("build" to "#546E7A"),
        SystemCategory.AI to ("sparkle" to "#00ACC1"),
        SystemCategory.NEWS to ("newspaper" to "#6D4C41"),
        SystemCategory.LIFESTYLE to ("spa" to "#43A047"),
        SystemCategory.OTHER to ("category" to "#757575"),
    )

    /** Factory-state preset rows (visible, preset icon and color, preset order). */
    val defaults: List<Category> = SystemCategory.entries.mapIndexed { index, key ->
        val (icon, color) = style.getValue(key)
        Category(id = idOf(key), systemKey = key, iconType = IconType.SYMBOL, iconValue = icon, colorHex = color, sortOrder = index)
    }

    fun default(key: SystemCategory): Category = defaults.first { it.systemKey == key }
}

/** Preset payment methods. Names are English fallbacks; the UI may localize by [PaymentMethod.id]. */
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
