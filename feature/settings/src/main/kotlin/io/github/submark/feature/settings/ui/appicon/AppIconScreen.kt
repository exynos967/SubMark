package io.github.submark.feature.settings.ui.appicon

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.submark.core.ui.component.ConfirmDialog
import io.github.submark.core.ui.component.SectionCard
import io.github.submark.core.ui.util.SnackbarEffect
import io.github.submark.feature.settings.R
import io.github.submark.feature.settings.data.LauncherIcon
import io.github.submark.feature.settings.ui.common.SettingsPage
import io.github.submark.feature.settings.ui.common.labelRes

@Composable
internal fun AppIconScreenRoute(onBack: () -> Unit, viewModel: AppIconViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    SnackbarEffect(viewModel.messages, snackbarHostState)
    AppIconScreen(state, snackbarHostState, onBack, viewModel::select)
}

@Composable
internal fun AppIconScreen(
    state: AppIconUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onSelect: (LauncherIcon) -> Unit,
) {
    var pending by rememberSaveable { mutableStateOf<LauncherIcon?>(null) }
    SettingsPage(
        title = stringResource(R.string.settings_app_icon_title),
        onBack = onBack,
        loading = state.selected == null,
        snackbarHostState = snackbarHostState,
    ) {
        SectionCard(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Info, null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    stringResource(R.string.settings_app_icon_note),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }
        LauncherIcon.entries.chunked(2).forEach { row ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                row.forEach { icon ->
                    IconTile(
                        icon = icon,
                        selected = state.selected == icon,
                        enabled = !state.applying,
                        onClick = { if (state.selected != icon) pending = icon },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (row.size == 1) Box(Modifier.weight(1f))
            }
        }
    }
    pending?.let { icon ->
        ConfirmDialog(
            title = stringResource(R.string.settings_app_icon_confirm_title),
            message = stringResource(R.string.settings_app_icon_confirm_message),
            confirmLabel = stringResource(R.string.settings_app_icon_apply),
            onConfirm = { onSelect(icon); pending = null },
            onDismiss = { pending = null },
        )
    }
}

@Composable
private fun IconTile(icon: LauncherIcon, selected: Boolean, enabled: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .clip(shape)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = shape,
            )
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Launchers show the middle 72 of the 108 dp adaptive-icon layers; mimic that crop.
        Box(Modifier.size(72.dp).clip(RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
            Image(painterResource(icon.background), null, Modifier.requiredSize(108.dp))
            Image(painterResource(icon.foreground), null, Modifier.requiredSize(108.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(icon.setting.labelRes), style = MaterialTheme.typography.labelLarge)
            if (selected) {
                Icon(
                    Icons.Rounded.CheckCircle,
                    contentDescription = stringResource(R.string.settings_selected),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 6.dp).size(18.dp),
                )
            }
        }
    }
}
