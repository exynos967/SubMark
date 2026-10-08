package io.github.submark.feature.subscriptions.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.MoreTime
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.submark.core.data.service.ExtendBy
import io.github.submark.core.domain.BillingCalculator
import io.github.submark.core.model.MarkTiming
import io.github.submark.core.model.RenewalType
import io.github.submark.core.model.SubscriptionKind
import io.github.submark.core.model.SubscriptionPhoto
import io.github.submark.core.model.SubscriptionStatus
import io.github.submark.core.ui.component.ConfirmDialog
import io.github.submark.core.ui.component.EmptyState
import io.github.submark.core.ui.component.LoadingState
import io.github.submark.core.ui.component.MarkPaidDialog
import io.github.submark.core.ui.component.SubMarkTopAppBar
import io.github.submark.core.ui.component.formatMoney
import io.github.submark.core.ui.navigation.PriceMonitorRoute
import io.github.submark.core.ui.navigation.ShareSubscriptionRoute
import io.github.submark.core.ui.navigation.SubscriptionAnalyticsRoute
import io.github.submark.core.ui.navigation.SubscriptionEditRoute
import io.github.submark.core.ui.navigation.SubscriptionRemindersRoute
import io.github.submark.core.ui.util.SnackbarEffect
import io.github.submark.feature.subscriptions.R
import java.math.BigDecimal

/** Subscription detail destination. */
@Composable
fun SubscriptionDetailScreenRoute(onBack: () -> Unit, onNavigate: (Any) -> Unit) {
    val viewModel: SubscriptionDetailViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    val back by rememberUpdatedState(onBack)
    SnackbarEffect(viewModel.snackbar, snackbarHost)
    LaunchedEffect(viewModel) { viewModel.close.collect { back() } }
    SubscriptionDetailScreen(
        state = state,
        snackbarHost = snackbarHost,
        onBack = onBack,
        onNavigate = onNavigate,
        actions = DetailActions(
            onPause = viewModel::pause,
            onActivate = viewModel::activate,
            onMarkPaid = viewModel::markPaid,
            onResolveTrial = viewModel::resolveTrial,
            onDismissTrial = viewModel::dismissTrialPrompt,
            onOpenExtend = viewModel::openExtend,
            onCloseExtend = viewModel::closeExtend,
            onExtend = viewModel::extend,
            onCheckWallet = viewModel::checkWallet,
            onActivateWishlist = viewModel::activateWishlist,
            onRequestDelete = viewModel::requestDelete,
            onDismissDelete = viewModel::dismissDelete,
            onDelete = viewModel::delete,
            onDeletePhoto = viewModel::deletePhoto,
            onCopied = viewModel::notifyCopied,
            onCannotOpen = viewModel::notifyCannotOpen,
        ),
    )
}

/** Callbacks of [SubscriptionDetailScreen]. */
class DetailActions(
    val onPause: () -> Unit,
    val onActivate: () -> Unit,
    val onMarkPaid: (MarkTiming) -> Unit,
    val onResolveTrial: (RenewalType) -> Unit,
    val onDismissTrial: () -> Unit,
    val onOpenExtend: () -> Unit,
    val onCloseExtend: () -> Unit,
    val onExtend: (ExtendBy, BigDecimal?) -> Unit,
    val onCheckWallet: (String?) -> Unit,
    val onActivateWishlist: (Boolean, String?) -> Unit,
    val onRequestDelete: () -> Unit,
    val onDismissDelete: () -> Unit,
    val onDelete: (Boolean) -> Unit,
    val onDeletePhoto: (SubscriptionPhoto) -> Unit,
    val onCopied: () -> Unit,
    val onCannotOpen: () -> Unit,
)

private enum class PendingDialog { PAUSE, ACTIVATE, MARK_PAID, WISHLIST }

