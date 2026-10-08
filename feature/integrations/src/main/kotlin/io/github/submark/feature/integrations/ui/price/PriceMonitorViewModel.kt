package io.github.submark.feature.integrations.ui.price

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.submark.core.data.settings.SettingsRepository
import io.github.submark.core.data.time.TimeProvider
import io.github.submark.core.database.dao.PriceMonitorDao
import io.github.submark.core.database.dao.SubscriptionDao
import io.github.submark.core.model.PriceCheckResult
import io.github.submark.core.model.PriceMonitor
import io.github.submark.core.model.PriceRecord
import io.github.submark.core.model.Subscription
import io.github.submark.core.model.SubscriptionKind
import io.github.submark.core.ui.format.UiText
import io.github.submark.core.ui.navigation.PriceMonitorRoute
import io.github.submark.core.ui.util.SnackbarMessage
import io.github.submark.feature.integrations.R
import io.github.submark.feature.integrations.data.CountryCatalog
import io.github.submark.feature.integrations.data.itunes.ItunesService
import io.github.submark.feature.integrations.data.price.PriceCheck
import io.github.submark.feature.integrations.ui.common.uiMessage
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PriceMonitorUiState(
    val loading: Boolean = true,
    val subscription: Subscription? = null,
    val monitor: PriceMonitor? = null,
    val records: List<PriceRecord> = emptyList(),
    val masterEnabled: Boolean = false,
    val checking: Boolean = false,
    val showRegionPicker: Boolean = false,
    val collapsedRegions: Set<String> = emptySet(),
) {
    /** Derived per-region statistics, newest-first records grouped by region. */
    val regionStats: List<PriceCheck.RegionStats>
        get() = records.groupBy { it.region }.mapNotNull { (region, recs) ->
            PriceCheck.stats(region, recs)
        }

    /** Wishlist subscription with an App Store id — the only eligible target. */
    val eligible: Boolean
        get() = subscription?.kind == SubscriptionKind.WISHLIST && !subscription?.appStoreId.isNullOrBlank()

    val needsAppStoreId: Boolean
        get() = subscription?.kind == SubscriptionKind.WISHLIST && subscription?.appStoreId.isNullOrBlank()
}

@HiltViewModel
class PriceMonitorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val subscriptionDao: SubscriptionDao,
    private val monitorDao: PriceMonitorDao,
    private val itunes: ItunesService,
    private val settings: SettingsRepository,
    private val time: TimeProvider,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<PriceMonitorRoute>()
    private val subscriptionId = route.subscriptionId

    private val checking = MutableStateFlow(false)
    private val showRegionPicker = MutableStateFlow(false)
    private val collapsed = MutableStateFlow<Set<String>>(emptySet())

    val state: StateFlow<PriceMonitorUiState> = combine(
        subscriptionDao.observe(subscriptionId),
        monitorDao.observe(subscriptionId),
        monitorDao.observeRecords(subscriptionId),
        settings.settings,
        combine(checking, showRegionPicker, collapsed) { c, p, col -> Triple(c, p, col) },
    ) { sub, monitor, records, s, extra ->
        PriceMonitorUiState(
            loading = false,
            subscription = sub,
            monitor = monitor,
            records = records,
            masterEnabled = s.integrations.priceMonitorEnabled,
            checking = extra.first,
            showRegionPicker = extra.second,
            collapsedRegions = extra.third,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PriceMonitorUiState(loading = true))

    private val messagesCh = Channel<SnackbarMessage>(Channel.BUFFERED)
    val messages = messagesCh.receiveAsFlow()

    fun setRegionPicker(show: Boolean) = showRegionPicker.tryEmit(show)

    fun toggleRegionCollapsed(region: String) {
        collapsed.value = collapsed.value.let { if (region in it) it - region else it + region }
    }

    /** Enables monitoring; seeds regions with the common storefront preset on first enable. */
    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val sub = subscriptionDao.get(subscriptionId) ?: return@launch
            val appStoreId = sub.appStoreId
            if (enabled && appStoreId.isNullOrBlank()) {
                messagesCh.send(SnackbarMessage(UiText.res(R.string.integrations_price_needs_app_store_id)))
                return@launch
            }
            val existing = monitorDao.observe(subscriptionId).first()
            val monitor = existing?.copy(enabled = enabled)
                ?: PriceMonitor(
                    subscriptionId = subscriptionId,
                    enabled = enabled,
                    appStoreId = appStoreId.orEmpty(),
                    regions = CountryCatalog.PRESELECTED,
                )
            monitorDao.upsert(monitor)
        }
    }

    fun addRegion(code: String) {
        viewModelScope.launch {
            val monitor = monitorDao.observe(subscriptionId).first() ?: return@launch
            if (monitor.regions.none { it.equals(code, true) }) {
                monitorDao.upsert(monitor.copy(regions = monitor.regions + code.uppercase()))
            }
        }
    }

    fun removeRegion(code: String) {
        viewModelScope.launch {
            val monitor = monitorDao.observe(subscriptionId).first() ?: return@launch
            if (monitor.regions.size <= 1) {
                messagesCh.send(SnackbarMessage(UiText.res(R.string.integrations_price_need_one_region)))
                return@launch
            }
            monitorDao.upsert(monitor.copy(regions = monitor.regions.filterNot { it.equals(code, true) }))
        }
    }

    /** Manual check over all configured regions; only complete store data is recorded. */
    fun checkNow() {
        if (checking.value) return
        viewModelScope.launch {
            checking.value = true
            val monitor = monitorDao.observe(subscriptionId).first()
            if (monitor == null || monitor.appStoreId.isBlank()) {
                checking.value = false
                messagesCh.send(SnackbarMessage(UiText.res(R.string.integrations_price_needs_app_store_id)))
                return@launch
            }
            var succeeded = 0
            var failed = 0
            for (regionRaw in monitor.regions) {
                val region = regionRaw.uppercase()
                itunes.lookupPrice(monitor.appStoreId, region).fold(
                    onSuccess = { price ->
                        succeeded++
                        monitorDao.upsertRecords(
                            listOf(
                                PriceRecord(
                                    subscriptionId = subscriptionId,
                                    region = region,
                                    price = price.price,
                                    currencyCode = price.currency,
                                    formattedPrice = price.formattedPrice,
                                    checkedAt = time.now(),
                                ),
                            ),
                        )
                    },
                    onFailure = { failed++ },
                )
            }
            monitorDao.upsert(
                monitor.copy(
                    lastCheckAt = time.now(),
                    lastCheckResult = when {
                        succeeded == 0 -> PriceCheckResult.FAILED
                        failed > 0 -> PriceCheckResult.PARTIAL
                        else -> PriceCheckResult.SUCCESS
                    },
                ),
            )
            checking.value = false
            messagesCh.send(
                SnackbarMessage(
                    if (failed == 0) {
                        UiText.res(R.string.integrations_price_check_done, succeeded)
                    } else {
                        UiText.res(R.string.integrations_price_check_partial, succeeded, failed)
                    },
                ),
            )
        }
    }
}
