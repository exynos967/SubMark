package io.github.submark.feature.settings.ui.root

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DeveloperMode
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.StarRate
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.ViewQuilt
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material.icons.rounded.TrendingDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.submark.core.ui.component.SettingsGroup
import io.github.submark.core.ui.component.SettingsNavRow
import io.github.submark.core.ui.navigation.AboutRoute
import io.github.submark.core.ui.navigation.AiSettingsRoute
import io.github.submark.core.ui.navigation.AppearanceSettingsRoute
import io.github.submark.core.ui.navigation.CategoryManagementRoute
import io.github.submark.core.ui.navigation.CustomFieldManagementRoute
import io.github.submark.core.ui.navigation.CustomizationRoute
import io.github.submark.core.ui.navigation.DataManagementRoute
import io.github.submark.core.ui.navigation.DeveloperOptionsRoute
import io.github.submark.core.ui.navigation.DocumentRoute
import io.github.submark.core.ui.navigation.InterfaceSettingsRoute
import io.github.submark.core.ui.navigation.NotificationSettingsRoute
import io.github.submark.core.ui.navigation.PaymentMethodManagementRoute
import io.github.submark.core.ui.navigation.PopularRepositoriesRoute
import io.github.submark.core.ui.navigation.PriceMonitorSettingsRoute
import io.github.submark.core.ui.navigation.RawgSettingsRoute
import io.github.submark.core.ui.navigation.RegionCurrencySettingsRoute
import io.github.submark.core.ui.navigation.SecuritySettingsRoute
import io.github.submark.core.ui.navigation.TagManagementRoute
import io.github.submark.core.ui.navigation.WalletManagementRoute
import io.github.submark.feature.settings.R
import io.github.submark.feature.settings.data.AppLocales
import io.github.submark.feature.settings.data.DocumentType
import io.github.submark.feature.settings.ui.about.SOURCE_URL
import io.github.submark.feature.settings.ui.about.openUrl
import io.github.submark.feature.settings.ui.common.SettingsPage
import io.github.submark.feature.settings.ui.common.labelRes

@Composable
internal fun SettingsRootScreenRoute(onNavigate: (Any) -> Unit, viewModel: SettingsRootViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    SettingsRootScreen(
        state = state,
        onNavigate = onNavigate,
        onRate = { rateApp(context) },
        onShare = { shareApp(context) },
    )
}

