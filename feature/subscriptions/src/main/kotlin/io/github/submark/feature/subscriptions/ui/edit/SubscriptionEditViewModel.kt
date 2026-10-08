package io.github.submark.feature.subscriptions.ui.edit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.submark.core.data.currency.CurrencyRepository
import io.github.submark.core.data.repository.CategoryRepository
import io.github.submark.core.data.repository.CustomFieldRepository
import io.github.submark.core.data.repository.CustomFieldWithOptions
import io.github.submark.core.data.repository.PaymentMethodRepository
import io.github.submark.core.data.repository.SubscriptionExtrasRepository
import io.github.submark.core.data.repository.SubscriptionRepository
import io.github.submark.core.data.repository.TagRepository
import io.github.submark.core.data.result.DataError
import io.github.submark.core.data.result.DataResult
import io.github.submark.core.data.service.PaymentService
import io.github.submark.core.data.service.SubscriptionService
import io.github.submark.core.data.service.WalletService
import io.github.submark.core.data.settings.AddFormSettings
import io.github.submark.core.data.settings.SettingsRepository
import io.github.submark.core.data.time.TimeProvider
import io.github.submark.core.model.Category
import io.github.submark.core.model.Currency
import io.github.submark.core.model.IconType
import io.github.submark.core.model.PaymentMethod
import io.github.submark.core.model.Subscription
import io.github.submark.core.model.SubscriptionPrefill
import io.github.submark.core.model.Tag
import io.github.submark.core.model.Wallet
import io.github.submark.core.ui.format.MoneyInput
import io.github.submark.core.ui.format.UiText
import io.github.submark.core.ui.icon.IconChoice
import io.github.submark.core.ui.navigation.SubscriptionEditRoute
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/** A photo shown in the form: stored ([id] non-null, edit mode) or pending until the new subscription exists. */
data class FormPhoto(val id: String?, val fileName: String)

/** Form-local editing state owned by the ViewModel. */
data class FormSession(
    val loaded: Boolean = false,
    val notFound: Boolean = false,
    val form: SubscriptionForm? = null,
    val initial: SubscriptionForm? = null,
    val base: Subscription? = null,
    val showErrors: Boolean = false,
    val saving: Boolean = false,
    val duplicateOf: Subscription? = null,
    val pendingPhotos: List<String> = emptyList(),
)

data class EditOptions(
    val addForm: AddFormSettings = AddFormSettings(),
    val wishlistEnabled: Boolean = true,
    val aiEnabled: Boolean = false,
    val defaultCurrencyCode: String = "USD",
    val currencies: List<Currency> = emptyList(),
    val categories: List<Category> = emptyList(),
    val paymentMethods: List<PaymentMethod> = emptyList(),
    val wallets: List<Wallet> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val allFields: List<CustomFieldWithOptions> = emptyList(),
)

data class SubscriptionEditUiState(
    val isEdit: Boolean,
    val loading: Boolean = true,
    val notFound: Boolean = false,
    val today: LocalDate = LocalDate.MIN,
    val zone: ZoneId = ZoneId.systemDefault(),
    val form: SubscriptionForm? = null,
    val options: EditOptions = EditOptions(),
    val fields: List<CustomFieldWithOptions> = emptyList(),
    val photos: List<FormPhoto> = emptyList(),
    val startDateLocked: Boolean = false,
    val walletProblem: DataError? = null,
    val depositWalletProblem: DataError? = null,
    val problems: List<FormProblem> = emptyList(),
    val showErrors: Boolean = false,
    val saving: Boolean = false,
    val duplicateOf: Subscription? = null,
    val dirty: Boolean = false,
)

sealed interface EditEvent {
    data object Close : EditEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SubscriptionEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val subscriptions: SubscriptionRepository,
    private val service: SubscriptionService,
    private val categories: CategoryRepository,
    private val tags: TagRepository,
    private val customFields: CustomFieldRepository,
    private val paymentMethods: PaymentMethodRepository,
    private val extras: SubscriptionExtrasRepository,
    private val payments: PaymentService,
    private val wallets: WalletService,
    private val currencies: CurrencyRepository,
    private val settings: SettingsRepository,
    private val photoStorage: PhotoStorage,
    private val time: TimeProvider,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<SubscriptionEditRoute>()
    private val editId: String? = route.id
    private val isEdit = editId != null