@Composable
fun SubscriptionDetailScreen(
    state: DetailUiState,
    snackbarHost: SnackbarHostState,
    onBack: () -> Unit,
    onNavigate: (Any) -> Unit,
    actions: DetailActions,
) {
    val content = state.content
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var menuOpen by remember { mutableStateOf(false) }
    var dialog by rememberSaveable { mutableStateOf<PendingDialog?>(null) }
    var viewingPhotoId by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            SubMarkTopAppBar(
                title = content?.subscription?.name ?: stringResource(R.string.subscriptions_detail_title),
                onBack = onBack,
                actions = {
                    if (content != null) {
                        val id = content.subscription.id
                        IconButton(onClick = { onNavigate(SubscriptionEditRoute(id = id)) }) {
                            Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.subscriptions_detail_action_edit))
                        }
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.subscriptions_detail_more_actions))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            MenuItem(R.string.subscriptions_detail_action_share, Icons.Rounded.Share) {
                                menuOpen = false
                                onNavigate(ShareSubscriptionRoute(id))
                            }
                            if (content.subscription.kind != SubscriptionKind.WISHLIST) {
                                MenuItem(R.string.subscriptions_detail_action_analytics, Icons.Rounded.Insights) {
                                    menuOpen = false
                                    onNavigate(SubscriptionAnalyticsRoute(id))
                                }
                            }
                            if (DetailLogic.isRecurring(content.subscription)) {
                                MenuItem(R.string.subscriptions_detail_action_reminders, Icons.Rounded.Notifications) {
                                    menuOpen = false
                                    onNavigate(SubscriptionRemindersRoute(id))
                                }
                            }
                            MenuItem(R.string.subscriptions_detail_action_delete, Icons.Rounded.Delete) {
                                menuOpen = false
                                actions.onRequestDelete()
                            }
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { padding ->
        when {
            state.loading -> LoadingState(Modifier.padding(padding))
            content == null -> EmptyState(
                title = stringResource(R.string.subscriptions_detail_not_found),
                message = stringResource(R.string.subscriptions_detail_not_found_message),
                icon = Icons.Rounded.SearchOff,
                actionLabel = stringResource(io.github.submark.core.ui.R.string.ui_action_back),
                onAction = onBack,
                modifier = Modifier.padding(padding),
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val sub = content.subscription
                item(key = "header") { HeaderSection(state, content) }
                item(key = "actions") {
                    ActionRow(
                        state = state,
                        content = content,
                        onAction = { action ->
                            when (action) {
                                QuickAction.MARK_PAID -> dialog = PendingDialog.MARK_PAID
                                QuickAction.PAUSE -> dialog = PendingDialog.PAUSE
                                QuickAction.ACTIVATE -> dialog = PendingDialog.ACTIVATE
                                QuickAction.EXTEND -> actions.onOpenExtend()
                                QuickAction.ACTIVATE_WISHLIST -> dialog = PendingDialog.WISHLIST
                                QuickAction.PRICE_MONITOR -> onNavigate(PriceMonitorRoute(sub.id))
                                QuickAction.REMINDERS -> onNavigate(SubscriptionRemindersRoute(sub.id))
                            }
                        },
                    )
                }
                item(key = "metrics") { MetricsSection(state, content) }
                if (sub.kind == SubscriptionKind.STORED_VALUE) item(key = "stored") { StoredValueSection(state, sub, onNavigate) }
                item(key = "bundle") { BundleSection(state, content, onNavigate) }
                if (sub.kind != SubscriptionKind.WISHLIST) item(key = "shared") { SharedSection(state, content, onNavigate) }
                content.wallet?.let { wallet -> item(key = "wallet") { WalletSection(state, wallet, onNavigate) } }
                if (sub.kind != SubscriptionKind.WISHLIST) item(key = "payments") { PaymentHistorySection(state, content, onNavigate) }
                item(key = "details") {
                    DetailsSection(state, content, onOpenUrl = { url -> if (!openUrl(context, url)) actions.onCannotOpen() })
                }
                item(key = "fields") { CustomFieldsSection(content.fields) }
                item(key = "note") {
                    NoteSection(sub.note) { text ->
                        clipboard.setText(AnnotatedString(text))
                        actions.onCopied()
                    }
                }
                item(key = "photos") { PhotosSection(content.photos) { viewingPhotoId = it.id } }
            }
        }
    }

    if (content == null) return
    val sub = content.subscription
    val busy = state.transient.busy

    when (dialog) {
        PendingDialog.PAUSE -> ConfirmDialog(
            title = stringResource(R.string.subscriptions_detail_pause_title, sub.name),
            message = stringResource(
                if (sub.kind == SubscriptionKind.LIFETIME) R.string.subscriptions_detail_pause_message_lifetime else R.string.subscriptions_detail_pause_message,
            ),
            onConfirm = {
                dialog = null
                actions.onPause()
            },
            onDismiss = { dialog = null },
            confirmLabel = stringResource(R.string.subscriptions_detail_action_pause),
            icon = Icons.Rounded.Pause,
        )
        PendingDialog.ACTIVATE -> {
            val next = DetailLogic.nextDateAfterActivation(sub, state.today)
            ConfirmDialog(
                title = stringResource(R.string.subscriptions_detail_activate_title, sub.name),
                message = when {
                    sub.kind == SubscriptionKind.LIFETIME -> stringResource(R.string.subscriptions_detail_activate_message_lifetime)
                    next != null -> stringResource(R.string.subscriptions_detail_activate_message_next, formatDay(next))
                    else -> stringResource(R.string.subscriptions_detail_activate_message)
                },
                onConfirm = {
                    dialog = null
                    actions.onActivate()
                },
                onDismiss = { dialog = null },
                confirmLabel = stringResource(R.string.subscriptions_detail_action_activate),
                icon = Icons.Rounded.PlayArrow,
            )
        }
        PendingDialog.MARK_PAID -> {
            val due = sub.nextPaymentDate
            if (due == null || !DetailLogic.isMarkable(sub)) {
                LaunchedEffect(Unit) { dialog = null }
            } else {
                val amount = content.shared?.userShare ?: sub.price
                MarkPaidDialog(
                    name = sub.name,
                    amountText = formatMoney(amount, sub.currencyCode, state.symbol(sub.currencyCode)),
                    dueDate = due,
                    today = state.today,
                    onConfirm = { timing ->
                        dialog = null
                        actions.onMarkPaid(timing)
                    },
                    onDismiss = { dialog = null },
                )
            }
        }
        PendingDialog.WISHLIST -> WishlistActivationDialog(
            name = sub.name,
            wallets = content.wallets,
            walletProblem = state.transient.walletProblem,
            onCheckWallet = actions.onCheckWallet,
            onConfirm = { toLifetime, walletId ->
                dialog = null
                actions.onActivateWishlist(toLifetime, walletId)
            },
            onDismiss = { dialog = null },
        )
        null -> Unit
    }

    state.transient.extendSheet?.let { sheet ->
        ExtendSheet(
            sheet = sheet,
            currencySymbol = state.symbol(sub.currencyCode),
            today = state.today,
            busy = busy,
            onExtend = actions.onExtend,
            onDismiss = actions.onCloseExtend,
        )
    }

    state.transient.deleteDialog?.let { delete ->
        DeleteDialog(delete, onDelete = actions.onDelete, onDismiss = actions.onDismissDelete)
    }

    val trialEnd = BillingCalculator.trialEndDate(sub)
    if (trialEnd != null && DetailLogic.isTrialEnded(sub, state.today) && !state.transient.trialPromptDismissed &&
        dialog == null && state.transient.deleteDialog == null && !busy
    ) {
        TrialEndedDialog(
            name = sub.name,
            trialEnd = trialEnd,
            onAuto = { actions.onResolveTrial(RenewalType.AUTO) },
            onManual = { actions.onResolveTrial(RenewalType.MANUAL) },
            onDelete = {
                actions.onDismissTrial()
                actions.onRequestDelete()
            },
            onLater = actions.onDismissTrial,
        )
    }

    viewingPhotoId?.let { id ->
        val photo = content.photos.firstOrNull { it.id == id }
        if (photo == null) {
            LaunchedEffect(id) { viewingPhotoId = null }
        } else {
            PhotoViewer(
                photo = photo,
                onDelete = {
                    viewingPhotoId = null
                    actions.onDeletePhoto(photo)
                },
                onDismiss = { viewingPhotoId = null },
            )
        }
    }
}

