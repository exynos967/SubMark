package io.github.submark.core.data.change

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Contributed with `@IntoSet` by features that mirror subscription data elsewhere
 * (notifications, calendar sync, widgets). Called after every committed mutation.
 */
interface SubscriptionChangeListener {
    /** [ids] = affected subscriptions; null = anything may have changed (import, reset, bulk edits). */
    suspend fun onSubscriptionsChanged(ids: Set<String>?)
}

/** Fans a committed change out to every registered listener; one failing listener never blocks the others. */
@Singleton
class ChangeNotifier @Inject constructor(
    private val listeners: Set<@JvmSuppressWildcards SubscriptionChangeListener>,
) {
    suspend fun notifyChanged(ids: Set<String>?) {
        if (ids != null && ids.isEmpty()) return
        listeners.forEach { listener ->
            runCatching { listener.onSubscriptionsChanged(ids) }
                .onFailure { Log.w(TAG, "listener ${listener::class.java.simpleName} failed", it) }
        }
    }

    suspend fun notifyChanged(id: String) = notifyChanged(setOf(id))

    private companion object {
        const val TAG = "ChangeNotifier"
    }
}
