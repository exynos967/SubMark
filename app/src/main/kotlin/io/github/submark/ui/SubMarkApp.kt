package io.github.submark.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import io.github.submark.MainUiState
import io.github.submark.R
import io.github.submark.StartupAlerts
import io.github.submark.core.ui.component.LoadingState
import io.github.submark.core.ui.component.LocalMoneyDisplayOptions
import io.github.submark.core.ui.component.MoneyDisplayOptions
import io.github.submark.core.ui.navigation.SubscriptionDetailRoute
import io.github.submark.core.ui.theme.SubMarkTheme
import io.github.submark.feature.settings.onboarding.OnboardingScreenRoute
import io.github.submark.feature.settings.security.AppLockScreen
import io.github.submark.feature.settings.toThemeConfig

/** Pending navigation requested by an incoming intent (notification, widget, deep link). */
sealed interface LaunchRequest {
    data class OpenSubscription(val id: String) : LaunchRequest
    data class DeepLink(val intent: android.content.Intent) : LaunchRequest
}

@Composable
fun SubMarkApp(
    state: MainUiState,
    launchRequest: LaunchRequest?,
    onLaunchRequestHandled: () -> Unit,
    onUnlocked: () -> Unit,
    onDismissAlerts: () -> Unit,
) {
    val settings = state.settings
    if (settings == null) {
        SubMarkTheme(config = io.github.submark.core.ui.theme.ThemeConfig()) { LoadingState() }
        return
    }
    SubMarkTheme(config = settings.toThemeConfig()) {
        CompositionLocalProvider(LocalMoneyDisplayOptions provides MoneyDisplayOptions(hideDecimals = settings.money.hideDecimalPlaces)) {
            when {
                !state.ready -> LoadingState()
                state.locked -> AppLockScreen(onUnlocked = onUnlocked)
                !settings.onboardingCompleted -> OnboardingScreenRoute(onFinished = {})
                else -> {
                    val navController = rememberNavController()
                    // Start tab is fixed for the lifetime of the NavHost; changing it applies on next launch.
                    val startTab = rememberSaveable { settings.startTab() }
                    SubMarkNavHost(navController, settings, startTab)
                    LaunchRequestHandler(navController, launchRequest, onLaunchRequestHandled)
                    state.alerts?.let { StartupAlertDialog(it, navController, onDismissAlerts) }
                }
            }
        }
    }
}

@Composable
private fun LaunchRequestHandler(navController: NavHostController, request: LaunchRequest?, onHandled: () -> Unit) {
    LaunchedEffect(request) {
        when (request) {
            is LaunchRequest.OpenSubscription -> navController.navigate(SubscriptionDetailRoute(request.id))
            is LaunchRequest.DeepLink -> navController.handleDeepLink(request.intent)
            null -> return@LaunchedEffect
        }
        onHandled()
    }
}

@Composable
private fun StartupAlertDialog(alerts: StartupAlerts, navController: NavHostController, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.startup_alert_title)) },
        text = {
            Column {
                if (alerts.expiredNames.isNotEmpty()) {
                    Text(
                        pluralStringResource(
                            R.plurals.startup_alert_expired,
                            alerts.expiredNames.size,
                            alerts.expiredNames.size,
                            alerts.expiredNames.joinToString(stringResource(R.string.list_separator)),
                        ),
                    )
                }
                if (alerts.endedTrials.isNotEmpty()) {
                    Text(
                        stringResource(
                            R.string.startup_alert_trials,
                            alerts.endedTrials.joinToString(stringResource(R.string.list_separator)) { it.second },
                        ),
                    )
                }
                if (alerts.walletFailureNames.isNotEmpty()) {
                    Text(
                        stringResource(
                            R.string.startup_alert_wallet,
                            alerts.walletFailureNames.joinToString(stringResource(R.string.list_separator)),
                        ),
                    )
                }
            }
        },
        confirmButton = {
            val firstTrial = alerts.endedTrials.firstOrNull()
            if (firstTrial != null) {
                TextButton(onClick = { onDismiss(); navController.navigate(SubscriptionDetailRoute(firstTrial.first)) }) {
                    Text(stringResource(R.string.startup_alert_review_trial))
                }
            } else {
                TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.ok)) }
            }
        },
        dismissButton = if (alerts.endedTrials.isNotEmpty()) {
            { TextButton(onClick = onDismiss) { Text(stringResource(R.string.startup_alert_later)) } }
        } else {
            null
        },
    )
}
