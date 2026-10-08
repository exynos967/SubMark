package io.github.submark.feature.analytics.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.submark.core.ui.component.ErrorState
import io.github.submark.core.ui.component.LoadingState
import io.github.submark.core.ui.component.SectionCard
import io.github.submark.core.ui.component.SubMarkTopAppBar
import io.github.submark.core.ui.component.formatMoney
import io.github.submark.core.ui.format.DateLabels
import io.github.submark.core.ui.format.asString
import io.github.submark.feature.analytics.R
import java.math.BigDecimal
import java.math.MathContext

@Composable
fun SubscriptionAnalyticsRoute(
    onBack: () -> Unit,
    viewModel: SubscriptionAnalyticsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    SubscriptionAnalyticsScreen(uiState = uiState, onBack = onBack)
}

@Composable
fun SubscriptionAnalyticsScreen(
    uiState: SubscriptionAnalyticsUiState,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            SubMarkTopAppBar(
                title = uiState.subscription?.name ?: stringResource(R.string.analytics_detail_title),
                subtitle = stringResource(R.string.analytics_title),
                onBack = onBack,
            )
        },
    ) { padding ->
        when {
            uiState.loading -> LoadingState(Modifier.padding(padding))
            uiState.notFound || uiState.subscription == null -> ErrorState(
                modifier = Modifier.padding(padding),
                message = stringResource(R.string.analytics_detail_not_found),
                onRetry = null,
            )
            else -> {
                val sub = uiState.subscription
                val code = sub.currencyCode
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // Lifecycle
                    item {
                        SectionCard(title = stringResource(R.string.analytics_detail_lifecycle_title)) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                val days = uiState.lifecycle?.subscribedDays ?: uiState.daysOwned
                                DetailRow(
                                    stringResource(R.string.analytics_detail_subscribed_duration),
                                    if (days == 0L) stringResource(R.string.analytics_detail_subscribed_today)
                                    else DateLabels.duration(sub.startDate, java.time.LocalDate.now()).asString(),
                                )
                                if (uiState.isLifetime) {
                                    DetailRow(
                                        stringResource(R.string.analytics_detail_days_owned),
                                        uiState.daysOwned.toString(),
                                    )
                                } else {
                                    uiState.lifecycle?.let { life ->
                                        DetailRow(
                                            stringResource(R.string.analytics_detail_completed_cycles),
                                            life.completedCycles.toString(),
                                        )
                                        life.daysToNext?.let {
                                            DetailRow(stringResource(R.string.analytics_detail_days_to_next), it.toString())
                                        }
                                    }
                                    sub.nextPaymentDate?.let {
                                        DetailRow(
                                            stringResource(R.string.analytics_detail_next_billing),
                                            DateLabels.formatDate(it),
                                        )
                                    }
                                    if (uiState.trialActive) {
                                        DetailRow(
                                            stringResource(R.string.analytics_detail_trial_active),
                                            uiState.trialEndDate?.let { DateLabels.formatDate(it) }.orEmpty(),
                                        )
                                    }
                                    if (uiState.paused) {
                                        Text(
                                            stringResource(R.string.analytics_detail_paused),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (!uiState.isLifetime) {
                        item {
                            SectionCard(title = stringResource(R.string.analytics_detail_payment_trend_title)) {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    val life = uiState.lifecycle
                                    if (life == null || life.completedCycles == 0) {
                                        Text(
                                            stringResource(R.string.analytics_detail_no_payments),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    } else {
                                        life.highest?.let {
                                            DetailRow(stringResource(R.string.analytics_detail_highest), formatMoney(it, code))
                                        }
                                        life.lowest?.let {
                                            DetailRow(stringResource(R.string.analytics_detail_lowest), formatMoney(it, code))
                                        }
                                        life.averagePayment?.let {
                                            DetailRow(stringResource(R.string.analytics_detail_average), formatMoney(it, code))
                                        }
                                        life.lastChange?.let {
                                            DetailRow(
                                                stringResource(R.string.analytics_detail_last_change),
                                                formatMoney(it, code, showPlusSign = true),
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            SectionCard(title = stringResource(R.string.analytics_detail_actual_cost_title)) {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    uiState.lifecycle?.let { life ->
                                        DetailRow(
                                            stringResource(R.string.analytics_detail_actual_daily),
                                            formatMoney(life.actualDaily, code),
                                        )
                                        DetailRow(
                                            stringResource(R.string.analytics_detail_actual_monthly),
                                            formatMoney(life.actualMonthly, code),
                                        )
                                        if (uiState.isShared && uiState.memberCount > 1) {
                                            DetailRow(
                                                stringResource(R.string.analytics_detail_per_person_daily),
                                                formatMoney(life.actualDaily, code),
                                            )
                                            DetailRow(
                                                stringResource(R.string.analytics_detail_per_person_monthly),
                                                formatMoney(life.actualMonthly, code),
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            SectionCard(title = stringResource(R.string.analytics_detail_theory_vs_actual_title)) {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    uiState.lifecycle?.let { life ->
                                        DetailRow(
                                            stringResource(R.string.analytics_detail_theoretical),
                                            formatMoney(life.theoryTotal, code),
                                        )
                                        DetailRow(
                                            stringResource(R.string.analytics_detail_actual),
                                            formatMoney(life.actualTotal, code),
                                        )
                                    }
                                }
                            }
                        }

                        item {
                            SectionCard(title = stringResource(R.string.analytics_detail_projected_title)) {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    uiState.projectedCurrentCycle?.let {
                                        DetailRow(stringResource(R.string.analytics_detail_current_cycle), formatMoney(it, code))
                                    }
                                    uiState.projectedWeekly?.let {
                                        DetailRow(stringResource(R.string.analytics_detail_weekly), formatMoney(it, code))
                                    }
                                    uiState.projectedMonthly?.let {
                                        DetailRow(stringResource(R.string.analytics_detail_monthly), formatMoney(it, code))
                                    }
                                    uiState.projectedQuarterly?.let {
                                        DetailRow(stringResource(R.string.analytics_detail_quarterly), formatMoney(it, code))
                                    }
                                    uiState.projectedSemiAnnual?.let {
                                        DetailRow(stringResource(R.string.analytics_detail_semiannual), formatMoney(it, code))
                                    }
                                    uiState.projectedAnnual?.let {
                                        DetailRow(stringResource(R.string.analytics_detail_annual), formatMoney(it, code))
                                        if (uiState.isShared && uiState.memberCount > 1) {
                                            DetailRow(
                                                "${stringResource(R.string.analytics_detail_monthly)} · ${stringResource(R.string.analytics_detail_per_person)}",
                                                formatMoney(
                                                    (uiState.projectedMonthly ?: BigDecimal.ZERO)
                                                        .divide(BigDecimal(uiState.memberCount), MathContext.DECIMAL64),
                                                    code,
                                                ),
                                            )
                                            DetailRow(
                                                "${stringResource(R.string.analytics_detail_annual)} · ${stringResource(R.string.analytics_detail_per_person)}",
                                                formatMoney(
                                                    it.divide(BigDecimal(uiState.memberCount), MathContext.DECIMAL64),
                                                    code,
                                                ),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        item {
                            SectionCard(title = stringResource(R.string.analytics_detail_holding_cost_title)) {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    uiState.dailyHoldingCost?.let {
                                        DetailRow(
                                            stringResource(R.string.analytics_detail_daily_holding_cost),
                                            formatMoney(it, code),
                                        )
                                    }
                                    DetailRow(
                                        stringResource(R.string.analytics_detail_days_owned),
                                        uiState.daysOwned.toString(),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