@Composable
private fun MenuItem(label: Int, icon: ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(stringResource(label)) }, leadingIcon = { Icon(icon, contentDescription = null) }, onClick = onClick)
}

private enum class QuickAction { MARK_PAID, PAUSE, ACTIVATE, EXTEND, ACTIVATE_WISHLIST, PRICE_MONITOR, REMINDERS }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActionRow(state: DetailUiState, content: DetailContent, onAction: (QuickAction) -> Unit) {
    val sub = content.subscription
    val enabled = !state.transient.busy
    val items = buildList {
        if (sub.kind == SubscriptionKind.WISHLIST) {
            add(Triple(QuickAction.ACTIVATE_WISHLIST, R.string.subscriptions_detail_action_activate, Icons.Rounded.RocketLaunch))
            if (!sub.appStoreId.isNullOrBlank()) add(Triple(QuickAction.PRICE_MONITOR, R.string.subscriptions_detail_action_price_monitor, Icons.AutoMirrored.Rounded.TrendingDown))
        } else {
            if (DetailLogic.isMarkable(sub)) add(Triple(QuickAction.MARK_PAID, R.string.subscriptions_detail_action_mark_paid, Icons.Rounded.CheckCircle))
            if (sub.status == SubscriptionStatus.ACTIVE) {
                add(Triple(QuickAction.PAUSE, R.string.subscriptions_detail_action_pause, Icons.Rounded.Pause))
            } else {
                add(Triple(QuickAction.ACTIVATE, R.string.subscriptions_detail_action_activate, Icons.Rounded.PlayArrow))
            }
            if (DetailLogic.canExtend(sub)) add(Triple(QuickAction.EXTEND, R.string.subscriptions_detail_action_extend, Icons.Rounded.MoreTime))
            if (DetailLogic.isRecurring(sub) && sub.status == SubscriptionStatus.ACTIVE) {
                add(Triple(QuickAction.REMINDERS, R.string.subscriptions_detail_action_reminders, Icons.Rounded.Notifications))
            }
        }
    }
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, androidx.compose.ui.Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEach { (action, label, icon) ->
            FilledTonalButton(onClick = { onAction(action) }, enabled = enabled) {
                Icon(icon, contentDescription = null)
                Text(stringResource(label), modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}
