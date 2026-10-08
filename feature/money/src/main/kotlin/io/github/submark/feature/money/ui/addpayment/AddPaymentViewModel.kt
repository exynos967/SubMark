package io.github.submark.feature.money.ui.addpayment

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.submark.core.data.currency.CurrencyRepository
import io.github.submark.core.data.repository.SubscriptionRepository
import io.github.submark.core.data.result.DataResult
import io.github.submark.core.data.service.AddPaymentOutcome
import io.github.submark.core.data.service.NewPayment
import io.github.submark.core.data.service.PaymentEdit
import io.github.submark.core.data.service.PaymentService
import io.github.submark.core.data.service.SharedService
import io.github.submark.core.data.service.SystemNotes
import io.github.submark.core.data.service.WalletService
import io.github.submark.core.data.time.TimeProvider
import io.github.submark.core.model.Currency
import io.github.submark.core.model.DateAdjustmentMode
import io.github.submark.core.model.PaymentKind
import io.github.submark.core.model.PaymentRecord
import io.github.submark.core.model.PaymentStatus
import io.github.submark.core.model.Subscription
import io.github.submark.core.model.SubscriptionKind
import io.github.submark.core.ui.format.MoneyInput
import io.github.submark.core.ui.format.UiText
import io.github.submark.core.ui.navigation.AddPaymentRoute
import io.github.submark.feature.money.R
import io.github.submark.feature.money.data.MoneyEnv
import io.github.submark.feature.money.data.MoneyEnvSource
import io.github.submark.feature.money.ui.common.WalletChoice
import io.github.submark.feature.money.ui.common.toUiText
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate
import javax.inject.Inject

/** Editable form values. Amount stays text so partial input survives. */
data class PaymentForm(
    val amountText: String = "",
    val currencyCode: String = "",
    val paymentDate: LocalDate = LocalDate.MIN,
    val status: PaymentStatus = PaymentStatus.SUCCESS,
    val note: String = "",
    val inAppPurchase: Boolean = false,
    val iapItemName: String = "",
    val adjustDates: Boolean = false,
    val adjustmentMode: DateAdjustmentMode = DateAdjustmentMode.END_DATE,
    val targetDate: LocalDate? = null,
    val syncPrice: Boolean = false,
    val payWithWallet: Boolean = false,
    val walletId: String? = null,
) {
    val amount: BigDecimal? get() = MoneyInput.parse(amountText)
}

