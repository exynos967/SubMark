package io.github.submark.feature.widget.upcoming

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
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
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.LocalSize
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import io.github.submark.core.data.service.SubscriptionService
import io.github.submark.core.model.PaymentSource
import io.github.submark.feature.widget.DueLabel
import io.github.submark.feature.widget.R
import io.github.submark.feature.widget.UpcomingRow
import io.github.submark.feature.widget.UpcomingWidgetState
import io.github.submark.feature.widget.WidgetLoader
import io.github.submark.feature.widget.openAppIntent
import io.github.submark.core.ui.format.MoneyFormatter
import java.util.Locale

class UpcomingWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(SMALL, MEDIUM, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = runCatching { WidgetLoader(context).upcoming(limit = MAX_ROWS) }.getOrNull()
        provideContent {
            GlanceTheme {
                val width = LocalSize.current.width
                val compact = width < MEDIUM_WIDTH
                val showMarkPaid = width >= MARK_PAID_MIN_WIDTH
                if (state == null) {
                    ErrorContent(context)
                } else {
                    Content(context, state, compact, showMarkPaid)
                }
            }
        }
    }

    companion object {
        const val MAX_ROWS = 10
    }
}

private val SMALL = DpSize(110.dp, 110.dp)
private val MEDIUM = DpSize(245.dp, 170.dp)
private val LARGE = DpSize(330.dp, 300.dp)
private val MEDIUM_WIDTH = 200.dp
private val MARK_PAID_MIN_WIDTH = 245.dp
private const val COMPACT_ROWS = 3
private const val MEDIUM_ROWS = 6

@androidx.compose.runtime.Composable
private fun Content(context: Context, state: UpcomingWidgetState, compact: Boolean, showMarkPaid: Boolean) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.background)
            .padding(12.dp)
            .clickable(actionStartActivity(openAppIntent(context, null))),
    ) {
        // Header: title + total + count
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(GlanceModifier.defaultWeight()) {
                Text(
                    context.getString(R.string.widget_upcoming_title),
                    style = TextStyle(fontWeight = FontWeight.Bold, color = GlanceTheme.colors.onBackground),
                    maxLines = 1,
                )
                Text(
                    context.resources.getQuantityString(R.plurals.widget_subs_count, state.subscriptionCount, state.subscriptionCount),
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant),
                    maxLines = 1,
                )
            }
            state.total?.let {
                Text(
                    MoneyFormatter.format(it, "", locale = Locale.getDefault()),
                    style = TextStyle(fontWeight = FontWeight.Bold, color = GlanceTheme.colors.primary),
                    maxLines = 1,
                )
            }
        }
        Spacer(GlanceModifier.height(8.dp))
        if (state.rows.isEmpty()) {
            Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(context.getString(R.string.widget_no_upcoming), style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant))
                    Text(context.getString(R.string.widget_add_hint), style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant))
                }
            }
        } else {
            val limit = if (compact) COMPACT_ROWS else MEDIUM_ROWS
            LazyColumn(GlanceModifier.fillMaxSize()) {
                items(state.rows.take(limit), itemId = { it.id.hashCode().toLong() }) { row ->
                    UpcomingRowView(context, row, showMarkPaid)
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun UpcomingRowView(context: Context, row: UpcomingRow, showMarkPaid: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(actionStartActivity(openAppIntent(context, row.id))),
    ) {
        // Icon fallback: monogram letter, no network images in widgets.
        Box(
            contentAlignment = Alignment.Center,
            modifier = GlanceModifier
                .size(32.dp)
                .background(GlanceTheme.colors.primaryContainer),
        ) {
            Text(
                row.fallbackLetter,
                style = TextStyle(fontWeight = FontWeight.Bold, color = GlanceTheme.colors.onPrimaryContainer),
            )
        }
        Spacer(GlanceModifier.width(8.dp))
        Column(GlanceModifier.defaultWeight()) {
            Text(row.name, style = TextStyle(fontWeight = FontWeight.Medium, color = GlanceTheme.colors.onBackground), maxLines = 1)
            Text(
                dueLabelText(context, row.dueLabel),
                style = TextStyle(
                    color = if (row.overdue) GlanceTheme.colors.error else GlanceTheme.colors.onSurfaceVariant,
                ),
                maxLines = 1,
            )
        }
        Text(
            MoneyFormatter.format(row.amount, row.currencyCode, locale = Locale.getDefault()),
            style = TextStyle(color = GlanceTheme.colors.onBackground),
            maxLines = 1,
        )
        if (showMarkPaid) {
            Spacer(GlanceModifier.width(8.dp))
            Box(
                contentAlignment = Alignment.Center,
                modifier = GlanceModifier
                    .background(GlanceTheme.colors.primary)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .clickable(
                        actionRunCallback<MarkPaidCallback>(
                            actionParametersOf(SubscriptionIdKey to row.id, SubscriptionNameKey to row.name),
                        ),
                    ),
            ) {
                Text(
                    context.getString(R.string.widget_mark_paid),
                    style = TextStyle(color = GlanceTheme.colors.onPrimary, fontWeight = FontWeight.Medium),
                    maxLines = 1,
                )
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun ErrorContent(context: Context) {
    Box(GlanceModifier.fillMaxSize().background(GlanceTheme.colors.background), contentAlignment = Alignment.Center) {
        Text(context.getString(R.string.widget_error), style = TextStyle(color = GlanceTheme.colors.error))
    }
}

private fun dueLabelText(context: Context, label: DueLabel): String = when (label) {
    DueLabel.Unknown -> context.getString(R.string.widget_unknown_date)
    DueLabel.Today -> context.getString(R.string.widget_today)
    DueLabel.Tomorrow -> context.getString(R.string.widget_tomorrow)
    is DueLabel.InDays -> context.resources.getQuantityString(R.plurals.widget_in_days, label.days, label.days)
    is DueLabel.Overdue -> context.resources.getQuantityString(R.plurals.widget_overdue_days, label.days, label.days)
}

val SubscriptionIdKey = ActionParameters.Key<String>("subscriptionId")
val SubscriptionNameKey = ActionParameters.Key<String>("subscriptionName")

/** "Mark paid" from the widget (payment source WIDGET). */
class MarkPaidCallback : ActionCallback {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface MarkPaidEntryPoint {
        fun subscriptionService(): SubscriptionService
    }

    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[SubscriptionIdKey] ?: return
        val name = parameters[SubscriptionNameKey].orEmpty()
        val service = EntryPointAccessors.fromApplication(context, MarkPaidEntryPoint::class.java).subscriptionService()
        val result = service.markPaid(id, newCycle = false, source = PaymentSource.WIDGET)
        val textRes = if (result.isSuccess) R.string.widget_marked_paid else R.string.widget_mark_failed
        android.widget.Toast.makeText(context, context.getString(textRes, name), android.widget.Toast.LENGTH_SHORT).show()
        // The change listener triggers a refresh; update eagerly too.
        UpcomingWidget().update(context, glanceId)
    }
}

class UpcomingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = UpcomingWidget()
}
