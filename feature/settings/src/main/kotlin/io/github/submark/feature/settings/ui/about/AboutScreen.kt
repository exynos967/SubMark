package io.github.submark.feature.settings.ui.about

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.submark.core.ui.component.SettingsGroup
import io.github.submark.core.ui.component.SettingsNavRow
import io.github.submark.core.ui.component.SettingsValueRow
import io.github.submark.core.ui.format.asString
import io.github.submark.core.ui.navigation.DocumentRoute
import io.github.submark.feature.settings.R
import io.github.submark.feature.settings.data.DocumentType
import io.github.submark.feature.settings.ui.common.SettingsPage

internal const val SOURCE_URL = "https://github.com/submark/submark"

@Composable
internal fun AboutScreenRoute(onBack: () -> Unit, onNavigate: (Any) -> Unit, viewModel: AboutViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Toasts replace each other immediately, which suits rapid taps better than queued snackbars.
    val toast = remember { arrayOfNulls<Toast>(1) }
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { msg ->
            toast[0]?.cancel()
            toast[0] = Toast.makeText(context, msg.text.asString(context), Toast.LENGTH_SHORT).also { it.show() }
        }
    }
    AboutScreen(
        state = state,
        onBack = onBack,
        onVersionTap = viewModel::onVersionTap,
        onOpenSource = { openUrl(context, SOURCE_URL) },
        onOpenDocument = { onNavigate(DocumentRoute(it.key)) },
    )
}

@Composable
internal fun AboutScreen(
    state: AboutUiState,
    onBack: () -> Unit,
    onVersionTap: () -> Unit,
    onOpenSource: () -> Unit,
    onOpenDocument: (DocumentType) -> Unit,
) {
    SettingsPage(title = stringResource(R.string.settings_about_title), onBack = onBack) {
        Column(
            Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.size(88.dp).clip(RoundedCornerShape(24.dp)), contentAlignment = Alignment.Center) {
                Image(painterResource(R.drawable.settings_icon_default_bg), null, Modifier.requiredSize(132.dp))
                Image(painterResource(R.drawable.settings_icon_default_fg), null, Modifier.requiredSize(132.dp))
            }
            Text(stringResource(R.string.settings_app_name), style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(R.string.settings_about_tagline),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        SettingsGroup {
            SettingsValueRow(
                title = stringResource(R.string.settings_about_version),
                value = state.version,
                icon = Icons.Rounded.Info,
                onClick = onVersionTap,
            )
        }
        SettingsGroup(
            title = stringResource(R.string.settings_about_open_source_group),
            footer = stringResource(R.string.settings_about_open_source_note),
        ) {
            SettingsValueRow(
                title = stringResource(R.string.settings_about_license),
                value = stringResource(R.string.settings_about_license_value),
                icon = Icons.Rounded.Gavel,
            )
            SettingsNavRow(
                title = stringResource(R.string.settings_about_source),
                subtitle = SOURCE_URL,
                icon = Icons.Rounded.Code,
                onClick = onOpenSource,
            )
        }
        SettingsGroup(
            title = stringResource(R.string.settings_about_privacy_group),
            footer = stringResource(R.string.settings_about_privacy_summary),
        ) {
            SettingsNavRow(stringResource(R.string.settings_privacy), { onOpenDocument(DocumentType.PRIVACY) }, icon = Icons.Rounded.PrivacyTip)
            SettingsNavRow(stringResource(R.string.settings_terms), { onOpenDocument(DocumentType.TERMS) }, icon = Icons.Rounded.Description)
            SettingsNavRow(stringResource(R.string.settings_faq), { onOpenDocument(DocumentType.FAQ) }, icon = Icons.AutoMirrored.Rounded.HelpOutline)
        }
    }
}

internal fun openUrl(context: Context, url: String): Boolean = try {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (_: ActivityNotFoundException) {
    Toast.makeText(context, context.getString(R.string.settings_no_app_for_link), Toast.LENGTH_SHORT).show()
    false
}
