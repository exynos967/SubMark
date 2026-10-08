package io.github.submark.feature.notifications.data

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.submark.core.data.repository.SubscriptionRepository
import io.github.submark.core.data.settings.SettingsRepository
import io.github.submark.core.database.dao.SubscriptionDao
import io.github.submark.core.domain.BillingCalculator
import io.github.submark.core.model.CycleUnit
import io.github.submark.core.model.Subscription
import io.github.submark.core.model.SubscriptionKind
import io.github.submark.core.ui.format.MoneyFormatter
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** Result of a calendar sync attempt, surfaced on the reminder/settings screens. */
enum class CalendarSyncResult { SYNCED, REMOVED, DISABLED, NO_PERMISSION, FAILED }

/**
 * Syncs subscriptions to an app-owned local "SubMark" calendar (CalendarContract): one recurring
 * event per subscription with an RRULE from the billing cycle; single-cycle subscriptions get one
 * all-day event. Updates on change, deletes when disabled/deleted.
 */
@Singleton
class CalendarSyncManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: SubscriptionRepository,
    private val subscriptionDao: SubscriptionDao,
    private val settings: SettingsRepository,
) {
    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED

    suspend fun isGloballyEnabled(): Boolean = settings.settings.first().notifications.calendarSyncEnabled

    /** Syncs every subscription that opts in; resets events of subscriptions whose toggle is off. */
    suspend fun syncAll() {
        val globallyOn = isGloballyEnabled()
        for (sub in repository.getAll()) {
            syncOne(sub, globallyOn)
        }
    }

    /** Syncs (or cleans up) the subscriptions with [ids]. [ids] null = everything. */
    suspend fun sync(ids: Set<String>?) {
        val globallyOn = isGloballyEnabled()
        val subs = if (ids == null) repository.getAll() else ids.mapNotNull { repository.get(it) }
        if (ids != null) {
            // Deleted subscriptions leave no row; remove their events via the stored ids is
            // impossible without a side table, so orphan events are swept whenever ids == null.
            subs.forEach { syncOne(it, globallyOn) }
        } else {
            subs.forEach { syncOne(it, globallyOn) }
            removeOrphans(subs)
        }
    }

    /** Toggles sync for one subscription, applying/removing its event immediately. */
    suspend fun setEnabledFor(subscriptionId: String, enabled: Boolean): CalendarSyncResult {
        val sub = repository.get(subscriptionId) ?: return CalendarSyncResult.FAILED
        subscriptionDao.upsert(sub.copy(calendarSyncEnabled = enabled, calendarEventId = null, updatedAt = sub.updatedAt))
        sub.calendarEventId?.let(::removeEvent)
        return if (enabled) {
            syncOne(sub.copy(calendarSyncEnabled = true, calendarEventId = null), isGloballyEnabled())
        } else {
            CalendarSyncResult.REMOVED
        }
    }

    suspend fun syncOne(sub: Subscription, globallyOn: Boolean): CalendarSyncResult {
        val wanted = globallyOn && sub.calendarSyncEnabled && sub.id.isNotEmpty() &&
            sub.kind != SubscriptionKind.WISHLIST && sub.kind != SubscriptionKind.LIFETIME &&
            (sub.nextPaymentDate != null)
        val existing = sub.calendarEventId
        if (!wanted) {
            if (existing != null) {
                removeEvent(existing)
                persistEventId(sub, null)
            }
            return if (!globallyOn || !sub.calendarSyncEnabled) CalendarSyncResult.DISABLED else CalendarSyncResult.REMOVED
        }
        if (!hasPermission()) return CalendarSyncResult.NO_PERMISSION
        return runCatching {
            val calendarId = ensureCalendar()
            val dtStart = (sub.nextPaymentDate ?: sub.startDate).atStartOfDay()
            val dtEnd = dtStart.plusDays(1)
            val values = ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, calendarId)
                put(CalendarContract.Events.TITLE, sub.name)
                put(CalendarContract.Events.DESCRIPTION, MoneyFormatter.format(sub.price, sub.currencyCode))
                put(CalendarContract.Events.DTSTART, java.util.TimeZone.getDefault().let { dtStart.atZone(it.toZoneId()).toInstant().toEpochMilli() })
                put(CalendarContract.Events.DTEND, java.util.TimeZone.getDefault().let { dtEnd.atZone(it.toZoneId()).toInstant().toEpochMilli() })
                put(CalendarContract.Events.ALL_DAY, 1)
                put(CalendarContract.Events.EVENT_TIMEZONE, java.util.TimeZone.getDefault().id)
                val rrule = rruleOf(sub)
                if (rrule != null) {
                    put(CalendarContract.Events.RRULE, rrule)
                    // All-day recurring events must use UTC floating times of whole days.
                    put(CalendarContract.Events.DTSTART, dtStart.toLocalDate().toEpochDay() * 86_400_000L)
                    put(CalendarContract.Events.DTEND, dtStart.toLocalDate().toEpochDay() * 86_400_000L + 86_400_000L)
                    put(CalendarContract.Events.EVENT_TIMEZONE, "UTC")
                    sub.endDate?.let {
                        put(CalendarContract.Events.RRULE, rrule + ";UNTIL=" + it.toString().replace("-", "") + "T235959Z")
                    }
                }
            }
            val eventId = if (existing != null) {
                context.contentResolver.update(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, existing), values, null, null)
                existing
            } else {
                val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
                uri?.lastPathSegment?.toLongOrNull()
            }
            if (eventId != null && eventId != existing) persistEventId(sub, eventId)
            CalendarSyncResult.SYNCED
        }.getOrElse { CalendarSyncResult.FAILED }
    }

    private fun rruleOf(sub: Subscription): String? {
        if (sub.isSingleCycle) return null
        val cycle = BillingCalculator.cycleLength(sub.billingCycle, sub.customCycleCount, sub.customCycleUnit) ?: return null
        val freq = when (cycle.unit) {
            CycleUnit.DAY -> "DAILY"
            CycleUnit.WEEK -> "WEEKLY"
            CycleUnit.MONTH -> "MONTHLY"
            CycleUnit.YEAR -> "YEARLY"
        }
        val base = "FREQ=$freq"
        return if (cycle.count > 1) "$base;INTERVAL=${cycle.count}" else base
    }

    private suspend fun persistEventId(sub: Subscription, eventId: Long?) {
        val fresh = subscriptionDao.get(sub.id) ?: return
        subscriptionDao.upsert(fresh.copy(calendarEventId = eventId, updatedAt = fresh.updatedAt))
    }

    private fun removeEvent(eventId: Long) {
        runCatching {
            context.contentResolver.delete(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId), null, null)
        }
    }

    /** Removes SubMark-calendar events that belong to no subscription any more (deletes). */
    private suspend fun removeOrphans(current: List<Subscription>) {
        if (!hasPermission()) return
        val keep = current.mapNotNull { it.calendarEventId }.toSet()
        val calendarId = ensureCalendar()
        context.contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            arrayOf(CalendarContract.Events._ID),
            "${CalendarContract.Events.CALENDAR_ID} = ?",
            arrayOf(calendarId.toString()),
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                if (id !in keep) removeEvent(id)
            }
        }
    }

    /** Id of the app-owned local calendar, creating it when missing. */
    @Suppress("SameParameterValue")
    private fun ensureCalendar(): Long {
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            arrayOf(CalendarContract.Calendars._ID),
            "${CalendarContract.Calendars.ACCOUNT_NAME} = ? AND ${CalendarContract.Calendars.ACCOUNT_TYPE} = ?",
            arrayOf(ACCOUNT_NAME, CalendarContract.ACCOUNT_TYPE_LOCAL),
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getLong(0)
        }
        val values = ContentValues().apply {
            put(CalendarContract.Calendars.ACCOUNT_NAME, ACCOUNT_NAME)
            put(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            put(CalendarContract.Calendars.NAME, "SubMark")
            put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, CALENDAR_DISPLAY)
            put(CalendarContract.Calendars.CALENDAR_COLOR, 0xFF3F51B5.toInt())
            put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_OWNER)
            put(CalendarContract.Calendars.OWNER_ACCOUNT, ACCOUNT_NAME)
            put(CalendarContract.Calendars.SYNC_EVENTS, 0)
            put(CalendarContract.Calendars.VISIBLE, 1)
        }
        val insertUri = CalendarContract.Calendars.CONTENT_URI.buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, ACCOUNT_NAME)
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            .build()
        val uri = context.contentResolver.insert(insertUri, values)
            ?: error("could not create SubMark calendar")
        return uri.lastPathSegment!!.toLong()
    }

    companion object {
        const val ACCOUNT_NAME = "submark_local"
        const val CALENDAR_DISPLAY = "SubMark"
    }
}
