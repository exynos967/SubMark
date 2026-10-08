package io.github.submark.feature.notifications.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import io.github.submark.feature.notifications.R

/**
 * Every notification channel of the app lives here so other features can reuse the ids
 * (price drop → backup feature, API budget → integrations feature post their own
 * notifications on these channels without creating them).
 */
object NotificationChannels {
    const val PAYMENT_REMINDERS = "payment_reminders"
    const val ALERTS = "alerts"
    const val PRICE_DROP = "price_drop"
    const val BACKUP_STATUS = "backup_status"
    const val API_BUDGET = "api_budget"

    val ALL = listOf(PAYMENT_REMINDERS, ALERTS, PRICE_DROP, BACKUP_STATUS, API_BUDGET)

    fun create(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channels = listOf(
            NotificationChannel(PAYMENT_REMINDERS, context.getString(R.string.notifications_channel_reminders), NotificationManager.IMPORTANCE_HIGH)
                .apply { description = context.getString(R.string.notifications_channel_reminders_desc) },
            NotificationChannel(ALERTS, context.getString(R.string.notifications_channel_alerts), NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = context.getString(R.string.notifications_channel_alerts_desc) },
            NotificationChannel(PRICE_DROP, context.getString(R.string.notifications_channel_price_drop), NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = context.getString(R.string.notifications_channel_price_drop_desc) },
            NotificationChannel(BACKUP_STATUS, context.getString(R.string.notifications_channel_backup), NotificationManager.IMPORTANCE_LOW)
                .apply { description = context.getString(R.string.notifications_channel_backup_desc) },
            NotificationChannel(API_BUDGET, context.getString(R.string.notifications_channel_api_budget), NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = context.getString(R.string.notifications_channel_api_budget_desc) },
        )
        manager.createNotificationChannels(channels)
    }
}
