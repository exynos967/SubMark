package io.github.submark.feature.money.ui.wallet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.submark.core.model.Wallet
import io.github.submark.core.model.WalletKind
import io.github.submark.core.ui.component.ConfirmDialog
import io.github.submark.core.ui.component.EmptyState
import io.github.submark.core.ui.component.LoadingState
import io.github.submark.core.ui.component.SectionCard
import io.github.submark.core.ui.component.SectionHeader
import io.github.submark.core.ui.component.StatusBadge
import io.github.submark.core.ui.component.SubMarkTopAppBar
import io.github.submark.core.ui.component.formatMoney
import io.github.submark.core.ui.format.BadgeTone
import io.github.submark.core.ui.format.descriptionRes
import io.github.submark.core.ui.format.labelRes
import io.github.submark.core.ui.util.SnackbarEffect
import io.github.submark.feature.money.R
import io.github.submark.feature.money.ui.common.StatCell
import io.github.submark.feature.money.ui.common.WalletIconBadge
import io.github.submark.feature.money.ui.common.availableCredit
import io.github.submark.core.ui.R as CoreR

@Composable
fun WalletManagementRoute(
    onBack: () -> Unit,
    onOpenActivity: (String?) -> Unit,
    viewModel: WalletManagementViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val host = remember { SnackbarHostState() }
    SnackbarEffect(viewModel.snackbar, host)
    WalletManagementScreen(
        state = state,
        snackbarHost = host,
        onBack = onBack,
        onOpenActivity = onOpenActivity,
        onCreate = viewModel::openCreate,
        onEdit = viewModel::openEdit,
        onTopUp = viewModel::openTopUp,
        onDeduct = viewModel::openDeduct,
        onDeactivate = viewModel::deactivate,
        onReactivate = viewModel::reactivate,
        onDelete = viewModel::delete,
        amountActions = WalletAmountActions(viewModel::updateAmountSheet, viewModel::submitAmountSheet, viewModel::closeAmountSheet),
        formActions = WalletFormActions(viewModel::updateForm, viewModel::saveForm, viewModel::closeForm),
    )
}

class WalletAmountActions(
    val update: ((WalletAmountState) -> WalletAmountState) -> Unit,
    val submit: () -> Unit,
    val close: () -> Unit,
)

class WalletFormActions(
    val update: ((WalletFormState) -> WalletFormState) -> Unit,
    val save: () -> Unit,
    val close: () -> Unit,
)

