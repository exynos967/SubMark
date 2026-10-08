package io.github.submark.feature.money.ui.storedvalue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.submark.core.data.repository.SubscriptionRepository
import io.github.submark.core.data.service.StoredValueService
import io.github.submark.core.data.time.TimeProvider
import io.github.submark.feature.money.data.MoneyEnv
import io.github.submark.feature.money.data.MoneyEnvSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class StoredValueStatsUiState(
    val loading: Boolean = true,
    val period: StoredValuePeriod = StoredValuePeriod.ALL_TIME,
    val stats: StoredValueStats? = null,
    val env: MoneyEnv = MoneyEnv.EMPTY,
)

@HiltViewModel
class StoredValueStatsViewModel @Inject constructor(
    subscriptions: SubscriptionRepository,
    storedValue: StoredValueService,
    envSource: MoneyEnvSource,
    time: TimeProvider,
) : ViewModel() {
    private val period = MutableStateFlow(StoredValuePeriod.ALL_TIME)

    val uiState: StateFlow<StoredValueStatsUiState> = combine(
        subscriptions.observeAll(),
        storedValue.observeAllRecords(),
        envSource.observe(),
        period,
    ) { subs, records, env, p ->
        StoredValueStatsUiState(
            loading = false,
            period = p,
            stats = StoredValueStatsCalculator.compute(subs, records, p, time.today(), time.zone(), env.converter, env.defaultCode),
            env = env,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StoredValueStatsUiState())

    fun setPeriod(value: StoredValuePeriod) { period.value = value }
}
