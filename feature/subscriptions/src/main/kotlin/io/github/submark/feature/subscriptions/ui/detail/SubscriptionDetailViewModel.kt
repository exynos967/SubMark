package io.github.submark.feature.subscriptions.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.submark.core.data.currency.CurrencyRepository
import io.github.submark.core.data.repository.CustomFieldRepository
import io.github.submark.core.data.repository.PaymentMethodRepository
import io.github.submark.core.data.repository.SubscriptionExtrasRepository
import io.github.submark.core.data.repository.SubscriptionItem
import io.github.submark.core.data.repository.SubscriptionRepository
import io.github.submark.core.data.result.DataError
import io.github.submark.core.data.result.DataResult
import io.github.submark.core.data.service.ExtendBy
import io.github.submark.core.data.service.PaymentService
import io.github.submark.core.data.service.SharedService
import io.github.submark.core.data.service.SubscriptionService
import io.github.submark.core.data.service.WalletCoverage
import io.github.submark.core.data.service.WalletService
import io.github.submark.core.data.settings.AppSettings
import io.github.submark.core.data.settings.SettingsRepository
import io.github.submark.core.data.time.TimeProvider
import io.github.submark.core.domain.CurrencyConverter
import io.github.submark.core.model.Currency
import io.github.submark.core.model.CustomFieldDefinition
import io.github.submark.core.model.CustomFieldOption
import io.github.submark.core.model.MarkTiming
import io.github.submark.core.model.PaymentMethod
import io.github.submark.core.model.PaymentRecord
import io.github.submark.core.model.RenewalType
import io.github.submark.core.model.SharedConfig
import io.github.submark.core.model.SharedMember
import io.github.submark.core.model.Subscription
import io.github.submark.core.model.SubscriptionPhoto
import io.github.submark.core.model.Wallet
import io.github.submark.core.ui.format.UiText
import io.github.submark.core.ui.navigation.SubscriptionDetailRoute
import io.github.submark.core.ui.util.SnackbarMessage
import io.github.submark.feature.subscriptions.R
import io.github.submark.feature.subscriptions.data.PhotoStorage
import io.github.submark.feature.subscriptions.ui.common.toUiText
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

/** One custom field with its value, for display. */
data class FieldDisplay(val definition: CustomFieldDefinition, val options: List<CustomFieldOption>, val value: String)

data class SharedSummary(val config: SharedConfig?, val members: List<SharedMember>, val userShare: BigDecimal?)

data class WalletSummary(val wallet: Wallet, val coverage: WalletCoverage?)

data class DeleteDialogState(val name: String, val hasWalletCharges: Boolean, val childCount: Int)

data class ExtendSheetState(val currentEnd: LocalDate, val expectedUpdatedAt: Instant)

/** Transient UI state owned by the ViewModel (dialogs that need data-layer lookups). */
data class DetailTransient(
    val busy: Boolean = false,
    val deleteDialog: DeleteDialogState? = null,
    val extendSheet: ExtendSheetState? = null,
    val trialPromptDismissed: Boolean = false,
    val walletProblem: DataError? = null,
)

data class DetailContent(
    val item: SubscriptionItem,
    val parent: Subscription?,
    val payments: List<PaymentRecord>,
    val paymentMethod: PaymentMethod?,
    val fields: List<FieldDisplay>,
    val photos: List<SubscriptionPhoto>,
    val shared: SharedSummary?,
    val wallet: WalletSummary?,
    val wallets: List<Wallet>,
) {
    val subscription: Subscription get() = item.subscription
}

data class DetailUiState(
    val loading: Boolean = true,
    val content: DetailContent? = null,
    val today: LocalDate = LocalDate.MIN,
    val settings: AppSettings = AppSettings(),
    val currencies: Map<String, Currency> = emptyMap(),
    val converter: CurrencyConverter? = null,
    val transient: DetailTransient = DetailTransient(),
) {
    val defaultCurrency: String get() = settings.money.defaultCurrencyCode
    fun symbol(code: String): String? = currencies[code]?.symbol
}

