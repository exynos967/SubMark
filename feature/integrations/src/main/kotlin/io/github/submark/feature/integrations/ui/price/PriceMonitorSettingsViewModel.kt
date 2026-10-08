package io.github.submark.feature.integrations.ui.price

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.submark.core.data.settings.SettingsRepository
import io.github.submark.core.database.dao.PriceMonitorDao
import io.github.submark.core.ui.format.UiText
import io.github.submark.core.ui.util.SnackbarMessage
import io.github.submark.feature.integrations.R
import io.github.submark.feature.integrations.data.price.PriceCheckWorker
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PriceMonitorSettingsUiState(
    val enabled: Boolean = false,
    val intervalHours: Int = 6,
    val thresholdPercent: Int = 10,
    val recordCount: Int = 0,
    val notificationsAllowed: Boolean = true,
    val storeRegion: String = "US",
    val confirmClear: Boolean = false,
) {
    companion object {
        val INTERVAL_OPTIONS = listOf(6, 12, 24)
    }
}

@HiltViewModel
class PriceMonitorSettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val monitorDao: PriceMonitorDao,
) : ViewModel() {

    private val confirmClear = kotlinx.coroutines.flow.MutableStateFlow(false)
    private val notificationsAllowed = kotlinx.coroutines.flow.MutableStateFlow(true)

    init {
        refreshNotificationState()
    }

    val state: StateFlow<PriceMonitorSettingsUiState> = combine(
        settings.settings,
        monitorDao.observeRecordCount(),
        combine(confirmClear, notificationsAllowed) { c, n -> c to n },
    ) { s, count, extra ->
        PriceMonitorSettingsUiState(
            enabled = s.integrations.priceMonitorEnabled,
            intervalHours = s.integrations.priceMonitorIntervalHours,
            thresholdPercent = s.integrations.priceDropThresholdPercent,
            recordCount = count,
            confirmClear = extra.first,
            notificationsAllowed = extra.second,
            storeRegion = s.integrations.storeRegion?.uppercase()
                ?: java.util.Locale.getDefault().country.uppercase().ifBlank { "US" },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PriceMonitorSettingsUiState())

    private val messagesCh = Channel<SnackbarMessage>(Channel.BUFFERED)
    val messages = messagesCh.receiveAsFlow()

    fun refreshNotificationState() {
        notificationsAllowed.value = PriceCheckWorker.hasNotificationPermission(context)
    }

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settings.update {
                it.copy(integrations = it.integrations.copy(priceMonitorEnabled = enabled))
            }
            reschedule()
        }
    }

    fun setInterval(hours: Int) {
        viewModelScope.launch {
            settings.update { it.copy(integrations = it.integrations.copy(priceMonitorIntervalHours = hours)) }
            reschedule()
        }
    }

    fun setThreshold(percent: Int) {
        viewModelScope.launch {
            settings.update {
                it.copy(integrations = it.integrations.copy(priceDropThresholdPercent = percent.coerceIn(1, 100)))
            }
        }
    }

    fun setStoreRegion(code: String) {
        viewModelScope.launch {
            settings.update { it.copy(integrations = it.integrations.copy(storeRegion = code.lowercase())) }
        }
    }

    private suspend fun reschedule() {
        val config = settings.settings.first()
        PriceCheckWorker.schedule(context, config.integrations.priceMonitorEnabled, config.integrations.priceMonitorIntervalHours)
    }

    fun runNow() {
        PriceCheckWorker.schedule(context, true, state.value.intervalHours)
        androidx.work.OneTimeWorkRequestBuilder<PriceCheckWorker>()
            .setConstraints(
                androidx.work.Constraints.Builder()
                    .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                    .build(),
            )
            .build()
            .also { androidx.work.WorkManager.getInstance(context).enqueue(it) }
        viewModelScope.launch {
            messagesCh.send(SnackbarMessage(UiText.res(R.string.integrations_price_run_scheduled)))
        }
    }

    fun sendTestNotification() {
        PriceCheckWorker.notifyDrop(
            context,
            appName = "Example App",
            region = state.value.storeRegion,
            dropPercent = state.value.thresholdPercent.toDouble(),
            price = java.math.BigDecimal("4.99"),
            currency = "USD",
            was = java.math.BigDecimal("9.99"),
        )
        if (!PriceCheckWorker.hasNotificationPermission(context)) {
            refreshNotificationState()
            viewModelScope.launch {
                messagesCh.send(SnackbarMessage(UiText.res(R.string.integrations_price_notifications_denied)))
            }
        }
    }

    fun askClear(show: Boolean) = confirmClear.tryEmit(show)

    fun clearHistory() {
        confirmClear.tryEmit(false)
        viewModelScope.launch {
            monitorDao.clearRecords()
            messagesCh.send(SnackbarMessage(UiText.res(R.string.integrations_price_history_cleared)))
        }
    }
}
