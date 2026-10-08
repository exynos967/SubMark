package io.github.submark.feature.money.ui.wallet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.submark.core.data.repository.SubscriptionRepository
import io.github.submark.core.data.result.DataError
import io.github.submark.core.data.result.DataResult
import io.github.submark.core.data.service.TrackedAssets
import io.github.submark.core.data.service.WalletService
import io.github.submark.core.data.time.TimeProvider
import io.github.submark.core.model.Wallet
import io.github.submark.core.model.WalletKind
import io.github.submark.core.model.WalletTxnType
import io.github.submark.core.ui.format.MoneyInput
import io.github.submark.core.ui.format.UiText
import io.github.submark.core.ui.util.SnackbarMessage
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

data class WalletAmountState(
    val wallet: Wallet,
    /** TOP_UP = add money, EXPENSE = deduct. */
    val type: WalletTxnType,
    val amountText: String = "",
    val note: String = "",
    val saving: Boolean = false,
    val error: UiText? = null,
) {
    val amount: BigDecimal? get() = MoneyInput.parse(amountText)
}

data class WalletFormState(
    val id: String? = null,
    val name: String = "",
    val kind: WalletKind = WalletKind.BALANCE_TRACKED,
    val currencyCode: String = "",
    /** Currency cannot change once transactions exist. */
    val currencyLocked: Boolean = false,
    val initialText: String = "",
    val creditText: String = "",
    val colorHex: String? = null,
    val iconValue: String? = null,
    val active: Boolean = true,
    /** Subscriptions linked to this wallet shown on edit. */
    val linkedCount: Int = 0,
    val saving: Boolean = false,
    val error: UiText? = null,
) {
    val initial: BigDecimal? get() = if (initialText.isBlank()) BigDecimal.ZERO else MoneyInput.parse(initialText)
    val creditLimit: BigDecimal? get() = creditText.ifBlank { null }?.let(MoneyInput::parse)
}

