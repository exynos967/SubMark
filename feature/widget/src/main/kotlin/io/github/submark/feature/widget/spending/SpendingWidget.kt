package io.github.submark.feature.widget.spending

import android.content.Context
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import io.github.submark.core.ui.format.MoneyFormatter
import io.github.submark.feature.widget.R
import io.github.submark.feature.widget.SpendingSummaryState
import io.github.submark.feature.widget.WidgetLoader
import io.github.submark.feature.widget.openAppIntent
import java.math.BigDecimal
import java.util.Locale

class SpendingWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(SMALL, MEDIUM))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = runCatching { WidgetLoader(context).spendingSummary() }.getOrNull()
        provideContent {
            GlanceTheme {
                if (state == null) {
                    Box(
                        GlanceModifier.fillMaxSize().background(GlanceTheme.colors.background),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(context.getString(R.string.widget_error), style = TextStyle(color = GlanceTheme.colors.error))
                    }
                } else {
                    Content(context, state)
                }
            }
        }
    }

    companion object {
        private val SMALL = DpSize(150.dp, 110.dp)
        private val MEDIUM = DpSize(245.dp, 110.dp)
    }
}

@androidx.compose.runtime.Composable
private fun Content(context: Context, state: SpendingSummaryState) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.background)
            .padding(12.dp)
            .clickable(actionStartActivity(openAppIntent(context, null))),
    ) {
        Text(
            context.getString(R.string.widget_spending_title),
            style = TextStyle(fontWeight = FontWeight.Bold, color = GlanceTheme.colors.onBackground),
            maxLines = 1,
        )
        Spacer(GlanceModifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(GlanceModifier.defaultWeight()) {
                Text(
                    context.getString(R.string.widget_this_month),
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant),
                    maxLines = 1,
                )
                Text(
                    state.projectedThisMonth?.let { MoneyFormatter.format(it, "", locale = Locale.getDefault()) }
                        ?: context.getString(R.string.widget_unknown_date),
                    style = TextStyle(fontWeight = FontWeight.Bold, color = GlanceTheme.colors.primary),
                    maxLines = 1,
                )
            }
            Spacer(GlanceModifier.width(8.dp))
            Column {
                Text(
                    context.getString(R.string.widget_active_count),
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant),
                    maxLines = 1,
                )
                Text(
                    state.activeCount.toString(),
                    style = TextStyle(fontWeight = FontWeight.Bold, color = GlanceTheme.colors.onBackground),
                    maxLines = 1,
                )
            }
        }
        Spacer(GlanceModifier.height(6.dp))
        val budgetText = when {
            state.budgetUsage == null -> context.getString(R.string.widget_no_budget)
            state.overBudget -> context.getString(R.string.widget_over_budget)
            else -> context.getString(
                R.string.widget_budget_used,
                state.budgetUsage.multiply(BigDecimal(100)).toInt(),
            )
        }
        Text(
            budgetText,
            style = TextStyle(
                color = if (state.overBudget) GlanceTheme.colors.error else GlanceTheme.colors.onSurfaceVariant,
            ),
            maxLines = 1,
        )
    }
}

class SpendingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SpendingWidget()
}
