package io.github.submark.feature.backup.ui.profiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.submark.core.model.BackupProfile
import io.github.submark.core.ui.component.EmptyState
import io.github.submark.core.ui.component.LoadingState
import io.github.submark.core.ui.component.SectionCard
import io.github.submark.core.ui.navigation.WebDavProfileRoute
import io.github.submark.core.ui.util.SnackbarEffect
import io.github.submark.feature.backup.R
import io.github.submark.feature.backup.ui.common.BackupPage
import java.text.DateFormat
import java.util.Date

@Composable
fun WebDavProfilesRoute(
    onBack: () -> Unit,
    onNavigate: (Any) -> Unit,
    viewModel: WebDavProfilesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val hostState = remember { androidx.compose.material3.SnackbarHostState() }
    SnackbarEffect(viewModel.messages, hostState)
    BackupPage(
        title = stringResource(R.string.backup_profiles_title),
        onBack = onBack,
        snackbarHostState = hostState,
        actions = {},
    ) {
        when {
            state.loading -> LoadingState()
            state.profiles.isEmpty() -> Column(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                EmptyState(
                    title = stringResource(R.string.backup_profiles_empty_title),
                    message = stringResource(R.string.backup_profiles_empty_message),
                    icon = Icons.Rounded.CloudQueue,
                    actionLabel = stringResource(R.string.backup_profiles_add),
                    onAction = { onNavigate(WebDavProfileRoute(null)) },
                )
            }
            else -> {
                state.profiles.forEach { profile ->
                    ProfileCard(
                        profile = profile,
                        onClick = { onNavigate(WebDavProfileRoute(profile.id)) },
                        onBackUpNow = { viewModel.backUpNow(profile) },
                    )
                }
                TextButton(
                    onClick = { onNavigate(WebDavProfileRoute(null)) },
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) {
                    Text(stringResource(R.string.backup_profiles_add))
                }
            }
        }
    }
}

@Composable
private fun ProfileCard(profile: BackupProfile, onClick: () -> Unit, onBackUpNow: () -> Unit) {
    SectionCard(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        onClick = onClick,
    ) {
        val dateFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(profile.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    profile.serverUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                val subtitle = when {
                    !profile.enabled -> stringResource(R.string.backup_profile_disabled)
                    profile.lastError != null -> stringResource(R.string.backup_profile_last_error, profile.lastError!!)
                    profile.lastSuccessAt != null -> stringResource(
                        R.string.backup_profile_last_success,
                        dateFormat.format(Date.from(profile.lastSuccessAt!!)),
                    )
                    else -> stringResource(R.string.backup_profile_never_backed_up)
                }
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (profile.lastError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onBackUpNow) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = stringResource(R.string.backup_back_up_now))
            }
        }
    }
}
