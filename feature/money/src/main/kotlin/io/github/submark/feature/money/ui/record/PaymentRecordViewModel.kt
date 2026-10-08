package io.github.submark.feature.money.ui.record

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.submark.core.data.repository.SubscriptionRepository
import io.github.submark.core.data.result.DataError
import io.github.submark.core.data.result.DataResult
import io.github.submark.core.data.service.PaymentService
import io.github.submark.core.data.service.WalletService
import io.github.submark.core.model.PaymentRecord
import io.github.submark.core.model.Subscription
import io.github.submark.core.model.Wallet
import io.github.submark.core.model.WalletTransaction
import io.github.submark.core.ui.format.UiText
import io.github.submark.core.ui.navigation.PaymentRecordRoute
import io.github.submark.feature.money.R
import io.github.submark.feature.money.data.MoneyEnv
import io.github.submark.feature.money.data.MoneyEnvSource
import io.github.submark.feature.money.ui.common.toUiText
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

data class PaymentRecordUiState(
    val loading: Boolean = true,
    val record: PaymentRecord? = null,
    val subscription: Subscription? = null,
    val wallet: Wallet? = null,
    val transaction: WalletTransaction? = null,
    val readOnly: Boolean = false,
    val showConverted: Boolean = false,
    /** [PaymentRecord.amount] in the default currency, null when no rate. */
    val convertedAmount: BigDecimal? = null,
    val deleting: Boolean = false,
    val error: UiText? = null,
    val env: MoneyEnv = MoneyEnv.EMPTY,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PaymentRecordViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val payments: PaymentService,
    subscriptions: SubscriptionRepository,
    wallets: WalletService,
    envSource: MoneyEnvSource,
) : ViewModel() {
    private val id = savedStateHandle.toRoute<PaymentRecordRoute>().id
    private val local = MutableStateFlow(Local())
    private val deletedChannel = Channel<Unit>(Channel.BUFFERED)

    /** Emits once the record was deleted; the screen then closes. */
    val deleted: Flow<Unit> = deletedChannel.receiveAsFlow()

    private data class Local(val showConverted: Boolean = false, val deleting: Boolean = false, val error: UiText? = null)

    private val recordFlow = payments.observe(id)

    private val subscriptionFlow: Flow<Subscription?> = recordFlow.flatMapLatest { r ->
        if (r == null) flowOf(null) else subscriptions.observe(r.subscriptionId)
    }

    private val transactionFlow: Flow<WalletTransaction?> = recordFlow.flatMapLatest { r ->
        val txnId = r?.walletTransactionId ?: return@flatMapLatest flowOf(null)
        wallets.observeAllTransactions().map { list -> list.firstOrNull { it.id == txnId } }
    }

    val uiState: StateFlow<PaymentRecordUiState> = combine(
        combine(recordFlow, subscriptionFlow, transactionFlow, ::Triple),
        wallets.observeAllWallets(),
        envSource.observe(),
        local,
    ) { (record, sub, txn), allWallets, env, l ->
        PaymentRecordUiState(
            loading = false,
            record = record,
            subscription = sub,
            wallet = record?.walletId?.let { wid -> allWallets.firstOrNull { it.id == wid } },
            transaction = txn,
            readOnly = record != null && payments.isReadOnly(record),
            showConverted = l.showConverted,
            convertedAmount = record?.let { env.toDefault(it.amount, it.currencyCode) },
            deleting = l.deleting,
            error = l.error,
            env = env,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PaymentRecordUiState())

    fun toggleConverted() = local.update { it.copy(showConverted = !it.showConverted) }

    fun dismissError() = local.update { it.copy(error = null) }

    fun delete() {
        val record = uiState.value.record ?: return
        if (local.value.deleting) return
        local.update { it.copy(deleting = true) }
        viewModelScope.launch {
            when (val result = payments.delete(record.id)) {
                is DataResult.Success -> {
                    local.update { it.copy(deleting = false) }
                    deletedChannel.send(Unit)
                }
                is DataResult.Failure -> local.update { it.copy(deleting = false, error = deleteErrorText(record, result.error)) }
            }
        }
    }

    companion object {
        /** A failure on a wallet-linked record means its reversal could not be posted; say so explicitly. */
        fun deleteErrorText(record: PaymentRecord, error: DataError): UiText =
            if (record.walletTransactionId != null && error !is DataError.NotFound && error !is DataError.Stale && error !is DataError.Invalid) {
                UiText.res(R.string.money_record_delete_wallet_failed, error.toUiText())
            } else {
                error.toUiText()
            }
    }
}
