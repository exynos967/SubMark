package io.github.submark.feature.money.ui.historical

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cached
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.submark.core.data.currency.CurrencyRepository
import io.github.submark.core.data.currency.HistoricalRateStats
import io.github.submark.core.data.result.DataResult
import io.github.submark.core.ui.component.ConfirmDialog
import io.github.submark.core.ui.component.LoadingState
import io.github.submark.core.ui.component.SectionCard
import io.github.submark.core.ui.component.SubMarkTopAppBar
import io.github.submark.core.ui.format.UiText
import io.github.submark.core.ui.util.SnackbarEffect
import io.github.submark.core.ui.util.SnackbarMessage
import io.github.submark.feature.money.R
import io.github.submark.feature.money.ui.common.StatCell
import io.github.submark.feature.money.ui.common.formatDate
import io.github.submark.feature.money.ui.common.toUiText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HistoricalRatesUiState(
    val loading: Boolean = true,
    val stats: HistoricalRateStats = HistoricalRateStats(0, null, null),
    val preloading: Boolean = false,
    val preloadDone: Int = 0,
    val preloadTotal: Int = CurrencyRepository.PRELOAD_DAYS,
    val clearing: Boolean = false,
) {
    val progress: Float get() = if (preloadTotal <= 0) 0f else preloadDone.toFloat() / preloadTotal
}

@HiltViewModel
class HistoricalRatesViewModel @Inject constructor(private val currencies: CurrencyRepository) : ViewModel() {
    private val work = MutableStateFlow(Work())
    private val messages = Channel<SnackbarMessage>(Channel.BUFFERED)
    val snackbar: Flow<SnackbarMessage> = messages.receiveAsFlow()

    private data class Work(val preloadDone: Int = 0, val preloadTotal: Int = 0, val working: Boolean = false)

    val uiState: StateFlow<HistoricalRatesUiState> = combine(currencies.observeHistoricalStats(), work) { stats, w ->
        HistoricalRatesUiState(
            loading = false,
            stats = stats,
            preloading = w.working && w.preloadTotal > 0,
            preloadDone = w.preloadDone,
            preloadTotal = if (w.preloadTotal > 0) w.preloadTotal else CurrencyRepository.PRELOAD_DAYS,
            clearing = w.working && w.preloadTotal == 0,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoricalRatesUiState())

    fun preload() {
        if (work.value.working) return
        work.value = Work(preloadDone = 0, preloadTotal = CurrencyRepository.PRELOAD_DAYS, working = true)
        viewModelScope.launch {
            val result = currencies.preloadRecent(onProgress = { done, total -> work.value = Work(done, total, working = true) })
            when (result) {
                is DataResult.Success -> messages.send(SnackbarMessage(UiText.plural(R.plurals.money_historical_preloaded, result.value, result.value)))
                is DataResult.Failure -> messages.send(SnackbarMessage(result.error.toUiText()))
            }
            work.value = Work()
        }
    }

    fun clear() {
        if (work.value.working) return
        work.value = Work(working = true)
        viewModelScope.launch {
            currencies.clearHistorical()
            messages.send(SnackbarMessage(UiText.res(R.string.money_historical_cleared)))
            work.value = Work()
        }
    }
}

@Composable
fun HistoricalRatesRoute(onBack: () -> Unit, viewModel: HistoricalRatesViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val host = remember { SnackbarHostState() }
    SnackbarEffect(viewModel.snackbar, host)
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        topBar = { SubMarkTopAppBar(title = stringResource(R.string.money_historical_title), onBack = onBack) },
        snackbarHost = { SnackbarHost(host) },
    ) { padding ->
        if (state.loading) {
            LoadingState(Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "about") {
                SectionCard(title = stringResource(R.string.money_historical_about)) {
                    AboutLine(Icons.Rounded.Speed, stringResource(R.string.money_historical_about_accurate))
                    AboutLine(Icons.Rounded.Cached, stringResource(R.string.money_historical_about_cache))
                    AboutLine(Icons.Rounded.DeleteSweep, stringResource(R.string.money_historical_about_cleanup))
                }
            }
            item(key = "stats") {
                SectionCard(title = stringResource(R.string.money_historical_stats)) {
                    Row {
                        StatCell(
                            stringResource(R.string.money_historical_records),
                            state.stats.count.toString(),
                            Modifier.weight(1f),
                        )
                        StatCell(
                            stringResource(R.string.money_historical_oldest),
                            state.stats.oldest?.let { formatDate(it) } ?: "—",
                            Modifier.weight(1f),
                        )
                        StatCell(
                            stringResource(R.string.money_historical_newest),
                            state.stats.newest?.let { formatDate(it) } ?: "—",
                            Modifier.weight(1f),
                        )
                    }
                    val freq = state.stats.count / CurrencyRepository.PRELOAD_DAYS.coerceAtLeast(1)
                    Text(
                        if (state.stats.count == 0) stringResource(R.string.money_historical_stats_empty) else stringResource(R.string.money_historical_stats_currencies, freq.coerceAtLeast(1)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item(key = "preload") {
                SectionCard(title = stringResource(R.string.money_historical_preload)) {
                    Text(stringResource(R.string.money_historical_preload_desc, CurrencyRepository.PRELOAD_DAYS), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (state.preloading) {
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                        Text(
                            stringResource(R.string.money_historical_preload_progress, state.preloadDone, state.preloadTotal),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    } else {
                        Spacer(Modifier.height(8.dp))
                        FilledTonalButton(onClick = viewModel::preload) {
                            Icon(Icons.Rounded.CloudDownload, contentDescription = null)
                            Spacer(Modifier.padding(4.dp))
                            Text(stringResource(R.string.money_historical_preload_button))
                        }
                    }
                }
            }
            item(key = "clear") {
                SectionCard(title = stringResource(R.string.money_historical_clear)) {
                    Text(stringResource(R.string.money_historical_clear_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { confirmClear = true },
                        enabled = !state.preloading && !state.clearing,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text(stringResource(R.string.money_historical_clear_button)) }
                }
            }
        }
    }
    if (confirmClear) {
        ConfirmDialog(
            title = stringResource(R.string.money_historical_clear_title),
            message = stringResource(R.string.money_historical_clear_confirm, state.stats.count),
            onConfirm = { confirmClear = false; viewModel.clear() },
            onDismiss = { confirmClear = false },
            confirmLabel = stringResource(io.github.submark.core.ui.R.string.ui_action_delete),
            destructive = true,
        )
    }
}

@Composable
private fun AboutLine(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.padding(6.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
