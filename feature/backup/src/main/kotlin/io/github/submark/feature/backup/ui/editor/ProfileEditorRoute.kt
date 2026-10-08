package io.github.submark.feature.backup.ui.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.submark.core.ui.component.LoadingState
import io.github.submark.core.ui.component.SubMarkTopAppBar
import io.github.submark.core.ui.util.SnackbarEffect
import io.github.submark.core.ui.component.ConfirmDialog
import io.github.submark.feature.backup.R
import io.github.submark.feature.backup.ui.detail.ProfileDetailScreen
import io.github.submark.feature.backup.ui.detail.ProfileDetailViewModel

/**
 * WebDavProfileRoute host: editor for a new profile; for an existing one the editor is followed by
 * the detail sections (run now, remote backups, restore, recent jobs).
 */
@Composable
fun ProfileEditorRoute(
    onBack: () -> Unit,
    viewModel: ProfileEditorViewModel = hiltViewModel(),
    detailViewModel: ProfileDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val detail by detailViewModel.state.collectAsStateWithLifecycle()
    val hostState = remember { SnackbarHostState() }
    SnackbarEffect(viewModel.messages, hostState)
    SnackbarEffect(detailViewModel.messages, hostState)

    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(state.saved, state.deleted) {
        if (state.saved || state.deleted) onBack()
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            SubMarkTopAppBar(
                title = stringResource(if (state.profileId == null) R.string.backup_editor_title_new else R.string.backup_editor_title_edit),
                onBack = onBack,
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            if (!state.loading && !state.deleted) {
                FloatingActionButton(onClick = viewModel::save) {
                    Icon(Icons.Rounded.Save, contentDescription = stringResource(io.github.submark.core.ui.R.string.ui_action_save))
                }
            }
        },
        snackbarHost = { SnackbarHost(hostState) },
    ) { padding ->
        if (state.loading) {
            LoadingState(Modifier.padding(padding))
        } else {
            Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
                ProfileEditorScreen(
                    state = state,
                    callbacks = ProfileEditorCallbacks(
                        onName = viewModel::onName,
                        onServerUrl = viewModel::onServerUrl,
                        onRemotePath = viewModel::onRemotePath,
                        onUsername = viewModel::onUsername,
                        onPassword = viewModel::onPassword,
                        onAllowHttpLocal = viewModel::onAllowHttpLocal,
                        onEncrypt = viewModel::onEncrypt,
                        onEncPassword = viewModel::onEncPassword,
                        onEncPasswordConfirm = viewModel::onEncPasswordConfirm,
                        onFrequency = viewModel::onFrequency,
                        onKeepCount = viewModel::onKeepCount,
                        onKeepDays = viewModel::onKeepDays,
                        onWifiOnly = viewModel::onWifiOnly,
                        onVerifyAfterUpload = viewModel::onVerifyAfterUpload,
                        onEnabled = viewModel::onEnabled,
                        onTest = viewModel::testConnection,
                        onDismissTest = viewModel::dismissTestResults,
                        onDelete = { showDeleteConfirm = true },
                        onSave = viewModel::save,
                    ),
                )
                if (state.profileId != null) {
                    ProfileDetailScreen(
                        state = detail,
                        onBackUpNow = detailViewModel::backUpNow,
                        onRefreshRemote = detailViewModel::refreshRemote,
                        onVerifyQuick = detailViewModel::verifyQuick,
                        onVerifyFull = detailViewModel::verifyFull,
                        onDeleteRemote = detailViewModel::deleteRemote,
                        onRestore = detailViewModel::requestRestore,
                        onModeChosen = detailViewModel::modeChosen,
                        onExactConfirmed = detailViewModel::exactConfirmed,
                        onCancelDialogs = detailViewModel::cancelDialogs,
                        onPasswordEntered = detailViewModel::passwordEntered,
                    )
                }
                Spacer(Modifier.height(96.dp)) // FAB clearance
            }
        }
    }

    if (showDeleteConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.backup_editor_delete_profile_title),
            message = stringResource(R.string.backup_editor_delete_profile_body),
            confirmLabel = stringResource(R.string.backup_editor_delete_keep_files),
            destructive = true,
            onConfirm = { showDeleteConfirm = false; viewModel.deleteProfile() },
            onDismiss = { showDeleteConfirm = false },
        )
    }
}