    private val session = MutableStateFlow(FormSession())
    private val snackbarChannel = Channel<SnackbarMessage>(Channel.BUFFERED)
    private val eventChannel = Channel<EditEvent>(Channel.BUFFERED)
    val snackbar: Flow<SnackbarMessage> = snackbarChannel.receiveAsFlow()
    val events: Flow<EditEvent> = eventChannel.receiveAsFlow()

    private val options: Flow<EditOptions> = combine(
        settings.settings,
        currencies.observeEnabledSorted(),
        categories.observeVisible(),
        combine(paymentMethods.observeAll(), wallets.observeWallets(), ::Pair),
        combine(tags.observeTags(), customFields.observeFields(), ::Pair),
    ) { s, currencyList, categoryList, (methods, walletList), (tagList, fields) ->
        EditOptions(
            addForm = s.addForm,
            wishlistEnabled = s.subscriptions.wishlistEnabled,
            aiEnabled = s.integrations.aiEnabled,
            defaultCurrencyCode = s.money.defaultCurrencyCode,
            currencies = currencyList,
            categories = categoryList.sortedBy { it.sortOrder },
            paymentMethods = methods.sortedBy { it.sortOrder },
            wallets = walletList.filter { it.isActive },
            tags = tagList.sortedBy { it.name.lowercase() },
            allFields = fields,
        )
    }

    private val storedPhotos: Flow<List<FormPhoto>> =
        if (editId != null) extras.observePhotos(editId).map { list -> list.sortedBy { it.sortOrder }.map { FormPhoto(it.id, it.fileName) } } else flowOf(emptyList())

    private val startLocked: Flow<Boolean> =
        if (editId != null) payments.observeForSubscription(editId).map { it.isNotEmpty() }.distinctUntilChanged() else flowOf(false)

    private val walletProblems: Flow<Pair<DataError?, DataError?>> = session
        .map { s ->
            val f = s.form
            if (f == null) {
                WalletCheck(null, null, null)
            } else {
                val price = MoneyInput.parse(f.priceText)
                val deposit = MoneyInput.parse(f.initialDepositText)
                WalletCheck(
                    f.walletId?.takeIf { f.kind != FormKind.WISHLIST && price != null && price.signum() > 0 }?.let { it to price!! },
                    f.depositWalletId?.takeIf { !isEdit && f.kind == FormKind.STORED_VALUE && deposit != null && deposit.signum() > 0 }?.let { it to deposit!! },
                    f.currencyCode,
                )
            }
        }
        .distinctUntilChanged()
        .mapLatest { check ->
            val code = check.currencyCode ?: return@mapLatest null to null
            val main = check.main?.let { (id, amount) -> wallets.chargeProblem(id, amount, code) }
            val deposit = check.deposit?.let { (id, amount) -> wallets.chargeProblem(id, amount, code) }
            main to deposit
        }

    private data class WalletCheck(
        val main: Pair<String, java.math.BigDecimal>?,
        val deposit: Pair<String, java.math.BigDecimal>?,
        val currencyCode: String?,
    )

