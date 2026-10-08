package io.github.submark.feature.notifications.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class ScheduledReminder(
    val requestCode: Int,
    val subscriptionId: String,
    val subscriptionName: String,
    val triggerAtMillis: Long,
    val kind: String,
    val cycleDueDate: String,
)

/**
 * What is currently scheduled with the AlarmManager. Persisted as JSON so a build can cancel
 * stale alarms (missing here) and legacy ids (anything from an older scheme).
 */
@Singleton
class ScheduledRemindersStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val Context.store by preferencesDataStore("notification_schedule")
    private val key = stringPreferencesKey("scheduled")
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun load(): List<ScheduledReminder> {
        val raw = context.store.data.first()[key] ?: return emptyList()
        return runCatching { json.decodeFromString<List<ScheduledReminder>>(raw) }.getOrDefault(emptyList())
    }

    suspend fun save(items: List<ScheduledReminder>) {
        val raw = json.encodeToString(kotlinx.serialization.builtins.ListSerializer(ScheduledReminder.serializer()), items)
        context.store.edit { it[key] = raw }
    }
}

fun ReminderTrigger.toScheduled(zone: java.time.ZoneId): ScheduledReminder = ScheduledReminder(
    requestCode = requestCode,
    subscriptionId = subscriptionId,
    subscriptionName = subscriptionName,
    triggerAtMillis = ReminderTriggers.triggerMillis(this, zone),
    kind = kind.name,
    cycleDueDate = cycleDueDate.toString(),
)

fun ScheduledReminder.triggerInstant(): Instant = Instant.ofEpochMilli(triggerAtMillis)