private data class DetailEnv(val settings: AppSettings, val currencies: Map<String, Currency>, val converter: CurrencyConverter)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SubscriptionDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    subscriptions: SubscriptionRepository,
    private val subscriptionService: SubscriptionService,
    private val paymentService: PaymentService,
    paymentMethods: PaymentMethodRepository,
    customFields: CustomFieldRepository,
    private val extras: SubscriptionExtrasRepository,
    private val sharedService: SharedService,
    private val walletService: WalletService,
    currencyRepository: CurrencyRepository,
    settingsRepository: SettingsRepository,
    private val photoStorage: PhotoStorage,
    private val time: TimeProvider,
) : ViewModel() {

    val subscriptionId: String = savedStateHandle.toRoute<SubscriptionDetailRoute>().id

    private val transient = MutableStateFlow(DetailTransient())
    private val messages = Channel<SnackbarMessage>(Channel.BUFFERED)
    val snackbar: Flow<SnackbarMessage> = messages.receiveAsFlow()
    private val closeEvents = Channel<Unit>(Channel.CONFLATED)
    val close: Flow<Unit> = closeEvents.receiveAsFlow()

    /** Set once this screen deleted the subscription, so the vanished row is not reported as "not found". */
    private var deleting = false

    private val context: Flow<DetailEnv> = combine(
        settingsRepository.settings,
        currencyRepository.observeCurrencies(),
        currencyRepository.observeConverter(),
    ) { settings, currencies, converter -> DetailEnv(settings, currencies.associateBy { it.code }, converter) }

    private val fields: Flow<List<FieldDisplay>> = combine(customFields.observeFields(), customFields.observeValues(subscriptionId)) { defs, values ->
        val byField = values.associateBy { it.fieldId }
        defs.sortedBy { it.definition.sortOrder }.mapNotNull { def ->
            val value = byField[def.definition.id]?.value?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            FieldDisplay(def.definition, def.options, value)
        }
    }

    private val shared: Flow<SharedSummary> = combine(
        sharedService.observeConfig(subscriptionId),
        sharedService.observeMembers(subscriptionId),
        subscriptions.observe(subscriptionId),
    ) { config, members, _ -> config to members }
        .mapLatest { (config, members) -> SharedSummary(config, members, sharedService.userShare(subscriptionId)) }

    private val content: Flow<DetailContent?> = subscriptions.observeItem(subscriptionId).flatMapLatest { item ->
        if (item == null) return@flatMapLatest flowOf(null)
        val sub = item.subscription
        val walletFlow: Flow<Wallet?> = sub.walletId?.let { walletService.observe(it) } ?: flowOf(null)
        val first = combine(
            paymentService.observeForSubscription(sub.id),
            paymentMethods.observeAll(),
            fields,
            extras.observePhotos(sub.id),
            shared,
        ) { payments, methods, fieldList, photos, sharedSummary ->
            PartialContent(DetailLogic.sortPayments(payments), methods.firstOrNull { it.id == sub.paymentMethodId }, fieldList, photos, sharedSummary)
        }
        combine(first, walletFlow, walletService.observeWallets(), subscriptions.observeAll(), currencyRepository.observeConverter()) { part, wallet, wallets, all, converter ->
            DetailContent(
                item = item,
                parent = sub.parentId?.let { pid -> all.firstOrNull { it.id == pid } },
                payments = part.payments,
                paymentMethod = part.method,
                fields = part.fields,
                photos = part.photos.sortedBy { it.sortOrder },
                shared = part.shared.takeIf { sub.isShared },
                wallet = wallet?.let { w ->
                    val linked = all.count { it.walletId == w.id }
                    WalletSummary(w, if (w.deletedAt == null) WalletService.coverage(sub, w, converter, linked) else null)
                },
                wallets = wallets,
            )
        }
    }

    private data class PartialContent(
        val payments: List<PaymentRecord>,
        val method: PaymentMethod?,
        val fields: List<FieldDisplay>,
        val photos: List<SubscriptionPhoto>,
        val shared: SharedSummary,
    )

    val uiState: StateFlow<DetailUiState> = combine(context, content, transient) { ctx, detail, tr ->
        if (detail == null && deleting) closeEvents.trySend(Unit)
        DetailUiState(
            loading = false,
            content = detail,
            today = time.today(),
            settings = ctx.settings,
            currencies = ctx.currencies,
            converter = ctx.converter,
            transient = tr,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailUiState(today = time.today()))

    private fun current(): Subscription? = uiState.value.content?.subscription

    private fun show(text: UiText) {
        messages.trySend(SnackbarMessage(text))
    }

    private fun <T> DataResult<T>.report(success: UiText? = null): DataResult<T> {
        when (this) {
            is DataResult.Success -> success?.let(::show)
            is DataResult.Failure -> show(error.toUiText())
        }
        return this
    }

    private fun launchBusy(block: suspend () -> Unit) {
        if (transient.value.busy) return
        transient.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                block()
            } finally {
                transient.update { it.copy(busy = false) }
            }
        }
    }

    fun pause() = launchBusy { subscriptionService.pause(subscriptionId).report(UiText.res(R.string.subscriptions_detail_msg_paused)) }

    fun activate() = launchBusy { subscriptionService.activate(subscriptionId).report(UiText.res(R.string.subscriptions_detail_msg_activated)) }

    fun markPaid(timing: MarkTiming) = launchBusy {
        val newCycle = timing == MarkTiming.EARLY_NEW_CYCLE || timing == MarkTiming.OVERDUE_NEW_CYCLE
        subscriptionService.markPaid(subscriptionId, newCycle).report(UiText.res(R.string.subscriptions_detail_msg_marked_paid))
    }

    fun resolveTrial(renewal: RenewalType) = launchBusy {
        subscriptionService.resolveTrial(subscriptionId, renewal).report(UiText.res(R.string.subscriptions_detail_msg_trial_resolved))
        transient.update { it.copy(trialPromptDismissed = true) }
    }

    fun dismissTrialPrompt() = transient.update { it.copy(trialPromptDismissed = true) }

    fun openExtend() {
        val sub = current() ?: return
        val end = sub.endDate ?: return
        if (!DetailLogic.canExtend(sub)) {
            show(UiText.res(R.string.subscriptions_error_not_extendable))
            return
        }
        transient.update { it.copy(extendSheet = ExtendSheetState(end, sub.updatedAt)) }
    }

    fun closeExtend() = transient.update { it.copy(extendSheet = null) }

    fun extend(by: ExtendBy, fee: BigDecimal?) {
        val sheet = transient.value.extendSheet ?: return
        launchBusy {
            when (val result = subscriptionService.extend(subscriptionId, by, fee, sheet.expectedUpdatedAt)) {
                is DataResult.Success -> {
                    transient.update { it.copy(extendSheet = null) }
                    show(UiText.res(R.string.subscriptions_detail_msg_extended))
                }
                is DataResult.Failure -> {
                    if (result.error == DataError.Stale) {
                        // Re-read the latest state; the user can check the new end date and try again.
                        transient.update { it.copy(extendSheet = null) }
                    }
                    show(result.error.toUiText())
                }
            }
        }
    }

    fun checkWallet(walletId: String?) {
        val sub = current() ?: return
        if (walletId == null) {
            transient.update { it.copy(walletProblem = null) }
            return
        }
        viewModelScope.launch {
            val problem = walletService.chargeProblem(walletId, sub.price, sub.currencyCode)
            transient.update { it.copy(walletProblem = problem) }
        }
    }

    fun activateWishlist(toLifetime: Boolean, walletId: String?) = launchBusy {
        subscriptionService.activateWishlist(subscriptionId, toLifetime, walletId.takeIf { toLifetime })
            .report(UiText.res(R.string.subscriptions_detail_msg_wishlist_activated))
        transient.update { it.copy(walletProblem = null) }
    }

    fun requestDelete() {
        val content = uiState.value.content ?: return
        viewModelScope.launch {
            val charges = subscriptionService.hasWalletCharges(subscriptionId)
            transient.update {
                it.copy(deleteDialog = DeleteDialogState(content.subscription.name, charges, content.item.children.size))
            }
        }
    }

    fun dismissDelete() = transient.update { it.copy(deleteDialog = null) }

    fun delete(reverseWalletCharges: Boolean) {
        transient.update { it.copy(deleteDialog = null) }
        launchBusy {
            deleting = true
            when (val result = subscriptionService.delete(subscriptionId, reverseWalletCharges)) {
                is DataResult.Success -> {
                    photoStorage.delete(result.value.photoFileNames)
                    closeEvents.trySend(Unit)
                }
                is DataResult.Failure -> {
                    deleting = false
                    show(result.error.toUiText())
                }
            }
        }
    }

    fun deletePhoto(photo: SubscriptionPhoto) = launchBusy {
        extras.deletePhoto(photo).report().let { if (it.isSuccess) photoStorage.delete(listOf(photo.fileName)) }
    }

    fun notifyCopied() = show(UiText.res(R.string.subscriptions_detail_msg_note_copied))

    fun notifyCannotOpen() = show(UiText.res(R.string.subscriptions_detail_msg_cannot_open))
}