    val uiState: StateFlow<SubscriptionEditUiState> = combine(
        session,
        options,
        combine(storedPhotos, startLocked, ::Pair),
        walletProblems,
    ) { s, opts, (stored, locked), (walletProblem, depositProblem) ->
        val form = s.form
        val fields = form?.let { SubscriptionFormLogic.applicableFields(opts.allFields, it.categoryId) }.orEmpty()
        SubscriptionEditUiState(
            isEdit = isEdit,
            loading = !s.loaded,
            notFound = s.notFound,
            today = time.today(),
            zone = time.zone(),
            form = form,
            options = opts,
            fields = fields,
            photos = stored + s.pendingPhotos.map { FormPhoto(null, it) },
            startDateLocked = locked,
            walletProblem = walletProblem,
            depositWalletProblem = depositProblem,
            problems = form?.let { SubscriptionFormLogic.validate(it, opts.allFields, isEdit) }.orEmpty(),
            showErrors = s.showErrors,
            saving = s.saving,
            duplicateOf = s.duplicateOf,
            dirty = form != null && (form != s.initial || s.pendingPhotos.isNotEmpty()),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SubscriptionEditUiState(isEdit = isEdit, today = time.today()))

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val s = settings.settings.first()
        val today = time.today()
        if (editId != null) {
            val sub = subscriptions.get(editId)
            if (sub == null) {
                session.update { it.copy(loaded = true, notFound = true) }
                return
            }
            val values = customFields.observeValues(editId).first().associate { it.fieldId to it.value }
            val form = SubscriptionFormLogic.formFromSubscription(sub, tags.getTagIds(editId), values)
            session.update { it.copy(loaded = true, form = form, initial = form, base = sub) }
            return
        }
        var form = SubscriptionForm(
            currencyCode = s.money.defaultCurrencyCode,
            startDate = today,
            generateHistory = s.subscriptions.autoGenerateHistoryDefault,
        )
        val initial = form
        route.prefillJson?.let { json ->
            val prefill = try {
                PREFILL_JSON.decodeFromString(SubscriptionPrefill.serializer(), json)
            } catch (e: SerializationException) {
                null
            } catch (e: IllegalArgumentException) {
                null
            }
            if (prefill == null) {
                snackbarChannel.trySend(SnackbarMessage(UiText.res(R.string.subscriptions_edit_prefill_failed)))
            } else {
                val visible = categories.observeVisible().first().map { it.id }.toSet()
                val tagIds = prefill.tags.mapNotNull { name -> tags.findOrCreate(name).getOrNull()?.id }
                form = SubscriptionFormLogic.applyPrefill(form, prefill, visible, tagIds)
                prefill.currencyCode?.let { code ->
                    val currency = currencies.get(code)
                    when {
                        currency == null -> form = form.copy(currencyCode = s.money.defaultCurrencyCode)
                        !currency.isEnabled -> currencies.enable(code)
                    }
                }
            }
        }
        // A prefilled form counts as unsaved input; an empty one does not.
        session.update { it.copy(loaded = true, form = form, initial = initial) }
    }

    fun update(transform: (SubscriptionForm) -> SubscriptionForm) {
        session.update { s -> s.form?.let { s.copy(form = transform(it)) } ?: s }
    }

    fun setStartDate(date: LocalDate) {
        val form = session.value.form ?: return
        val (updated, adjusted) = SubscriptionFormLogic.withStartDate(form, date)
        session.update { it.copy(form = updated) }
        if (adjusted) snackbarChannel.trySend(SnackbarMessage(UiText.res(R.string.subscriptions_edit_end_adjusted)))
    }

    fun setIcon(choice: IconChoice) = update { it.copy(iconType = choice.type, iconValue = choice.value) }

    fun clearIcon() = update { it.copy(iconType = null, iconValue = null) }

    fun setKind(kind: FormKind) = update { f ->
        val children = if (kind == FormKind.BUNDLE && f.children.isEmpty()) listOf(SubscriptionFormLogic.newChild(f)) else f.children
        f.copy(kind = kind, children = children, isActive = if (kind == FormKind.WISHLIST) false else if (f.kind == FormKind.WISHLIST) true else f.isActive)
    }

    fun addChild() = update { it.copy(children = it.children + SubscriptionFormLogic.newChild(it)) }

    fun updateChild(key: String, transform: (ChildForm) -> ChildForm) = update { f ->
        f.copy(children = f.children.map { if (it.key == key) transform(it) else it })
    }

    fun copyMainDates(key: String) = update { f -> f.copy(children = f.children.map { if (it.key == key) SubscriptionFormLogic.copyMainDates(it, f) else it }) }

    fun removeChild(key: String) = update { f -> f.copy(children = f.children.filterNot { it.key == key }) }

    /** Creates a category from the picker and selects it. */
    fun createCategory(name: String, colorHex: String) {
        viewModelScope.launch {
            when (val result = categories.create(name, IconType.SYMBOL, DEFAULT_CATEGORY_ICON, colorHex)) {
                is DataResult.Success -> update { it.copy(categoryId = result.value.id) }
                is DataResult.Failure -> snackbarChannel.send(SnackbarMessage(result.error.toUiText()))
            }
        }
    }