data class AddPaymentUiState(
    val loading: Boolean = true,
    val missing: Boolean = false,
    val isEdit: Boolean = false,
    val subscription: Subscription? = null,
    /** User's share for shared subscriptions (the default amount). */
    val userShare: BigDecimal? = null,
    val editingRecord: PaymentRecord? = null,
    /** Edit mode on a record the service refuses to edit. */
    val readOnly: Boolean = false,
    /** The original note is a system note; it is kept and not editable. */
    val noteLocked: Boolean = false,
    val form: PaymentForm = PaymentForm(),
    val preview: PaymentPreview? = null,
    val walletChoices: List<WalletChoice> = emptyList(),
    val currencies: List<Currency> = emptyList(),
    val amountError: UiText? = null,
    val saving: Boolean = false,
    val error: UiText? = null,
    val outcome: AddPaymentOutcome? = null,
    val today: LocalDate = LocalDate.MIN,
    val env: MoneyEnv = MoneyEnv.EMPTY,
) {
    val canSave: Boolean get() = !loading && !missing && !readOnly && !saving && amountError == null && form.amount != null
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AddPaymentViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val payments: PaymentService,
    private val subscriptions: SubscriptionRepository,
    private val shared: SharedService,
    private val wallets: WalletService,
    currencies: CurrencyRepository,
    envSource: MoneyEnvSource,
    private val time: TimeProvider,
) : ViewModel() {
    private val route = savedStateHandle.toRoute<AddPaymentRoute>()

    private data class Base(
        val loading: Boolean = true,
        val missing: Boolean = false,
        val subscription: Subscription? = null,
        val userShare: BigDecimal? = null,
        val record: PaymentRecord? = null,
        val noteLocked: Boolean = false,
        val saving: Boolean = false,
        val error: UiText? = null,
        val outcome: AddPaymentOutcome? = null,
    )

    private val base = MutableStateFlow(Base())
    private val form = MutableStateFlow(PaymentForm(paymentDate = time.today()))
    private val closeChannel = Channel<Unit>(Channel.BUFFERED)

    /** Emits when the screen should close (edit saved, or result dialog acknowledged). */
    val close: Flow<Unit> = closeChannel.receiveAsFlow()

    private val walletChoices: Flow<List<WalletChoice>> = combine(
        wallets.observeWallets(),
        form.map { Triple(it.amount, it.currencyCode, it.status) }.distinctUntilChanged(),
    ) { list, key -> list.filter { it.isActive } to key }
        .mapLatest { (list, key) ->
            val (amount, code, _) = key
            list.map { w ->
                val problem = if (amount != null && amount.signum() > 0 && code.isNotEmpty()) wallets.chargeProblem(w.id, amount, code) else null
                WalletChoice(w, problem)
            }
        }

    val uiState: StateFlow<AddPaymentUiState> = combine(
        base,
        form,
        walletChoices,
        currencies.observeEnabledSorted(),
        envSource.observe(),
    ) { b, f, choices, enabled, env ->
        val sub = b.subscription
        val today = time.today()
        val isEdit = route.paymentId != null
        val preview = if (sub != null && !isEdit) {
            AddPaymentPreviewCalculator.preview(
                sub,
                PreviewInput(
                    amount = f.amount, currencyCode = f.currencyCode, paymentDate = f.paymentDate, status = f.status,
                    inAppPurchase = f.inAppPurchase,
                    adjustment = if (f.adjustDates) f.adjustmentMode else DateAdjustmentMode.NONE,
                    target = f.targetDate, syncPrice = f.syncPrice, payWithWallet = f.payWithWallet && f.walletId != null,
                ),
                today,
            )
        } else {
            null
        }
        val currencyList = if (enabled.none { it.code == f.currencyCode } && f.currencyCode.isNotEmpty()) {
            enabled + listOfNotNull(env.currencies[f.currencyCode])
        } else {
            enabled
        }
        AddPaymentUiState(
            loading = b.loading,
            missing = b.missing,
            isEdit = isEdit,
            subscription = sub,
            userShare = b.userShare,
            editingRecord = b.record,
            readOnly = b.record?.let { payments.isReadOnly(it) } ?: false,
            noteLocked = b.noteLocked,
            form = f,
            preview = preview,
            walletChoices = choices,
            currencies = currencyList,
            amountError = MoneyInput.validate(f.amountText, allowZero = true)?.let { UiText.res(it.messageRes) },
            saving = b.saving,
            error = b.error,
            outcome = b.outcome,
            today = today,
            env = env,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AddPaymentUiState())

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val sub = subscriptions.get(route.subscriptionId)
        if (sub == null) {
            base.value = Base(loading = false, missing = true)
            return
        }
        val share = if (sub.isShared) shared.userShare(sub.id) else null
        val paymentId = route.paymentId
        if (paymentId == null) {
            val amount = share ?: sub.price
            form.value = PaymentForm(
                amountText = amount.stripTrailingZeros().toPlainString(),
                currencyCode = sub.currencyCode,
                paymentDate = time.today(),
                payWithWallet = sub.walletId != null,
                walletId = sub.walletId,
            )
            base.value = Base(loading = false, subscription = sub, userShare = share)
        } else {
            val record = payments.get(paymentId)
            if (record == null) {
                base.value = Base(loading = false, missing = true)
                return
            }
            val locked = SystemNotes.isSystem(record.note)
            form.value = PaymentForm(
                amountText = record.amount.stripTrailingZeros().toPlainString(),
                currencyCode = record.currencyCode,
                paymentDate = record.paymentDate,
                status = record.status,
                note = if (locked) "" else record.note.orEmpty(),
                inAppPurchase = record.kind == PaymentKind.IN_APP_PURCHASE,
                iapItemName = record.iapItemName.orEmpty(),
            )
            base.value = Base(loading = false, subscription = sub, userShare = share, record = record, noteLocked = locked)
        }
    }

    fun update(transform: (PaymentForm) -> PaymentForm) = form.update(transform)

    fun setAdjustmentMode(mode: DateAdjustmentMode) = form.update { it.copy(adjustmentMode = mode, targetDate = null) }

    fun dismissError() = base.update { it.copy(error = null) }

    fun acknowledgeOutcome() {
        base.update { it.copy(outcome = null) }
        viewModelScope.launch { closeChannel.send(Unit) }
    }

    fun save() {
        val state = uiState.value
        if (!state.canSave) return
        val sub = state.subscription ?: return
        val f = state.form
        val amount = f.amount ?: return
        if (state.isEdit) return saveEdit(state, f, amount)

        val preview = state.preview
        if (sub.kind == SubscriptionKind.WISHLIST) return fail(UiText.res(R.string.money_invalid_wishlist))
        if (f.adjustDates && f.status == PaymentStatus.SUCCESS && preview?.targetProblem != null) {
            return fail(UiText.res(R.string.money_add_error_target))
        }
        val walletId = f.walletId.takeIf { f.payWithWallet && preview?.walletChargeApplies == true }
        if (f.payWithWallet && f.status == PaymentStatus.SUCCESS && amount.signum() > 0 && f.walletId == null) {
            return fail(UiText.res(R.string.money_add_error_wallet_required))
        }
        base.update { it.copy(saving = true) }
        viewModelScope.launch {
            val result = payments.add(
                NewPayment(
                    subscriptionId = sub.id,
                    amount = amount,
                    currencyCode = f.currencyCode,
                    paymentDate = f.paymentDate,
                    status = f.status,
                    inAppPurchase = f.inAppPurchase,
                    iapItemName = f.iapItemName.trim().takeIf { f.inAppPurchase && it.isNotEmpty() },
                    note = f.note.trim().takeIf { it.isNotEmpty() },
                    dateAdjustment = if (f.adjustDates) f.adjustmentMode else DateAdjustmentMode.NONE,
                    adjustmentTargetDate = f.targetDate.takeIf { f.adjustDates },
                    syncSubscriptionPrice = f.syncPrice,
                    walletId = walletId,
                ),
            )
            when (result) {
                is DataResult.Success -> base.update { it.copy(saving = false, outcome = result.value) }
                is DataResult.Failure -> base.update {
                    it.copy(saving = false, error = if (walletId != null) UiText.res(R.string.money_add_error_wallet_charge, result.error.toUiText()) else result.error.toUiText())
                }
            }
        }
    }

    private fun saveEdit(state: AddPaymentUiState, f: PaymentForm, amount: BigDecimal) {
        val record = state.editingRecord ?: return
        base.update { it.copy(saving = true) }
        viewModelScope.launch {
            val result = payments.edit(
                PaymentEdit(
                    id = record.id,
                    amount = amount,
                    currencyCode = f.currencyCode,
                    paymentDate = f.paymentDate,
                    status = f.status,
                    inAppPurchase = f.inAppPurchase,
                    iapItemName = f.iapItemName.trim().takeIf { f.inAppPurchase && it.isNotEmpty() },
                    note = if (state.noteLocked) record.note else f.note.trim().takeIf { it.isNotEmpty() },
                    expectedUpdatedAt = record.updatedAt,
                ),
            )
            when (result) {
                is DataResult.Success -> {
                    base.update { it.copy(saving = false) }
                    closeChannel.send(Unit)
                }
                is DataResult.Failure -> base.update { it.copy(saving = false, error = result.error.toUiText()) }
            }
        }
    }

    private fun fail(text: UiText) = base.update { it.copy(error = text) }
}