@Composable
internal fun SettingsRootScreen(
    state: SettingsRootUiState,
    onNavigate: (Any) -> Unit,
    onRate: () -> Unit,
    onShare: () -> Unit,
) {
    val on = stringResource(R.string.settings_on)
    val off = stringResource(R.string.settings_off)
    val language = remember(state.language) { AppLocales.current() } ?: state.language
    SettingsPage(title = stringResource(R.string.settings_title), onBack = null, loading = state.loading) {
        SettingsGroup(title = stringResource(R.string.settings_group_general)) {
            SettingsNavRow(
                title = stringResource(R.string.settings_appearance_title),
                subtitle = stringResource(R.string.settings_appearance_subtitle),
                value = stringResource(state.theme.labelRes),
                icon = Icons.Rounded.Palette,
                onClick = { onNavigate(AppearanceSettingsRoute) },
            )
            SettingsNavRow(
                title = stringResource(R.string.settings_language),
                value = stringResource(language.labelRes),
                icon = Icons.Rounded.Language,
                onClick = { onNavigate(AppearanceSettingsRoute) },
            )
            SettingsNavRow(
                title = stringResource(R.string.settings_interface_title),
                subtitle = stringResource(R.string.settings_interface_subtitle),
                icon = Icons.Rounded.ViewQuilt,
                onClick = { onNavigate(InterfaceSettingsRoute) },
            )
            SettingsNavRow(
                title = stringResource(R.string.settings_customization_title),
                subtitle = stringResource(R.string.settings_customization_subtitle),
                icon = Icons.Rounded.Widgets,
                onClick = { onNavigate(CustomizationRoute) },
            )
            SettingsNavRow(
                title = stringResource(R.string.settings_region_title),
                value = state.defaultCurrency,
                icon = Icons.Rounded.Storefront,
                onClick = { onNavigate(RegionCurrencySettingsRoute) },
            )
        }
        SettingsGroup(title = stringResource(R.string.settings_group_organize)) {
            SettingsNavRow(stringResource(R.string.settings_manage_categories), { onNavigate(CategoryManagementRoute) }, icon = Icons.Rounded.Category)
            SettingsNavRow(stringResource(R.string.settings_manage_tags), { onNavigate(TagManagementRoute) }, icon = Icons.AutoMirrored.Rounded.Label)
            SettingsNavRow(stringResource(R.string.settings_manage_custom_fields), { onNavigate(CustomFieldManagementRoute) }, icon = Icons.Rounded.Tune)
            SettingsNavRow(stringResource(R.string.settings_payment_methods), { onNavigate(PaymentMethodManagementRoute) }, icon = Icons.Rounded.CreditCard)
            SettingsNavRow(stringResource(R.string.settings_wallets), { onNavigate(WalletManagementRoute) }, icon = Icons.Rounded.AccountBalanceWallet)
        }
        SettingsGroup(title = stringResource(R.string.settings_group_reminders_security)) {
            SettingsNavRow(
                title = stringResource(R.string.settings_notifications),
                value = if (state.notificationsEnabled) on else off,
                icon = Icons.Rounded.Notifications,
                onClick = { onNavigate(NotificationSettingsRoute) },
            )
            SettingsNavRow(
                title = stringResource(R.string.settings_security_title),
                value = if (state.appLockEnabled) on else off,
                icon = Icons.Rounded.Lock,
                onClick = { onNavigate(SecuritySettingsRoute) },
            )
            SettingsNavRow(
                title = stringResource(R.string.settings_data_management),
                subtitle = stringResource(R.string.settings_data_management_subtitle),
                icon = Icons.Rounded.Storage,
                onClick = { onNavigate(DataManagementRoute) },
            )
        }
        SettingsGroup(title = stringResource(R.string.settings_group_integrations)) {
            SettingsNavRow(stringResource(R.string.settings_price_monitoring), { onNavigate(PriceMonitorSettingsRoute) }, icon = Icons.Rounded.TrendingDown)
            SettingsNavRow(stringResource(R.string.settings_ai_recognition), { onNavigate(AiSettingsRoute) }, icon = Icons.Rounded.AutoAwesome)
            SettingsNavRow(stringResource(R.string.settings_rawg), { onNavigate(RawgSettingsRoute) }, icon = Icons.Rounded.SportsEsports)
            SettingsNavRow(stringResource(R.string.settings_popular_repositories), { onNavigate(PopularRepositoriesRoute) }, icon = Icons.Rounded.Storefront)
        }
        SettingsGroup(title = stringResource(R.string.settings_group_support)) {
            SettingsNavRow(stringResource(R.string.settings_faq), { onNavigate(DocumentRoute(DocumentType.FAQ.key)) }, icon = Icons.AutoMirrored.Rounded.HelpOutline)
            SettingsNavRow(stringResource(R.string.settings_privacy), { onNavigate(DocumentRoute(DocumentType.PRIVACY.key)) }, icon = Icons.Rounded.PrivacyTip)
            SettingsNavRow(stringResource(R.string.settings_terms), { onNavigate(DocumentRoute(DocumentType.TERMS.key)) }, icon = Icons.Rounded.Description)
            SettingsNavRow(stringResource(R.string.settings_rate), onRate, icon = Icons.Rounded.StarRate)
            SettingsNavRow(stringResource(R.string.settings_share_app), onShare, icon = Icons.Rounded.Share)
            SettingsNavRow(stringResource(R.string.settings_about_title), { onNavigate(AboutRoute) }, icon = Icons.Rounded.Info)
        }
        if (state.developerOptionsUnlocked) {
            SettingsGroup {
                SettingsNavRow(stringResource(R.string.settings_dev_title), { onNavigate(DeveloperOptionsRoute) }, icon = Icons.Rounded.DeveloperMode)
            }
        }
    }
}

private fun rateApp(context: Context) {
    val pkg = context.packageName
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        openUrl(context, "https://play.google.com/store/apps/details?id=$pkg")
    }
}

private fun shareApp(context: Context) {
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, context.getString(R.string.settings_share_text, SOURCE_URL))
    context.startActivity(
        Intent.createChooser(send, context.getString(R.string.settings_share_app)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}