    /** Tag picker "Create" callback: returns the id to select. */
    suspend fun createTag(name: String): String? = when (val result = tags.findOrCreate(name)) {
        is DataResult.Success -> result.value.id
        is DataResult.Failure -> {
            snackbarChannel.send(SnackbarMessage(result.error.toUiText()))
            null
        }
    }

    fun addPhoto(uri: android.net.Uri) {
        viewModelScope.launch {
            val fileName = photoStorage.import(uri)
            if (fileName == null) {
                snackbarChannel.send(SnackbarMessage(UiText.res(R.string.subscriptions_edit_photo_failed)))
                return@launch
            }
            if (editId != null) {
                extras.addPhoto(editId, fileName).onFailureMessage { photoStorage.delete(listOf(fileName)) }
            } else {
                session.update { it.copy(pendingPhotos = it.pendingPhotos + fileName) }
            }
        }
    }

    fun removePhoto(photo: FormPhoto) {
        viewModelScope.launch {
            if (photo.id != null && editId != null) {
                val stored = extras.observePhotos(editId).first().firstOrNull { it.id == photo.id } ?: return@launch
                extras.deletePhoto(stored).onFailureMessage { }
                if (extras.observePhotos(editId).first().none { it.id == photo.id }) photoStorage.delete(listOf(photo.fileName))
            } else {
                session.update { it.copy(pendingPhotos = it.pendingPhotos - photo.fileName) }
                photoStorage.delete(listOf(photo.fileName))
            }
        }
    }

    fun showErrors() = session.update { it.copy(showErrors = true) }

    fun save(allowDuplicate: Boolean = false) {
        val s = session.value
        val form = s.form ?: return
        if (s.saving) return
        viewModelScope.launch {
            val fields = customFields.observeFields().first()
            val problems = SubscriptionFormLogic.validate(form, fields, isEdit)
            if (problems.isNotEmpty()) {
                session.update { it.copy(showErrors = true, duplicateOf = null) }
                snackbarChannel.send(SnackbarMessage(problems.first().toUiText()))
                return@launch
            }
            session.update { it.copy(saving = true, duplicateOf = null) }
            val draft = SubscriptionFormLogic.buildDraft(form, s.base, fields, time.today(), time.now(), allowDuplicate)
            val result: DataResult<String> = if (isEdit) {
                when (val r = service.update(draft)) {
                    is DataResult.Success -> DataResult.Success(draft.subscription.id)
                    is DataResult.Failure -> r
                }
            } else {
                service.create(draft)
            }
            when (result) {
                is DataResult.Success -> {
                    if (!isEdit) {
                        session.value.pendingPhotos.forEach { extras.addPhoto(result.value, it) }
                    }
                    session.update { it.copy(saving = false, pendingPhotos = emptyList(), initial = it.form) }
                    eventChannel.send(EditEvent.Close)
                }
                is DataResult.Failure -> {
                    val error = result.error
                    if (error is DataError.DuplicateAppStoreId) {
                        session.update { it.copy(saving = false, duplicateOf = error.existing) }
                    } else {
                        session.update { it.copy(saving = false) }
                        snackbarChannel.send(SnackbarMessage(UiText.res(R.string.subscriptions_edit_save_failed, error.toUiText())))
                    }
                }
            }
        }
    }

    fun dismissDuplicate() = session.update { it.copy(duplicateOf = null) }

    /** Leaves without saving: removes photo files that were never attached. */
    fun discard() {
        val pending = session.value.pendingPhotos
        session.update { it.copy(pendingPhotos = emptyList(), initial = it.form) }
        viewModelScope.launch {
            if (pending.isNotEmpty()) photoStorage.delete(pending)
            eventChannel.send(EditEvent.Close)
        }
    }

    private suspend fun <T> DataResult<T>.onFailureMessage(cleanup: suspend () -> Unit) {
        if (this is DataResult.Failure) {
            cleanup()
            snackbarChannel.send(SnackbarMessage(error.toUiText()))
        }
    }

    private companion object {
        const val DEFAULT_CATEGORY_ICON = "category"
        val PREFILL_JSON = Json { ignoreUnknownKeys = true }
    }
}
