package io.github.submark.feature.subscriptions.ui.edit

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.LocalOffer
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import coil.compose.AsyncImage
import io.github.submark.core.model.BillingCycle
import io.github.submark.core.model.CycleUnit
import io.github.submark.core.model.RenewalType
import io.github.submark.core.model.SubscriptionKind
import io.github.submark.core.ui.component.CategoryChip
import io.github.submark.core.ui.component.ConfirmDialog
import io.github.submark.core.ui.component.CurrencyPickerSheet
import io.github.submark.core.ui.component.DatePickerField
import io.github.submark.core.ui.component.ErrorState
import io.github.submark.core.ui.component.LoadingState
import io.github.submark.core.ui.component.MoneyInputField
import io.github.submark.core.ui.component.SectionCard
import io.github.submark.core.ui.component.SegmentedTabs
import io.github.submark.core.ui.component.SettingsSwitchRow
import io.github.submark.core.ui.component.SubMarkTopAppBar
import io.github.submark.core.ui.component.TagChip
import io.github.submark.core.ui.format.MoneyInput
import io.github.submark.core.ui.format.asString
import io.github.submark.core.ui.format.displayName
import io.github.submark.core.ui.format.labelRes
import io.github.submark.core.ui.icon.SubscriptionIcon
import io.github.submark.core.ui.navigation.AiRecognitionRoute
import io.github.submark.core.ui.navigation.CategoryManagementRoute
import io.github.submark.core.ui.navigation.IconPickerRoute
import io.github.submark.core.ui.navigation.PopularSubscriptionsRoute
import io.github.submark.core.ui.util.SnackbarEffect
import io.github.submark.core.ui.util.colorFromHex
import io.github.submark.feature.subscriptions.R
import io.github.submark.feature.subscriptions.data.PhotoStorage
import io.github.submark.feature.subscriptions.ui.common.IconResultEffect
import io.github.submark.feature.subscriptions.ui.common.TagPickerSheet
import io.github.submark.feature.subscriptions.ui.common.toUiText
import java.io.File
import java.time.LocalDate
import io.github.submark.core.ui.R as UiR

@Composable
fun SubscriptionEditScreenRoute(backStackEntry: NavBackStackEntry, onBack: () -> Unit, onNavigate: (Any) -> Unit) {
    val viewModel: SubscriptionEditViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    SnackbarEffect(viewModel.snackbar, snackbarHost)
    IconResultEffect(backStackEntry) { viewModel.setIcon(it) }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event -> if (event == EditEvent.Close) onBack() }
    }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) viewModel.addPhoto(uri)
    }
    SubscriptionEditScreen(
        state = state,
        snackbarHost = snackbarHost,
        actions = EditActions(
            update = viewModel::update,
            setStartDate = viewModel::setStartDate,
            setKind = viewModel::setKind,
            clearIcon = viewModel::clearIcon,
            pickIcon = { query -> onNavigate(IconPickerRoute(query = query.ifBlank { null })) },
            addChild = viewModel::addChild,
            updateChild = viewModel::updateChild,
            copyMainDates = viewModel::copyMainDates,
            removeChild = viewModel::removeChild,
            createCategory = viewModel::createCategory,
            createTag = viewModel::createTag,
            addPhoto = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            removePhoto = viewModel::removePhoto,
            save = { viewModel.save() },
            saveAnyway = { viewModel.save(allowDuplicate = true) },
            dismissDuplicate = viewModel::dismissDuplicate,
            discard = viewModel::discard,
            back = onBack,
            navigate = onNavigate,
        ),
    )
}

/** Callbacks of the stateless form. */
class EditActions(
    val update: ((SubscriptionForm) -> SubscriptionForm) -> Unit,
    val setStartDate: (LocalDate) -> Unit,
    val setKind: (FormKind) -> Unit,
    val clearIcon: () -> Unit,
    val pickIcon: (String) -> Unit,
    val addChild: () -> Unit,
    val updateChild: (String, (ChildForm) -> ChildForm) -> Unit,
    val copyMainDates: (String) -> Unit,
    val removeChild: (String) -> Unit,
    val createCategory: (String, String) -> Unit,
    val createTag: suspend (String) -> String?,
    val addPhoto: () -> Unit,
    val removePhoto: (FormPhoto) -> Unit,
    val save: () -> Unit,
    val saveAnyway: () -> Unit,
    val dismissDuplicate: () -> Unit,
    val discard: () -> Unit,
    val back: () -> Unit,
    val navigate: (Any) -> Unit,
)

