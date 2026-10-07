package io.github.submark.core.data.change

/**
 * Contributed with `@IntoSet` by features that need work at every app launch (reschedule reminders,
 * run due backups, ...). The app calls these after seeding and `SubscriptionService.processDue()`.
 */
interface AppStartListener {
    suspend fun onAppStart()
}
