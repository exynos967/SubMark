package io.github.submark.feature.share.ui.report

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.submark.core.data.settings.PosterDisplayMode
import io.github.submark.core.data.settings.PosterStyle
import io.github.submark.core.model.PaymentStatus
import io.github.submark.core.ui.format.DateLabels
import io.github.submark.feature.share.R
import io.github.submark.feature.share.ui.poster.FallbackAccent
import io.github.submark.feature.share.ui.poster.posterPalette
import java.time.format.DateTimeFormatter

private const val MAX_SIMPLE_ROWS = 12
private const val MAX_DETAIL_ITEMS_PER_MONTH = 6
private const val MAX_DETAIL_MONTHS = 4

/** Static report poster content; rendered both on screen (preview) and into a Bitmap. */
@Composable
fun FinancialReportPoster(
    state: ReportUiState,
    modifier: Modifier = Modifier,
) {
    val palette = posterPalette(state.style, FallbackAccent)
    val symbol = state.currencySymbols[state.defaultCurrencyCode]
    val mask = stringResource(R.string.report_mask)
    val locale = io.github.submark.core.ui.component.currentLocale()

    val money: (java.math.BigDecimal) -> String = { value ->
        if (state.hideAmounts) mask
        else io.github.submark.core.ui.format.MoneyFormatter.format(
            amount = value,
            currencyCode = state.defaultCurrencyCode,
            symbol = symbol,
            locale = locale,
        )
    }
    val name: (String) -> String = { id ->
        if (state.hideNames) mask else state.subscriptionNames[id] ?: mask
    }

    Column(
        modifier = modifier
            .background(palette.background)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Header
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            if (state.showLogo) {
                Text(
                    stringResource(R.string.share_footer),
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.onBackground.copy(alpha = 0.6f),
                )
            }
            Text(
                stringResource(R.string.report_poster_heading),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = palette.onBackground,
                textAlign = TextAlign.Center,
            )
            val start = state.periodStart
            val end = state.periodEnd
            if (start != null && end != null) {
                Text(
                    "${DateLabels.formatDate(start)} — ${DateLabels.formatDate(end)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.onBackground.copy(alpha = 0.7f),
                )
            }
            if (state.showGeneratedDate) {
                state.generatedDate?.let {
                    Text(
                        DateLabels.formatDate(it),
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.onBackground.copy(alpha = 0.5f),
                    )
                }
            }
        }

        // Statistics
        if (state.showStatistics) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(palette.card)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                StatCell(stringResource(R.string.report_total_expenses), money(state.total), palette.onBackground, Modifier.weight(1f))
                StatCell(stringResource(R.string.report_expense_months), state.expenseMonths.toString(), palette.onBackground, Modifier.weight(1f))
                StatCell(stringResource(R.string.report_payment_count), state.paymentCount.toString(), palette.onBackground, Modifier.weight(1f))
                StatCell(stringResource(R.string.report_average_payment), money(state.averagePayment), palette.onBackground, Modifier.weight(1f))
            }
        }

        // Details
        if (state.months.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(palette.card)
                    .padding(20.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.report_no_payments),
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.onBackground.copy(alpha = 0.7f),
                )
            }
        } else if (state.displayMode == PosterDisplayMode.SIMPLE) {
            SimpleTable(state, money = money, name = name, palette = palette, mask = mask)
        } else {
            DetailedList(state, money = money, name = name, palette = palette)
        }
    }
}

