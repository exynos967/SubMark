package io.github.submark.feature.money.ui.wallet

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.submark.core.data.result.DataResult
import io.github.submark.core.data.service.WalletService
import io.github.submark.core.data.time.TimeProvider
import io.github.submark.core.model.Wallet
import io.github.submark.core.model.WalletTransaction
import io.github.submark.core.model.WalletTxnStatus
import io.github.submark.core.ui.format.UiText
import io.github.submark.core.ui.navigation.WalletActivityRoute
import io.github.submark.core.ui.util.SnackbarMessage
import io.github.submark.feature.money.R
import io.github.submark.feature.money.data.MoneyEnv
import io.github.submark.feature.money.data.MoneyEnvSource
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
import java.time.ZoneId
import javax.inject.Inject

data class WalletActivityUiState(
    val loading: Boolean = true,
    /** Null = all wallets. */
    val wallet: Wallet? = null,
    val allWallets: Boolean = true,
    /** id -> name; deleted wallets resolved to "(deleted)" in the UI. */
    val walletNames: Map<String, String> = emptyMap(),
    val deletedWalletIds: Set<String> = emptySet(),
    val walletCurrencies: Map<String, String> = emptyMap(),
    val query: String = "",
    val transactions: List<WalletTransaction> = emptyList(),
    val totalCount: Int = 0,
    val detail: WalletTransaction? = null,
    val busy: Boolean = false,
    val today: java.time.LocalDate = java.time.LocalDate.MIN,
    val zone: ZoneId = ZoneId.systemDefault(),
    val env: MoneyEnv = MoneyEnv.EMPTY,
)

@HiltViewModel
class WalletActivityViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val wallets: WalletService,
    private val time: TimeProvider,
    envSource: MoneyEnvSource,
) : ViewModel() {
    val walletId: String? = savedStateHandle.toRoute<WalletActivityRoute>().walletId
    private val query = MutableStateFlow("")
    private val detail = MutableStateFlow<WalletTransaction?>(null)
    private val busy = MutableStateFlow(false)
    private val messages = Channel<SnackbarMessage>(Channel.BUFFERED)
    val snackbar: Flow<SnackbarMessage> = messages.receiveAsFlow()

    val uiState: StateFlow<WalletActivityUiState> = combine(
        combine(wallets.observeAllWallets(), if (walletId == null) wallets.observeAllTransactions() else wallets.observeTransactions(walletId), ::Pair),
        combine(combine(query, detail, ::Pair), busy, ::Pair),
        envSource.observe(),
    ) { (allWallets, txns), (pd, b), env ->
        val q = pd.first
        val d = pd.second
        val visible = txns.sortedByDescending { it.occurredAt }.filter { matches(it, q) }
        WalletActivityUiState(
            loading = false,
            wallet = walletId?.let { id -> allWallets.firstOrNull { it.id == id } },
            allWallets = walletId == null,
            walletNames = allWallets.associate { it.id to it.name },
            deletedWalletIds = allWallets.filter { it.deletedAt != null }.map { it.id }.toSet(),
            walletCurrencies = allWallets.associate { it.id to it.currencyCode },
            query = q,
            transactions = visible,
            totalCount = txns.size,
            detail = d,
            busy = b,
            today = time.today(),
            zone = time.zone(),
            env = env,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WalletActivityUiState())

    fun setQuery(value: String) { query.value = value }

    fun openDetail(txn: WalletTransaction) { detail.value = txn }

    fun closeDetail() { detail.value = null }

    fun reverse(txn: WalletTransaction, note: String) {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            when (val result = wallets.reverseTransaction(txn.id, note.ifBlank { null })) {
                is DataResult.Success -> {
                    detail.value = null
                    messages.send(SnackbarMessage(UiText.res(R.string.money_wallet_txn_reversed)))
                }
                is DataResult.Failure -> messages.send(SnackbarMessage(result.error.toUiText()))
            }
            busy.value = false
        }
    }

    private fun matches(txn: WalletTransaction, q: String): Boolean {
        val t = q.trim()
        if (t.isEmpty()) return true
        return txn.note?.contains(t, ignoreCase = true) == true ||
            txn.amount.toPlainString().contains(t.replace(',', '.')) ||
            txn.occurredAt.atZone(time.zone()).toLocalDate().toString().contains(t)
    }
}
