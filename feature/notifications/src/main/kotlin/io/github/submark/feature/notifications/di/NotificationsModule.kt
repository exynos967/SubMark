package io.github.submark.feature.notifications.data

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import io.github.submark.core.data.change.AppStartListener
import io.github.submark.core.data.change.SubscriptionChangeListener
import javax.inject.Inject

/** Rebuilds reminders + calendar events after subscription mutations. */
class NotificationsChangeListener @Inject constructor(
    private val rebuild: ReminderRebuildService,
    private val calendarSync: CalendarSyncManager,
) : SubscriptionChangeListener {
    override suspend fun onSubscriptionsChanged(ids: Set<String>?) {
        rebuild.rebuildAll()
        calendarSync.sync(ids)
    }
}

/** On every launch: rebuild everything and make sure the daily refresh worker exists. */
class NotificationsAppStartListener @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val rebuild: ReminderRebuildService,
    private val calendarSync: CalendarSyncManager,
) : AppStartListener {
    override suspend fun onAppStart() {
        NotificationChannels.create(context)
        rebuild.rebuildAll()
        calendarSync.syncAll()
        NotificationRefreshWorker.schedule(context)
    }
}

@Module
@InstallIn(SingletonComponent::class)
object NotificationsModule {
    @Provides
    @IntoSet
    fun provideChangeListener(listener: NotificationsChangeListener): SubscriptionChangeListener = listener

    @Provides
    @IntoSet
    fun provideAppStartListener(listener: NotificationsAppStartListener): AppStartListener = listener
}