@Composable
fun SubscriptionEditScreen(state: SubscriptionEditUiState, snackbarHost: SnackbarHostState, actions: EditActions) {
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    val requestBack = { if (state.dirty) confirmDiscard = true else actions.back() }
    BackHandler(enabled = state.dirty) { confirmDiscard = true }

    Scaffold(
        topBar = {
            SubMarkTopAppBar(
                title = stringResource(if (state.isEdit) R.string.subscriptions_edit_title_edit else R.string.subscriptions_edit_title_add),
                subtitle = stringResource(if (state.isEdit) R.string.subscriptions_edit_subtitle_edit else R.string.subscriptions_edit_subtitle_add),
                onBack = requestBack,
                actions = {
                    if (!state.isEdit && state.form != null) {
                        if (state.options.addForm.showPopularButton) {
                            IconButton(onClick = { actions.navigate(PopularSubscriptionsRoute) }) {
                                Icon(Icons.Rounded.Storefront, contentDescription = stringResource(R.string.subscriptions_edit_popular))
                            }
                        }
                        if (state.options.aiEnabled) {
                            IconButton(onClick = { actions.navigate(AiRecognitionRoute()) }) {
                                Icon(Icons.Rounded.AutoAwesome, contentDescription = stringResource(R.string.subscriptions_edit_ai_scan))
                            }
                        }
                    }
                    if (state.form != null) {
                        TextButton(onClick = actions.save, enabled = !state.saving) { Text(stringResource(UiR.string.ui_action_save)) }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { padding ->
        val form = state.form
        when {
            state.loading -> LoadingState(Modifier.padding(padding))
            state.notFound || form == null -> ErrorState(
                message = stringResource(R.string.subscriptions_edit_not_found),
                onRetry = null,
                modifier = Modifier.padding(padding).fillMaxSize(),
            )
            else -> EditForm(state, form, actions, Modifier.padding(padding))
        }
    }

    if (confirmDiscard) {
        ConfirmDialog(
            title = stringResource(R.string.subscriptions_edit_discard_title),
            message = stringResource(R.string.subscriptions_edit_discard_message),
            onConfirm = {
                confirmDiscard = false
                actions.discard()
            },
            onDismiss = { confirmDiscard = false },
            confirmLabel = stringResource(R.string.subscriptions_edit_discard_confirm),
            dismissLabel = stringResource(R.string.subscriptions_edit_discard_keep),
            destructive = true,
        )
    }
    state.duplicateOf?.let { existing ->
        AlertDialog(
            onDismissRequest = actions.dismissDuplicate,
            title = { Text(stringResource(R.string.subscriptions_edit_duplicate_title)) },
            text = { Text(stringResource(R.string.subscriptions_edit_duplicate_message, existing.name)) },
            confirmButton = { TextButton(onClick = actions.saveAnyway) { Text(stringResource(R.string.subscriptions_edit_duplicate_add_anyway)) } },
            dismissButton = { TextButton(onClick = actions.dismissDuplicate) { Text(stringResource(UiR.string.ui_action_cancel)) } },
        )
    }
}

@Composable
private fun EditForm(state: SubscriptionEditUiState, form: SubscriptionForm, actions: EditActions, modifier: Modifier) {
    val settings = state.options.addForm
    val errors = if (state.showErrors) state.problems else emptyList()
    Column(
        modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BasicInfoCard(state, form, errors, actions)
        DatesCard(state, form, errors, actions)
        if (form.kind != FormKind.LIFETIME) PaymentCard(state, form, errors, actions)
        if (form.kind == FormKind.LIFETIME && state.options.wallets.isNotEmpty()) LifetimeWalletCard(state, form, actions)
        if (form.kind == FormKind.BUNDLE && !state.isEdit) BundleCard(state, form, errors, actions)
        if (settings.showTagManagement) TagsCard(state, form, actions)
        AdditionalCard(state, form, errors, actions)
        if (state.showErrors && errors.isNotEmpty()) {
            Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    errors.forEach { Text(it.toUiText().asString(), color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
        Box(Modifier.padding(bottom = 24.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BasicInfoCard(state: SubscriptionEditUiState, form: SubscriptionForm, errors: List<FormProblem>, actions: EditActions) {
    var currencySheet by rememberSaveable { mutableStateOf(false) }
    var categorySheet by rememberSaveable { mutableStateOf(false) }
    var newCategory by rememberSaveable { mutableStateOf(false) }
    val opts = state.options
    SectionCard(title = stringResource(R.string.subscriptions_edit_section_basic)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box {
                    Surface(onClick = { actions.pickIcon(form.name) }, shape = RoundedCornerShape(16.dp)) {
                        SubscriptionIcon(form.iconType, form.iconValue, fallbackName = form.name, size = 64.dp)
                    }
                }
                Column(Modifier.weight(1f)) {
                    OutlinedTextField(
                        value = form.name,
                        onValueChange = { v -> actions.update { it.copy(name = v) } },
                        label = { Text(stringResource(R.string.subscriptions_edit_name)) },
                        placeholder = { Text(stringResource(R.string.subscriptions_edit_name_hint)) },
                        isError = FormProblem.BlankName in errors,
                        supportingText = if (FormProblem.BlankName in errors) {
                            { Text(stringResource(R.string.subscriptions_edit_error_name)) }
                        } else {
                            null
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row {
                        TextButton(onClick = { actions.pickIcon(form.name) }) {
                            Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(stringResource(R.string.subscriptions_edit_icon_change), modifier = Modifier.padding(start = 4.dp))
                        }
                        if (form.iconValue != null) {
                            TextButton(onClick = actions.clearIcon) { Text(stringResource(R.string.subscriptions_edit_icon_clear)) }
                        }
                    }
                }
            }

            Text(stringResource(R.string.subscriptions_edit_kind), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                availableKinds(state).forEach { kind ->
                    FilterChip(selected = form.kind == kind, onClick = { actions.setKind(kind) }, label = { Text(kindLabel(kind)) })
                }
            }
            HintText(kindDescription(form.kind))

            val currency = opts.currencies.firstOrNull { it.code == form.currencyCode }
            val priceProblem = errors.filterIsInstance<FormProblem.Price>().firstOrNull()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                MoneyInputField(
                    value = form.priceText,
                    onValueChange = { v -> actions.update { it.copy(priceText = v) } },
                    label = stringResource(
                        when (form.kind) {
                            FormKind.LIFETIME -> R.string.subscriptions_edit_price_lifetime
                            FormKind.STORED_VALUE -> R.string.subscriptions_edit_price_stored_value
                            FormKind.WISHLIST -> R.string.subscriptions_edit_price_wishlist
                            else -> R.string.subscriptions_edit_price
                        },
                    ),
                    currencySymbol = currency?.symbol,
                    allowZero = true,
                    showErrorWhenEmpty = priceProblem != null,
                    imeAction = androidx.compose.ui.text.input.ImeAction.Next,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(onClick = { currencySheet = true }, modifier = Modifier.padding(top = 8.dp)) {
                    Text(form.currencyCode)
                }
            }
            if (MoneyInput.parse(form.priceText)?.signum() == 0) HintText(stringResource(R.string.subscriptions_edit_price_free))

            val category = opts.categories.firstOrNull { it.id == form.categoryId }
            Text(stringResource(R.string.subscriptions_edit_category), style = MaterialTheme.typography.labelLarge)
            CategoryChip(
                name = category?.displayName()?.asString() ?: stringResource(R.string.subscriptions_edit_category_choose),
                color = colorFromHex(category?.colorHex),
                iconType = category?.iconType,
                iconValue = category?.iconValue,
                selected = true,
                onClick = { categorySheet = true },
            )
        }
    }
    if (currencySheet) {
        CurrencyPickerSheet(
            currencies = opts.currencies,
            selectedCode = form.currencyCode,
            onSelect = { c ->
                actions.update { it.copy(currencyCode = c.code) }
                currencySheet = false
            },
            onDismiss = { currencySheet = false },
            defaultCode = opts.defaultCurrencyCode,
        )
    }
    if (categorySheet) {
        CategoryPickerSheet(
            categories = opts.categories,
            selectedId = form.categoryId,
            onSelect = { id ->
                actions.update { it.copy(categoryId = id) }
                categorySheet = false
            },
            onCreate = {
                categorySheet = false
                newCategory = true
            },
            onManage = {
                categorySheet = false
                actions.navigate(CategoryManagementRoute)
            },
            onDismiss = { categorySheet = false },
        )
    }
    if (newCategory) {
        NewCategoryDialog(
            onConfirm = { name, hex ->
                newCategory = false
                actions.createCategory(name, hex)
            },
            onDismiss = { newCategory = false },
        )
    }
}

private fun availableKinds(state: SubscriptionEditUiState): List<FormKind> = buildList {
    add(FormKind.REGULAR)
    add(FormKind.STORED_VALUE)
    add(FormKind.LIFETIME)
    if (state.options.wishlistEnabled || state.form?.kind == FormKind.WISHLIST) add(FormKind.WISHLIST)
    if (!state.isEdit && (state.options.addForm.showLocalBundleOption || state.form?.kind == FormKind.BUNDLE)) add(FormKind.BUNDLE)
}

@Composable
private fun kindLabel(kind: FormKind): String = when (kind) {
    FormKind.REGULAR -> stringResource(SubscriptionKind.REGULAR.labelRes)
    FormKind.STORED_VALUE -> stringResource(SubscriptionKind.STORED_VALUE.labelRes)
    FormKind.LIFETIME -> stringResource(SubscriptionKind.LIFETIME.labelRes)
    FormKind.WISHLIST -> stringResource(SubscriptionKind.WISHLIST.labelRes)
    FormKind.BUNDLE -> stringResource(R.string.subscriptions_edit_kind_bundle)
}

@Composable
private fun kindDescription(kind: FormKind): String = stringResource(
    when (kind) {
        FormKind.REGULAR -> R.string.subscriptions_edit_kind_regular_desc
        FormKind.STORED_VALUE -> R.string.subscriptions_edit_kind_stored_value_desc
        FormKind.LIFETIME -> R.string.subscriptions_edit_kind_lifetime_desc
        FormKind.WISHLIST -> R.string.subscriptions_edit_kind_wishlist_desc
        FormKind.BUNDLE -> R.string.subscriptions_edit_kind_bundle_desc
    },
)

@Composable
private fun DatesCard(state: SubscriptionEditUiState, form: SubscriptionForm, errors: List<FormProblem>, actions: EditActions) {
    var durationDialog by rememberSaveable { mutableStateOf(false) }
    val showEnd = state.options.addForm.showEndDateOptions
    SectionCard(title = stringResource(R.string.subscriptions_edit_section_dates)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DatePickerField(
                label = stringResource(if (form.kind == FormKind.LIFETIME) R.string.subscriptions_edit_purchase_date else R.string.subscriptions_edit_start_date),
                date = form.startDate,
                onDateChange = actions.setStartDate,
                today = state.today,
                showRelative = true,
                enabled = !state.startDateLocked,
                supportingText = if (state.startDateLocked) stringResource(R.string.subscriptions_edit_start_locked) else null,
                modifier = Modifier.fillMaxWidth(),
            )
            if (showEnd) {
                SettingsSwitchRow(
                    title = stringResource(R.string.subscriptions_edit_end_date_toggle),
                    subtitle = stringResource(R.string.subscriptions_edit_end_date_desc),
                    checked = form.endDate != null,
                    onCheckedChange = { on ->
                        actions.update { f ->
                            if (on) f.copy(endDate = f.endDate ?: f.startDate.plusMonths(1)) else f.copy(endDate = null, isSingleCycle = false)
                        }
                    },
                )
                val endError = when {
                    FormProblem.EndNotAfterStart in errors -> stringResource(R.string.subscriptions_edit_error_end_after_start)
                    FormProblem.SingleCycleNeedsEnd in errors -> stringResource(R.string.subscriptions_edit_error_single_cycle_end)
                    else -> null
                }
                if (form.endDate != null) {
                    DatePickerField(
                        label = stringResource(R.string.subscriptions_edit_end_date),
                        date = form.endDate,
                        onDateChange = { d -> actions.update { it.copy(endDate = d) } },
                        today = state.today,
                        minDate = form.startDate.plusDays(1),
                        showRelative = true,
                        isError = endError != null,
                        supportingText = endError,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { durationDialog = true }) { Text(stringResource(R.string.subscriptions_edit_duration_button)) }
                        TextButton(onClick = { actions.update { it.copy(endDate = null, isSingleCycle = false) } }) {
                            Text(stringResource(R.string.subscriptions_edit_end_date_clear))
                        }
                    }
                } else if (endError != null) {
                    ErrorText(endError)
                }
                if (form.isRecurring) {
                    SettingsSwitchRow(
                        title = stringResource(R.string.subscriptions_edit_single_cycle),
                        subtitle = stringResource(R.string.subscriptions_edit_single_cycle_desc),
                        checked = form.isSingleCycle,
                        onCheckedChange = { on ->
                            actions.update { f ->
                                if (on) f.copy(isSingleCycle = true, endDate = f.endDate ?: f.startDate.plusMonths(1), fixedPaymentDay = null) else f.copy(isSingleCycle = false)
                            }
                        },
                    )
                }
            }
            if (form.isRecurring && form.renewalType == RenewalType.TRIAL) {
                Text(stringResource(R.string.subscriptions_edit_trial_section), style = MaterialTheme.typography.titleSmall)
                DatePickerField(
                    label = stringResource(R.string.subscriptions_edit_trial_start),
                    date = form.trialStartDate,
                    onDateChange = { d -> actions.update { it.copy(trialStartDate = d) } },
                    today = state.today,
                    isError = FormProblem.TrialStart in errors,
                    supportingText = if (FormProblem.TrialStart in errors) stringResource(R.string.subscriptions_edit_error_trial_start) else null,
                    modifier = Modifier.fillMaxWidth(),
                )
                TrialDaysInput(
                    text = form.trialDaysText,
                    onChange = { v -> actions.update { it.copy(trialDaysText = v) } },
                    isError = SubscriptionFormLogic.parseTrialDays(form.trialDaysText) == null,
                )
                val trialEnd = form.trialStartDate?.let { start -> SubscriptionFormLogic.parseTrialDays(form.trialDaysText)?.let { start.plusDays(it.toLong()) } }
                if (trialEnd != null) {
                    HintText(stringResource(R.string.subscriptions_edit_trial_billing_hint, io.github.submark.core.ui.format.DateLabels.formatDate(trialEnd)))
                }
            }
        }
    }
    if (durationDialog) {
        DurationDialog(
            onConfirm = { amount, months ->
                durationDialog = false
                actions.update { it.copy(endDate = SubscriptionFormLogic.endByDuration(it.startDate, amount, months)) }
            },
            onDismiss = { durationDialog = false },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PaymentCard(state: SubscriptionEditUiState, form: SubscriptionForm, errors: List<FormProblem>, actions: EditActions) {
    var fixedDayDialog by rememberSaveable { mutableStateOf(false) }
    val settings = state.options.addForm
    val wishlist = form.kind == FormKind.WISHLIST
    SectionCard(title = stringResource(R.string.subscriptions_edit_section_payment)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!form.isSingleCycle) {
                Text(stringResource(R.string.subscriptions_edit_cycle), style = MaterialTheme.typography.labelLarge)
                CycleChooser(
                    cycle = form.billingCycle,
                    countText = form.customCountText,
                    unit = form.customUnit,
                    onCycle = { c -> actions.update { f -> f.copy(billingCycle = c, fixedPaymentDay = f.fixedPaymentDay.takeIf { c in SubscriptionFormLogic.MONTH_BASED }) } },
                    onCount = { v -> actions.update { it.copy(customCountText = v) } },
                    onUnit = { u -> actions.update { it.copy(customUnit = u) } },
                    countError = FormProblem.CustomCycle in errors,
                )
            } else {
                HintText(stringResource(R.string.subscriptions_edit_single_cycle_auto_period))
            }
            if (wishlist) {
                HintText(stringResource(R.string.subscriptions_edit_wishlist_cycle_hint))
                return@Column
            }
            if (settings.showFixedPaymentDay && SubscriptionFormLogic.fixedDayEligible(form)) {
                SettingsSwitchRow(
                    title = stringResource(R.string.subscriptions_edit_fixed_day),
                    subtitle = form.fixedPaymentDay?.let { stringResource(R.string.subscriptions_edit_fixed_day_value, it) }
                        ?: stringResource(R.string.subscriptions_edit_fixed_day_desc),
                    checked = form.fixedPaymentDay != null,
                    onCheckedChange = { on ->
                        if (on) fixedDayDialog = true else actions.update { it.copy(fixedPaymentDay = null) }
                    },
                )
                if (form.fixedPaymentDay != null) {
                    TextButton(onClick = { fixedDayDialog = true }) { Text(stringResource(R.string.subscriptions_edit_fixed_day_change)) }
                    HintText(stringResource(R.string.subscriptions_edit_fixed_day_next_cycle))
                    if (SubscriptionFormLogic.fixedDayOverflows(form.fixedPaymentDay)) {
                        HintText(stringResource(R.string.subscriptions_edit_fixed_day_overflow, form.fixedPaymentDay))
                    }
                }
            }
            Text(stringResource(R.string.subscriptions_edit_renewal), style = MaterialTheme.typography.labelLarge)
            val renewals = buildList {
                add(RenewalType.AUTO)
                add(RenewalType.MANUAL)
                if (settings.showTrialOption || form.renewalType == RenewalType.TRIAL) add(RenewalType.TRIAL)
            }
            SegmentedTabs(renewals, selected = form.renewalType, onSelect = { r ->
                actions.update { f -> f.copy(renewalType = r, trialStartDate = if (r == RenewalType.TRIAL) f.trialStartDate ?: f.startDate else f.trialStartDate) }
            }) { stringResource(it.labelRes) }
            HintText(
                stringResource(
                    when (form.renewalType) {
                        RenewalType.AUTO -> R.string.subscriptions_edit_renewal_auto_desc
                        RenewalType.MANUAL -> R.string.subscriptions_edit_renewal_manual_desc
                        RenewalType.TRIAL -> R.string.subscriptions_edit_renewal_trial_desc
                    },
                ),
            )
            if (settings.showPaymentMethodSelector) {
                val none = stringResource(R.string.subscriptions_edit_not_set)
                SelectField(
                    label = stringResource(R.string.subscriptions_edit_payment_method),
                    value = state.options.paymentMethods.firstOrNull { it.id == form.paymentMethodId }?.name ?: none,
                    options = listOf<Pair<String?, String>>(null to none) + state.options.paymentMethods.map { it.id to it.name },
                    onSelect = { id -> actions.update { it.copy(paymentMethodId = id) } },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (state.options.wallets.isNotEmpty()) {
                WalletSelect(
                    label = stringResource(R.string.subscriptions_edit_wallet),
                    state = state,
                    selectedId = form.walletId,
                    onSelect = { id -> actions.update { it.copy(walletId = id) } },
                    problem = state.walletProblem?.toUiText()?.asString(),
                    hint = stringResource(R.string.subscriptions_edit_wallet_hint),
                )
            }
            val historyVisible = !state.isEdit && (form.kind == FormKind.REGULAR || form.kind == FormKind.BUNDLE) &&
                form.startDate < state.today && settings.showHistoricalPaymentsOption
            if (historyVisible) {
                SettingsSwitchRow(
                    title = stringResource(R.string.subscriptions_edit_generate_history),
                    subtitle = stringResource(R.string.subscriptions_edit_generate_history_desc),
                    checked = form.generateHistory,
                    onCheckedChange = { on -> actions.update { it.copy(generateHistory = on) } },
                )
            }
            if (!state.isEdit && form.kind == FormKind.STORED_VALUE) {
                Text(stringResource(R.string.subscriptions_edit_deposit_section), style = MaterialTheme.typography.titleSmall)
                MoneyInputField(
                    value = form.initialDepositText,
                    onValueChange = { v -> actions.update { it.copy(initialDepositText = v) } },
                    label = stringResource(R.string.subscriptions_edit_deposit_amount),
                    currencySymbol = state.options.currencies.firstOrNull { it.code == form.currencyCode }?.symbol,
                    allowZero = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                HintText(stringResource(R.string.subscriptions_edit_deposit_hint))
                if (state.options.wallets.isNotEmpty()) {
                    WalletSelect(
                        label = stringResource(R.string.subscriptions_edit_deposit_wallet),
                        state = state,
                        selectedId = form.depositWalletId,
                        onSelect = { id -> actions.update { it.copy(depositWalletId = id) } },
                        problem = state.depositWalletProblem?.toUiText()?.asString(),
                        hint = null,
                    )
                }
            }
        }
    }
    if (fixedDayDialog) {
        FixedDayDialog(
            initial = form.fixedPaymentDay ?: form.startDate.dayOfMonth,
            onConfirm = { day ->
                fixedDayDialog = false
                actions.update { it.copy(fixedPaymentDay = day) }
            },
            onDismiss = { fixedDayDialog = false },
        )
    }
}

@Composable
private fun LifetimeWalletCard(state: SubscriptionEditUiState, form: SubscriptionForm, actions: EditActions) {
    SectionCard(title = stringResource(R.string.subscriptions_edit_section_payment)) {
        WalletSelect(
            label = stringResource(if (state.isEdit) R.string.subscriptions_edit_wallet else R.string.subscriptions_edit_purchase_wallet),
            state = state,
            selectedId = form.walletId,
            onSelect = { id -> actions.update { it.copy(walletId = id) } },
            problem = state.walletProblem?.toUiText()?.asString(),
            hint = if (state.isEdit) null else stringResource(R.string.subscriptions_edit_purchase_wallet_hint),
        )
    }
}

@Composable
private fun WalletSelect(
    label: String,
    state: SubscriptionEditUiState,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    problem: String?,
    hint: String?,
) {
    val none = stringResource(R.string.subscriptions_edit_wallet_none)
    val wallets = state.options.wallets
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SelectField(
            label = label,
            value = wallets.firstOrNull { it.id == selectedId }?.let { "${it.name} (${it.currencyCode})" } ?: none,
            options = listOf<Pair<String?, String>>(null to none) + wallets.map { it.id to "${it.name} (${it.currencyCode})" },
            onSelect = onSelect,
            supportingText = problem ?: hint,
            isError = false,
            modifier = Modifier.fillMaxWidth(),
        )
        if (problem != null) {
            HintText(stringResource(R.string.subscriptions_edit_wallet_problem_note), color = MaterialTheme.colorScheme.tertiary)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CycleChooser(
    cycle: BillingCycle,
    countText: String,
    unit: CycleUnit,
    onCycle: (BillingCycle) -> Unit,
    onCount: (String) -> Unit,
    onUnit: (CycleUnit) -> Unit,
    countError: Boolean,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        BillingCycle.entries.forEach { c ->
            FilterChip(selected = cycle == c, onClick = { onCycle(c) }, label = { Text(stringResource(c.labelRes)) })
        }
    }
    if (cycle == BillingCycle.CUSTOM) {
        val invalid = countError || SubscriptionFormLogic.parseCount(countText) == null
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
            OutlinedTextField(
                value = countText,
                onValueChange = { v -> onCount(v.filter { it.isDigit() }.take(4)) },
                label = { Text(stringResource(R.string.subscriptions_edit_cycle_every)) },
                isError = invalid,
                supportingText = if (invalid) {
                    { Text(stringResource(R.string.subscriptions_edit_error_custom_cycle)) }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            SelectField(
                label = stringResource(R.string.subscriptions_edit_cycle_unit),
                value = stringResource(unit.labelRes),
                options = CycleUnit.entries.map { it to stringResource(it.labelRes) },
                onSelect = onUnit,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun BundleCard(state: SubscriptionEditUiState, form: SubscriptionForm, errors: List<FormProblem>, actions: EditActions) {
    SectionCard(title = stringResource(R.string.subscriptions_edit_section_bundle)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            HintText(stringResource(R.string.subscriptions_edit_bundle_hint))
            if (FormProblem.BundleNeedsChild in errors) ErrorText(stringResource(R.string.subscriptions_edit_error_bundle_child))
            val symbol = state.options.currencies.firstOrNull { it.code == form.currencyCode }?.symbol
            form.children.forEachIndexed { index, child ->
                ChildCard(index, child, symbol, state.today, errors, actions)
            }
            OutlinedButton(onClick = actions.addChild) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Text(stringResource(R.string.subscriptions_edit_bundle_add_child), modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}

@Composable
private fun ChildCard(index: Int, child: ChildForm, currencySymbol: String?, today: LocalDate, errors: List<FormProblem>, actions: EditActions) {
    val update: ((ChildForm) -> ChildForm) -> Unit = { actions.updateChild(child.key, it) }
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.subscriptions_edit_bundle_child_title, index + 1), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                IconButton(onClick = { actions.removeChild(child.key) }) {
                    Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.subscriptions_edit_bundle_remove_child, index + 1))
                }
            }
            val nameError = FormProblem.ChildBlankName(index) in errors
            OutlinedTextField(
                value = child.name,
                onValueChange = { v -> update { it.copy(name = v) } },
                label = { Text(stringResource(R.string.subscriptions_edit_name)) },
                isError = nameError,
                supportingText = if (nameError) {
                    { Text(stringResource(R.string.subscriptions_edit_error_name)) }
                } else {
                    null
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            MoneyInputField(
                value = child.priceText,
                onValueChange = { v -> update { it.copy(priceText = v) } },
                label = stringResource(R.string.subscriptions_edit_price),
                currencySymbol = currencySymbol,
                allowZero = true,
                showErrorWhenEmpty = FormProblem.ChildPrice(index) in errors,
                modifier = Modifier.fillMaxWidth(),
            )
            CycleChooser(
                cycle = child.billingCycle,
                countText = child.customCountText,
                unit = child.customUnit,
                onCycle = { c -> update { it.copy(billingCycle = c) } },
                onCount = { v -> update { it.copy(customCountText = v) } },
                onUnit = { u -> update { it.copy(customUnit = u) } },
                countError = FormProblem.ChildCustomCycle(index) in errors,
            )
            DatePickerField(
                label = stringResource(R.string.subscriptions_edit_start_date),
                date = child.startDate,
                onDateChange = { d -> update { c -> c.copy(startDate = d, endDate = c.endDate?.takeIf { it > d }) } },
                today = today,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                DatePickerField(
                    label = stringResource(R.string.subscriptions_edit_end_date_optional),
                    date = child.endDate,
                    onDateChange = { d -> update { it.copy(endDate = d) } },
                    today = today,
                    minDate = child.startDate.plusDays(1),
                    isError = FormProblem.ChildEndNotAfterStart(index) in errors,
                    modifier = Modifier.weight(1f),
                )
                if (child.endDate != null) {
                    IconButton(onClick = { update { it.copy(endDate = null) } }) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.subscriptions_edit_end_date_clear))
                    }
                }
            }
            TextButton(onClick = { actions.copyMainDates(child.key) }) {
                Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.subscriptions_edit_bundle_copy_dates), modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagsCard(state: SubscriptionEditUiState, form: SubscriptionForm, actions: EditActions) {
    var picker by rememberSaveable { mutableStateOf(false) }
    SectionCard(title = stringResource(R.string.subscriptions_edit_section_tags)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            state.options.tags.filter { it.id in form.tagIds }.forEach { tag ->
                TagChip(tag.name, tag.color, onRemove = { actions.update { f -> f.copy(tagIds = f.tagIds - tag.id) } })
            }
            AssistChip(
                onClick = { picker = true },
                label = { Text(stringResource(R.string.subscriptions_edit_tags_add)) },
                leadingIcon = { Icon(Icons.Rounded.LocalOffer, contentDescription = null, modifier = Modifier.size(18.dp)) },
            )
        }
    }
    if (picker) {
        TagPickerSheet(
            tags = state.options.tags,
            selectedIds = form.tagIds.toSet(),
            onDone = { ids ->
                picker = false
                actions.update { f -> f.copy(tagIds = f.tagIds.filter { it in ids } + ids.filter { it !in f.tagIds }) }
            },
            onDismiss = { picker = false },
            onCreate = actions.createTag,
        )
    }
}

@Composable
private fun AdditionalCard(state: SubscriptionEditUiState, form: SubscriptionForm, errors: List<FormProblem>, actions: EditActions) {
    val settings = state.options.addForm
    val showExtras = settings.showAdditionalOptionsCard
    if (!settings.showWebsiteField && !settings.showNotesField && !showExtras) return
    SectionCard(title = stringResource(R.string.subscriptions_edit_section_additional)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (settings.showWebsiteField) {
                OutlinedTextField(
                    value = form.website,
                    onValueChange = { v -> actions.update { it.copy(website = v) } },
                    label = { Text(stringResource(R.string.subscriptions_edit_website)) },
                    placeholder = { Text(stringResource(R.string.subscriptions_edit_website_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (settings.showNotesField) {
                OutlinedTextField(
                    value = form.note,
                    onValueChange = { v -> actions.update { it.copy(note = v) } },
                    label = { Text(stringResource(R.string.subscriptions_edit_note)) },
                    minLines = 3,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (showExtras) {
                val wishlist = form.kind == FormKind.WISHLIST
                SettingsSwitchRow(
                    title = stringResource(R.string.subscriptions_edit_active),
                    subtitle = stringResource(
                        when {
                            wishlist -> R.string.subscriptions_edit_active_wishlist
                            form.kind == FormKind.LIFETIME -> R.string.subscriptions_edit_active_lifetime
                            else -> R.string.subscriptions_edit_active_desc
                        },
                    ),
                    checked = form.isActive && !wishlist,
                    onCheckedChange = { on -> actions.update { it.copy(isActive = on) } },
                    enabled = !wishlist,
                )
                if (state.fields.isNotEmpty()) {
                    Text(stringResource(R.string.subscriptions_edit_custom_fields), style = MaterialTheme.typography.titleSmall)
                    state.fields.forEach { field ->
                        val id = field.definition.id
                        CustomFieldInput(
                            field = field,
                            value = form.customFieldValues[id].orEmpty(),
                            onValueChange = { v -> actions.update { f -> f.copy(customFieldValues = f.customFieldValues + (id to v)) } },
                            today = state.today,
                            zone = state.zone,
                            showErrors = state.showErrors,
                        )
                    }
                }
                PhotosSection(state.photos, actions)
            }
        }
    }
}

@Composable
private fun PhotosSection(photos: List<FormPhoto>, actions: EditActions) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val dir = remember(context) { File(context.filesDir, PhotoStorage.PHOTO_DIR) }
    Text(stringResource(R.string.subscriptions_edit_photos), style = MaterialTheme.typography.titleSmall)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(photos, key = { it.fileName }) { photo ->
            Box(Modifier.size(88.dp)) {
                AsyncImage(
                    model = File(dir, photo.fileName),
                    contentDescription = stringResource(R.string.subscriptions_edit_photo),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
                )
                Surface(
                    onClick = { actions.removePhoto(photo) },
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(28.dp),
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.subscriptions_edit_photo_remove), modifier = Modifier.padding(4.dp))
                }
            }
        }
        item {
            Surface(
                onClick = actions.addPhoto,
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.size(88.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = stringResource(R.string.subscriptions_edit_photo_add))
                }
            }
        }
    }
}