@Composable
private fun StatCell(label: String, value: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SimpleTable(
    state: ReportUiState,
    money: (java.math.BigDecimal) -> String,
    name: (String) -> String,
    palette: io.github.submark.feature.share.ui.poster.PosterPalette,
    mask: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(palette.card)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(modifier = Modifier.padding(vertical = 8.dp)) {
            PosterText(stringResource(R.string.report_col_time), palette, Modifier.weight(1.2f), bold = true)
            PosterText(stringResource(R.string.report_col_item), palette, Modifier.weight(2f), bold = true)
            PosterText(stringResource(R.string.report_col_amount), palette, Modifier.weight(1.2f), bold = true)
        }
        val rows = state.months.flatMap { m -> m.payments.map { it to m.yearMonth } }
        rows.take(MAX_SIMPLE_ROWS).forEach { (p, _) ->
            Row(modifier = Modifier.padding(vertical = 4.dp)) {
                PosterText(
                    if (state.hidePaymentDetails) mask else DateLabels.formatMonthDay(p.paymentDate),
                    palette,
                    Modifier.weight(1.2f),
                )
                PosterText(name(p.subscriptionId), palette, Modifier.weight(2f))
                PosterText(money(p.amount), palette, Modifier.weight(1.2f))
            }
        }
        if (rows.size > MAX_SIMPLE_ROWS) {
            PosterText(
                pluralStringResource(R.plurals.report_more_items, rows.size - MAX_SIMPLE_ROWS, rows.size - MAX_SIMPLE_ROWS),
                palette,
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
            )
        }
        Row(modifier = Modifier.padding(vertical = 8.dp)) {
            PosterText(stringResource(R.string.report_total), palette, Modifier.weight(1.2f), bold = true)
            PosterText("", palette, Modifier.weight(2f))
            PosterText(money(state.total), palette, Modifier.weight(1.2f), bold = true)
        }
    }
}

@Composable
private fun DetailedList(
    state: ReportUiState,
    money: (java.math.BigDecimal) -> String,
    name: (String) -> String,
    palette: io.github.submark.feature.share.ui.poster.PosterPalette,
) {
    state.months.take(MAX_DETAIL_MONTHS).forEach { month ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(palette.card)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                month.yearMonth.format(DateTimeFormatter.ofPattern("yyyy-MM")),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = palette.onBackground,
            )
            month.payments.take(MAX_DETAIL_ITEMS_PER_MONTH).forEach { p ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        PosterText(name(p.subscriptionId), palette, Modifier.fillMaxWidth())
                        if (!state.hidePaymentDetails) {
                            PosterText(
                                DateLabels.formatMonthDay(p.paymentDate) + " · " + stringResource(
                                    if (p.status == PaymentStatus.SUCCESS) R.string.report_status_success else R.string.report_status_pending,
                                ),
                                palette,
                                Modifier.fillMaxWidth(),
                                small = true,
                            )
                        }
                        val note = p.note
                        if (!state.hideNotes && !note.isNullOrBlank()) {
                            PosterText(note, palette, Modifier.fillMaxWidth(), small = true)
                        }
                    }
                    PosterText(money(p.amount), palette, Modifier)
                }
            }
            if (month.payments.size > MAX_DETAIL_ITEMS_PER_MONTH) {
                PosterText(
                    pluralStringResource(
                        R.plurals.report_more_items,
                        month.payments.size - MAX_DETAIL_ITEMS_PER_MONTH,
                        month.payments.size - MAX_DETAIL_ITEMS_PER_MONTH,
                    ),
                    palette,
                    Modifier.fillMaxWidth(),
                    small = true,
                )
            }
            Row(Modifier.fillMaxWidth()) {
                PosterText(
                    stringResource(R.string.report_transaction_count, month.payments.size),
                    palette,
                    Modifier.weight(1f),
                    small = true,
                )
                PosterText(
                    stringResource(R.string.report_subtotal) + ": " + money(month.subtotal),
                    palette,
                    Modifier,
                    bold = true,
                )
            }
        }
    }
    if (state.months.size > MAX_DETAIL_MONTHS) {
        PosterText(
            pluralStringResource(R.plurals.report_more_months, state.months.size - MAX_DETAIL_MONTHS, state.months.size - MAX_DETAIL_MONTHS),
            palette,
            Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun PosterText(
    text: String,
    palette: io.github.submark.feature.share.ui.poster.PosterPalette,
    modifier: Modifier = Modifier,
    bold: Boolean = false,
    small: Boolean = false,
) {
    Text(
        text,
        style = if (small) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall,
        fontWeight = if (bold) FontWeight.SemiBold else null,
        color = palette.onBackground,
        modifier = modifier,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}