data class WalletManagementUiState(
    val loading: Boolean = true,
    val activeWallets: List<Wallet> = emptyList(),
    val inactiveWallets: List<Wallet> = emptyList(),
    val transactionCounts: Map<String, Int> = emptyMap(),
    val assets: TrackedAssets? = null,
    val amountSheet: WalletAmountState? = null,
    val form: WalletFormState? = null,
    val env: MoneyEnv = MoneyEnv.EMPTY,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WalletManagementViewModel @Inject constructor(
    private val wallets: WalletService,
    private val subscriptions: SubscriptionRepository,
    envSource: MoneyEnvSource,
    private val time: TimeProvider,
) : ViewModel() {
    private val amountSheet = MutableStateFlow<WalletAmountState?>(null)
    private val form = MutableStateFlow<WalletFormState?>(null)
    private val messages = Channel<SnackbarMessage>(Channel.BUFFERED)
    val snackbar: Flow<SnackbarMessage> = messages.receiveAsFlow()

    private val envFlow = envSource.observe()

    private val assets = envFlow.map { it.defaultCode }.distinctUntilChanged()
        .flatMapLatest { code -> wallets.observeTrackedAssets(code) }

    private val counts = wallets.observeAllTransactions()
        .map { txns -> txns.groupingBy { it.walletId }.eachCount() }

    val uiState: StateFlow<WalletManagementUiState> = combine(
        combine(wallets.observeWallets(), assets, envFlow, ::Triple),
        combine(counts, amountSheet, form, ::Triple),
    ) { a, b ->
        val visible = a.first
        val assets = a.second
        val env = a.third
        val counts = b.first
        val amount = b.second
        val f = b.third
        WalletManagementUiState(
            loading = false,
            activeWallets = visible.filter { it.isActive },
            inactiveWallets = visible.filter { !it.isActive },
            transactionCounts = counts,
            assets = assets,
            amountSheet = amount,
            form = f,
            env = env,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WalletManagementUiState())

    fun openTopUp(wallet: Wallet) = amountSheet.update { WalletAmountState(wallet, WalletTxnType.TOP_UP) }

    fun openDeduct(wallet: Wallet) = amountSheet.update { WalletAmountState(wallet, WalletTxnType.EXPENSE) }

    fun closeAmountSheet() = amountSheet.update { null }

    fun updateAmountSheet(transform: (WalletAmountState) -> WalletAmountState) = amountSheet.update { it?.let(transform)?.copy(error = null) }

    fun submitAmountSheet() {
        val sheet = amountSheet.value ?: return
        val amount = sheet.amount
        if (sheet.saving) return
        when {
            amount == null || amount.signum() <= 0 -> amountSheet.update { it?.copy(error = UiText.res(R.string.money_invalid_non_positive_amount)) }
            amountProblem(sheet.wallet, amount) != null -> amountSheet.update { it?.copy(error = amountProblem(sheet.wallet, amount)!!.toUiText()) }
            else -> {
                amountSheet.update { it?.copy(saving = true) }
                viewModelScope.launch {
                    val note = sheet.note.trim().takeIf { it.isNotEmpty() }
                    val result = if (sheet.type == WalletTxnType.TOP_UP) wallets.topUp(sheet.wallet.id, amount, note) else wallets.deduct(sheet.wallet.id, amount, note)
                    when (result) {
                        is DataResult.Success -> amountSheet.value = null
                        is DataResult.Failure -> amountSheet.update { it?.copy(saving = false, error = result.error.toUiText()) }
                    }
                }
            }
        }
    }

    fun openCreate() = form.update { WalletFormState(currencyCode = uiState.value.env.defaultCode) }

    fun openEdit(wallet: Wallet) {
        form.value = WalletFormState()
        viewModelScope.launch {
            val locked = wallets.isCurrencyLocked(wallet.id)
            val linked = linkedCount(wallet.id)
            form.value = WalletFormState(
                id = wallet.id,
                name = wallet.name,
                kind = wallet.kind,
                currencyCode = wallet.currencyCode,
                currencyLocked = locked,
                creditText = wallet.creditLimit?.stripTrailingZeros()?.toPlainString().orEmpty(),
                colorHex = wallet.colorHex,
                iconValue = wallet.iconValue,
                active = wallet.isActive,
                linkedCount = linked,
            )
        }
    }

    fun closeForm() = form.update { null }

    fun updateForm(transform: (WalletFormState) -> WalletFormState) = form.update { it?.let(transform)?.copy(error = null) }

    fun saveForm() {
        val f = form.value ?: return
        if (f.saving) return
        val error = when {
            f.name.isBlank() -> UiText.res(R.string.money_invalid_blank_name)
            f.id == null && f.initial == null -> UiText.res(R.string.money_invalid_field_value)
            f.id == null && f.kind == WalletKind.BALANCE_TRACKED && (f.initial?.signum() ?: 0) < 0 -> UiText.res(R.string.money_invalid_negative_amount)
            f.kind == WalletKind.CREDIT && f.creditText.isNotBlank() && (f.creditLimit == null || f.creditLimit!!.signum() < 0) -> UiText.res(R.string.money_invalid_credit_limit)
            else -> null
        }
        if (error != null) return form.update { it?.copy(error = error) }
        form.update { it?.copy(saving = true) }
        viewModelScope.launch {
            val result = if (f.id == null) {
                wallets.create(
                    name = f.name,
                    kind = f.kind,
                    currencyCode = f.currencyCode,
                    initialBalance = f.initial ?: BigDecimal.ZERO,
                    creditLimit = f.creditLimit.takeIf { f.kind == WalletKind.CREDIT },
                    colorHex = f.colorHex,
                    iconValue = f.iconValue,
                )
            } else {
                val current = wallets.get(f.id)
                if (current == null) {
                    form.update { it?.copy(saving = false, error = DataError.NotFound.toUiText()) }
                    return@launch
                }
                wallets.update(
                    current.copy(
                        name = f.name,
                        kind = f.kind,
                        currencyCode = if (f.currencyLocked) current.currencyCode else f.currencyCode,
                        creditLimit = f.creditLimit.takeIf { f.kind == WalletKind.CREDIT },
                        isActive = f.active,
                        colorHex = f.colorHex,
                        iconValue = f.iconValue,
                    ),
                )
            }
            when (result) {
                is DataResult.Success -> {
                    form.value = null
                    messages.send(SnackbarMessage(UiText.res(R.string.money_wallet_saved)))
                }
                is DataResult.Failure -> form.update { it?.copy(saving = false, error = result.error.toUiText()) }
            }
        }
    }

    fun deactivate(wallet: Wallet) = viewModelScope.launch {
        val linked = linkedCount(wallet.id)
        val result = wallets.deactivate(wallet.id)
        when (result) {
            is DataResult.Success -> messages.send(
                SnackbarMessage(
                    if (linked > 0) UiText.plural(R.plurals.money_wallet_deactivated_unlinked, linked, linked)
                    else UiText.res(R.string.money_wallet_deactivated),
                ),
            )
            is DataResult.Failure -> messages.send(SnackbarMessage(result.error.toUiText()))
        }
        Unit
    }

    fun reactivate(wallet: Wallet) = mutate(UiText.res(R.string.money_wallet_reactivated)) { wallets.reactivate(wallet.id) }

    fun delete(wallet: Wallet) = mutate(UiText.res(R.string.money_wallet_deleted)) { wallets.delete(wallet.id) }

    private fun mutate(success: UiText? = null, block: suspend () -> DataResult<*>) {
        viewModelScope.launch {
            when (val result = block()) {
                is DataResult.Success -> success?.let { messages.send(SnackbarMessage(it)) }
                is DataResult.Failure -> messages.send(SnackbarMessage(result.error.toUiText()))
            }
        }
    }

    private suspend fun linkedCount(walletId: String): Int = subscriptions.getAll().count { it.walletId == walletId }

    companion object {
        /** Balance after a manual operation; negative means it exceeds the funds. */
        fun afterBalance(wallet: Wallet, type: WalletTxnType, amount: BigDecimal?): BigDecimal? = when {
            amount == null || amount.signum() <= 0 -> null
            type == WalletTxnType.TOP_UP -> wallet.balance + amount
            else -> wallet.balance - amount
        }

        /** Rule violation of a manual deduction, or null when allowed. Mirrors WalletLedger.checkFunds. */
        fun amountProblem(wallet: Wallet, amount: BigDecimal?): DataError? {
            if (amount == null) return null
            val after = wallet.balance - amount
            return when (wallet.kind) {
                WalletKind.BALANCE_TRACKED -> if (after.signum() < 0) DataError.InsufficientFunds(wallet.id, wallet.balance.max(BigDecimal.ZERO), amount) else null
                WalletKind.CREDIT -> wallet.creditLimit?.let {
                    if (after < -it) DataError.InsufficientFunds(wallet.id, (wallet.balance + it).max(BigDecimal.ZERO), amount) else null
                }
                WalletKind.SETTLEMENT_ONLY -> null
            }
        }
    }
}
