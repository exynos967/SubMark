package io.github.submark.startup

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.submark.core.data.change.AppStartListener
import io.github.submark.core.data.currency.CurrencyRepository
import io.github.submark.core.data.currency.RateRefreshWorker
import io.github.submark.core.data.seed.DataSeeder
import io.github.submark.core.data.service.ProcessDueSummary
import io.github.submark.core.data.service.SubscriptionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Launch sequence: seed presets → process due renewals/expiry → schedule rate refresh → feature start hooks.
 * The due summary is kept for the UI to surface as one-time alerts.
 */
@Singleton
class AppStartup @Inject constructor(
    @ApplicationContext private val context: Context,
    private val seeder: DataSeeder,
    private val subscriptionService: SubscriptionService,
    private val currencyRepository: CurrencyRepository,
    private val startListeners: Set<@JvmSuppressWildcards AppStartListener>,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _ready = MutableStateFlow(false)
    private val _summary = MutableStateFlow<ProcessDueSummary?>(null)

    /** True once seeding finished; the UI waits for it so pickers never see an empty catalogue. */
    val ready: StateFlow<Boolean> = _ready.asStateFlow()
    val dueSummary: StateFlow<ProcessDueSummary?> = _summary.asStateFlow()

    fun start() {
        scope.launch {
            runStep("seed") { seeder.seed() }
            _ready.value = true
            runStep("processDue") { _summary.value = subscriptionService.processDue() }
            RateRefreshWorker.schedule(context)
            startListeners.forEach { listener -> launch { runStep(listener::class.java.simpleName) { listener.onAppStart() } } }
            runStep("refreshRates") { currencyRepository.refreshRates() }
        }
    }

    /** Re-run due processing when the app returns to the foreground on a new day. */
    fun refreshDue() {
        scope.launch { runStep("processDue") { _summary.value = subscriptionService.processDue() } }
    }

    fun consumeSummary() {
        _summary.value = null
    }

    private suspend fun runStep(name: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "startup step $name failed", e)
        }
    }

    private companion object {
        const val TAG = "AppStartup"
    }
}
