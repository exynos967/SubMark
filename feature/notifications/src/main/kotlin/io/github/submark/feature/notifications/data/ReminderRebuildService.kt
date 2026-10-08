package io.github.submark.feature.notifications.data

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.submark.core.data.repository.SubscriptionExtrasRepository
import io.github.submark.core.data.repository.SubscriptionRepository
import io.github.submark.core.data.settings.SettingsRepository
import io.github.submark.core.data.time.TimeProvider
import io.github.submark.core.model.Subscription
import io.github.submark.core.ui.format.MoneyFormatter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Recomputes and (re)schedules every payment reminder (spec §2.1). Cancel + rebuild keeps
 * scheduling idempotent; request codes are deterministic so a double run converges.
 */
@Singleton
class ReminderRebuildService @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val subscriptions: SubscriptionRepository,
    private val extras: SubscriptionExtrasRepository,
    private val settings: SettingsRepository,
    private val scheduler: ReminderAlarmScheduler,
    private val store: ScheduledRemindersStore,
    private val time: TimeProvider,
) {
    private val mutex = Mutex()

    /** Rebuilds all reminders. When the master switch is off, everything is cancelled instead. */
    suspend fun rebuildAll() = mutex.withLock {
        NotificationChannels.create(context)
        val prefs = settings.settings.first().notifications
        val subs = subscriptions.getAll()
        val reminders = extras.observeAllReminders().first().groupBy { it.subscriptionId }
        val zone = time.zone()
        val now = java.time.LocalDateTime.now(zone)

        val enabled = prefs.enabled && NotificationManagerCompat.from(context).areNotificationsEnabled()
        val triggers = if (enabled) {
            ReminderTriggers.compute(subs, reminders, prefs, now, zone) { amountOf(it) }
        } else {
            emptyList()
        }

        val previous = store.load()
        val newCodes = triggers.mapTo(HashSet()) { it.requestCode }
        // Cancel stale (no longer wanted) and legacy ids; keep the rest untouched.
        previous.filter { it.requestCode !in newCodes }
            .forEach { scheduler.cancel(it.requestCode) }

        for (trigger in triggers) {
            scheduler.schedule(trigger, ReminderTriggers.triggerMillis(trigger, zone))
        }
        store.save(triggers.map { it.toScheduled(zone) })
    }

    /** Currently scheduled reminders, for the diagnostics screen. */
    suspend fun scheduled(): List<ScheduledReminder> = store.load()

    fun hasPermission(): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun canScheduleExact(): Boolean = scheduler.canScheduleExact()

    fun scheduleTest() = scheduler.scheduleTest("", "SubMark", ReminderAlarmScheduler.TEST_DELAY_MILLIS)

    private fun amountOf(sub: Subscription): String =
        MoneyFormatter.format(sub.price, sub.currencyCode)
}
