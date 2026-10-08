package io.github.submark.feature.settings.ui.onboarding

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Sell
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.submark.feature.settings.R
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreenRoute(onFinished: () -> Unit, viewModel: OnboardingViewModel = hiltViewModel()) {
    OnboardingScreen(onFinish = { viewModel.complete(onFinished) })
}

private data class Tile(@StringRes val label: Int, @StringRes val value: Int, val icon: ImageVector)

private data class OnboardingPage(
    @StringRes val title: Int,
    @StringRes val subtitle: Int,
    val tiles: List<Tile>,
    val chips: List<Int>,
)

private val pages = listOf(
    OnboardingPage(
        R.string.settings_onboarding_1_title,
        R.string.settings_onboarding_1_subtitle,
        listOf(
            Tile(R.string.settings_onboarding_demo_monthly, R.string.settings_onboarding_demo_monthly_value, Icons.Rounded.Payments),
            Tile(R.string.settings_onboarding_demo_yearly, R.string.settings_onboarding_demo_yearly_value, Icons.Rounded.CalendarMonth),
            Tile(R.string.settings_onboarding_demo_active, R.string.settings_onboarding_demo_active_value, Icons.Rounded.Subscriptions),
        ),
        listOf(R.string.settings_onboarding_chip_recurring, R.string.settings_onboarding_chip_one_time, R.string.settings_onboarding_chip_trial),
    ),
    OnboardingPage(
        R.string.settings_onboarding_2_title,
        R.string.settings_onboarding_2_subtitle,
        listOf(
            Tile(R.string.settings_onboarding_demo_next_bill, R.string.settings_onboarding_demo_next_bill_value, Icons.Rounded.Event),
            Tile(R.string.settings_onboarding_demo_due_soon, R.string.settings_onboarding_demo_due_soon_value, Icons.Rounded.NotificationsActive),
            Tile(R.string.settings_onboarding_demo_price_alert, R.string.settings_onboarding_demo_price_alert_value, Icons.Rounded.Sell),
        ),
        listOf(R.string.settings_onboarding_chip_tomorrow, R.string.settings_onboarding_chip_reminder, R.string.settings_onboarding_chip_price),
    ),
    OnboardingPage(
        R.string.settings_onboarding_3_title,
        R.string.settings_onboarding_3_subtitle,
        listOf(
            Tile(R.string.settings_onboarding_demo_wallet, R.string.settings_onboarding_demo_wallet_value, Icons.Rounded.AccountBalanceWallet),
            Tile(R.string.settings_onboarding_demo_backup, R.string.settings_onboarding_demo_backup_value, Icons.Rounded.Backup),
            Tile(R.string.settings_onboarding_demo_widget, R.string.settings_onboarding_demo_widget_value, Icons.Rounded.Widgets),
        ),
        listOf(R.string.settings_onboarding_chip_wallet, R.string.settings_onboarding_chip_backup, R.string.settings_onboarding_chip_widgets),
    ),
)

@Composable
internal fun OnboardingScreen(onFinish: () -> Unit) {
    val pagerState = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val isLast = pagerState.currentPage == pages.lastIndex
    Scaffold { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.End) {
                if (!isLast) TextButton(onClick = onFinish) { Text(stringResource(R.string.settings_onboarding_skip)) }
                else Spacer(Modifier.height(48.dp))
            }
            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { index ->
                PageContent(pages[index])
            }
            val indicatorDescription = stringResource(R.string.settings_onboarding_page, pagerState.currentPage + 1, pages.size)
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
                    .semantics { contentDescription = indicatorDescription },
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            ) {
                repeat(pages.size) { i ->
                    val selected = i == pagerState.currentPage
                    Box(
                        Modifier
                            .size(width = if (selected) 24.dp else 8.dp, height = 8.dp)
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                CircleShape,
                            ),
                    )
                }
            }
            Button(
                onClick = { if (isLast) onFinish() else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp).height(52.dp),
            ) {
                Text(stringResource(if (isLast) R.string.settings_onboarding_start else R.string.settings_onboarding_next))
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PageContent(page: OnboardingPage) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        DemoDashboard(page)
        Spacer(Modifier.height(32.dp))
        Text(stringResource(page.title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(page.subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** Illustration built from static demo values; hidden from accessibility since the text below says the same. */
@Composable
private fun DemoDashboard(page: OnboardingPage) {
    Card(
        modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth().clearAndSetSemantics { },
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            page.tiles.forEach { tile ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(tile.icon, null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        stringResource(tile.label),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                    )
                    Text(stringResource(tile.value), style = MaterialTheme.typography.titleMedium)
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                page.chips.forEach { chip -> SuggestionChip(onClick = {}, label = { Text(stringResource(chip)) }, enabled = false) }
            }
        }
    }
}
