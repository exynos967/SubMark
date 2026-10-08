package io.github.submark.feature.widget

import android.content.Context
import android.content.Intent
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import io.github.submark.core.data.currency.CurrencyRepository
import io.github.submark.core.data.repository.SubscriptionRepository
import io.github.submark.core.data.settings.SettingsRepository
import io.github.submark.core.data.time.TimeProvider
import io.github.submark.core.ui.format.MoneyFormatter
import kotlinx.coroutines.flow.first
import java.math.BigDecimal
import java.util.Locale

/** Loads and converts the data the widgets render; reached from Glance receivers and callbacks. */
class WidgetLoader(private val context: Context) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun subscriptionRepository(): SubscriptionRepository
        fun currencyRepository(): CurrencyRepository
        fun settingsRepository(): SettingsRepository
        fun timeProvider(): TimeProvider
    }

    private val entry: WidgetEntryPoint by lazy {
        EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)
    }

    suspend fun upcoming(limit: Int): UpcomingWidgetState {
        val subs = entry.subscriptionRepository().getAll()
        val converter = entry.currencyRepository().converter()
        val defaultCode = entry.settingsRepository().settings.first().money.defaultCurrencyCode
        val today = entry.timeProvider().today()
        return WidgetData.upcoming(subs, today, limit, { amount, code -> converter.convert(amount, code, defaultCode) }, defaultCode)
    }

    suspend fun spendingSummary(): SpendingSummaryState {
        val subs = entry.subscriptionRepository().getAll()
        val converter = entry.currencyRepository().converter()
        val money = entry.settingsRepository().settings.first().money
        val today = entry.timeProvider().today()
        return WidgetData.spendingSummary(subs, today, money.annualBudget) { amount, code ->
            converter.convert(amount, code, money.defaultCurrencyCode)
        }
    }

    fun format(amount: BigDecimal, code: String, locale: Locale = Locale.getDefault()): String =
        MoneyFormatter.format(amount = amount, currencyCode = code, locale = locale)
}

/** Intent the widgets fire to open the app (and optionally a subscription) in the launcher activity. */
fun openAppIntent(context: Context, subscriptionId: String?): Intent {
    val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
        ?: Intent().setPackage(context.packageName)
    launch.action = Intent.ACTION_VIEW
    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    if (subscriptionId != null) launch.putExtra(SUBSCRIPTION_ID_EXTRA, subscriptionId)
    return launch
}

/** Extra the launcher activity reads to route to a subscription detail (shared with notifications). */
const val SUBSCRIPTION_ID_EXTRA = "submark.subscriptionId"