@Composable
fun WalletManagementScreen(
    state: WalletManagementUiState,
    snackbarHost: SnackbarHostState,
    onBack: () -> Unit,
    onOpenActivity: (String?) -> Unit,
    onCreate: () -> Unit,
    onEdit: (Wallet) -> Unit,
    onTopUp: (Wallet) -> Unit,
    onDeduct: (Wallet) -> Unit,
    onDeactivate: (Wallet) -> Unit,
    onReactivate: (Wallet) -> Unit,
    onDelete: (Wallet) -> Unit,
    amountActions: WalletAmountActions,
    formActions: WalletFormActions,
) {
    var confirmDeactivate by remember { mutableStateOf<Wallet?>(null) }
    var confirmDelete by remember { mutableStateOf<Wallet?>(null) }
    Scaffold(
        topBar = {
            SubMarkTopAppBar(
                title = stringResource(R.string.money_wallet_title),
                onBack = onBack,
                actions = {
                    IconButton(onClick = { onOpenActivity(null) }) {
                        Icon(Icons.AutoMirrored.Rounded.ReceiptLong, contentDescription = stringResource(R.string.money_wallet_all_activity))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreate) {
                Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.money_wallet_create))
            }
        },
    ) { padding ->
        if (state.loading) {
            LoadingState(Modifier.padding(padding))
            return@Scaffold
        }
        val env = state.env
        val total = state.activeWallets.size + state.inactiveWallets.size
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "summary") {
                SectionCard {
                    Row {
                        StatCell(stringResource(R.string.money_wallet_count), total.toString(), Modifier.weight(1f))
                        StatCell(stringResource(R.string.money_wallet_active_count), state.activeWallets.size.toString(), Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(8.dp))
                    val trackedAssets = state.assets
                    Row {
                        StatCell(
                            stringResource(R.string.money_wallet_assets, env.defaultCode),
                            trackedAssets?.let { formatMoney(it.total, env.defaultCode, env.symbol(env.defaultCode)) } ?: "—",
                            Modifier.weight(1f),
                        )
                        StatCell(
                            stringResource(R.string.money_wallet_transaction_count),
                            state.transactionCounts.values.sum().toString(),
                            Modifier.weight(1f),
                        )
                    }
                    val missingRates = trackedAssets?.missingRateCodes?.takeIf { it.isNotEmpty() }
                    if (missingRates != null) {
                        Text(
                            stringResource(R.string.money_wallet_assets_missing, missingRates.toList().sorted().joinToString(", ")),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Text(stringResource(R.string.money_wallet_assets_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (total == 0) {
                item(key = "empty") {
                    EmptyState(
                        title = stringResource(R.string.money_wallet_empty),
                        message = stringResource(R.string.money_wallet_empty_message),
                        icon = Icons.Rounded.AccountBalanceWallet,
                    )
                }
            } else {
                items(state.activeWallets, key = { it.id }) { wallet ->
                    WalletCard(
                        wallet = wallet,
                        transactionCount = state.transactionCounts[wallet.id] ?: 0,
                        env = env,
                        onClick = { onOpenActivity(wallet.id) },
                        onTopUp = { onTopUp(wallet) },
                        onDeduct = { onDeduct(wallet) },
                        onEdit = { onEdit(wallet) },
                        onDeactivate = { confirmDeactivate = wallet },
                        onReactivate = { onReactivate(wallet) },
                        onDelete = { confirmDelete = wallet },
                    )
                }
                if (state.inactiveWallets.isNotEmpty()) {
                    item(key = "inactive_header") {
                        SectionHeader(
                            stringResource(R.string.money_wallet_inactive_section),
                            actionLabel = null,
                            onAction = null,
                        )
                        Text(
                            pluralStringResource(R.plurals.money_wallet_inactive_hint, state.inactiveWallets.size, state.inactiveWallets.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                    }
                }
                items(state.inactiveWallets, key = { it.id }) { wallet ->
                    WalletCard(
                        wallet = wallet,
                        transactionCount = state.transactionCounts[wallet.id] ?: 0,
                        env = env,
                        onClick = { onOpenActivity(wallet.id) },
                        onTopUp = null,
                        onDeduct = null,
                        onEdit = { onEdit(wallet) },
                        onDeactivate = null,
                        onReactivate = { onReactivate(wallet) },
                        onDelete = { confirmDelete = wallet },
                    )
                }
            }
        }
    }
    confirmDeactivate?.let { wallet ->
        ConfirmDialog(
            title = stringResource(R.string.money_wallet_deactivate_title),
            message = stringResource(R.string.money_wallet_deactivate_message),
            onConfirm = { confirmDeactivate = null; onDeactivate(wallet) },
            onDismiss = { confirmDeactivate = null },
            confirmLabel = stringResource(R.string.money_wallet_deactivate),
        )
    }
    confirmDelete?.let { wallet ->
        ConfirmDialog(
            title = stringResource(R.string.money_wallet_delete_title, wallet.name),
            message = stringResource(R.string.money_wallet_delete_message, formatMoney(wallet.balance, wallet.currencyCode, state.env.symbol(wallet.currencyCode))),
            onConfirm = { confirmDelete = null; onDelete(wallet) },
            onDismiss = { confirmDelete = null },
            confirmLabel = stringResource(CoreR.string.ui_action_delete),
            destructive = true,
        )
    }
    state.amountSheet?.let { WalletAmountSheet(it, state.env, amountActions.update, amountActions.submit, amountActions.close) }
    state.form?.let { WalletFormSheet(it, state.env, formActions.update, formActions.save, formActions.close) }
}

// ---------------------------------------------------------------- wallet card

@Composable
private fun WalletCard(
    wallet: Wallet,
    transactionCount: Int,
    env: io.github.submark.feature.money.data.MoneyEnv,
    onClick: () -> Unit,
    onTopUp: (() -> Unit)?,
    onDeduct: (() -> Unit)?,
    onEdit: () -> Unit,
    onDeactivate: (() -> Unit)?,
    onReactivate: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    SectionCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            WalletIconBadge(wallet, size = 44)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(wallet.name, style = MaterialTheme.typography.titleMedium)
                Text(stringResource(wallet.kind.descriptionRes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.money_more_actions_for, wallet.name))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    onTopUp?.let { action ->
                        DropdownMenuItem(text = { Text(stringResource(R.string.money_wallet_top_up)) }, onClick = { menu = false; action() })
                    }
                    onDeduct?.let { action ->
                        DropdownMenuItem(text = { Text(stringResource(R.string.money_wallet_deduct)) }, onClick = { menu = false; action() })
                    }
                    DropdownMenuItem(text = { Text(stringResource(R.string.money_wallet_activity)) }, onClick = { menu = false; onClick() })
                    DropdownMenuItem(text = { Text(stringResource(CoreR.string.ui_action_edit)) }, onClick = { menu = false; onEdit() })
                    if (onDeactivate != null) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.money_wallet_deactivate)) }, onClick = { menu = false; onDeactivate() })
                    } else {
                        DropdownMenuItem(text = { Text(stringResource(R.string.money_wallet_reactivate)) }, onClick = { menu = false; onReactivate() })
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(CoreR.string.ui_action_delete), color = MaterialTheme.colorScheme.error) },
                        onClick = { menu = false; onDelete() },
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                when (wallet.kind) {
                    WalletKind.CREDIT -> {
                        val available = wallet.availableCredit()
                        Text(stringResource(R.string.money_wallet_available_credit), style = MaterialTheme.typography.labelMedium)
                        Text(
                            available?.let { formatMoney(it, wallet.currencyCode, env.symbol(wallet.currencyCode)) } ?: stringResource(R.string.money_wallet_unlimited),
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                    else -> {
                        Text(stringResource(R.string.money_wallet_balance_label), style = MaterialTheme.typography.labelMedium)
                        Text(
                            formatMoney(wallet.balance, wallet.currencyCode, env.symbol(wallet.currencyCode)),
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                StatusBadge(stringResource(wallet.kind.labelRes), tone = if (wallet.kind == WalletKind.CREDIT && wallet.balance.signum() < 0) BadgeTone.WARNING else BadgeTone.NEUTRAL)
                if (transactionCount > 0) {
                    Text(
                        stringResource(R.string.money_wallet_txn_format, transactionCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
