package io.github.submark.feature.share.ui.report

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.submark.core.data.settings.PosterDisplayMode
import io.github.submark.core.data.settings.PosterStyle
import io.github.submark.core.data.time.TimeProvider
import io.github.submark.core.ui.component.DatePickerField
import io.github.submark.core.ui.component.SectionCard
import io.github.submark.core.ui.component.SegmentedTabs
import io.github.submark.core.ui.component.SettingsGroup
import io.github.submark.core.ui.component.SettingsSwitchRow
import io.github.submark.core.ui.component.SubMarkTopAppBar
import io.github.submark.feature.share.R
import io.github.submark.feature.share.data.PosterSaveResult
import io.github.submark.feature.share.data.PosterStorage
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun FinancialReportPosterRoute(
    onBack: () -> Unit,
    viewModel: FinancialReportPosterViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val posterStorage = remember { PosterStorage(context.applicationContext) }
    FinancialReportPosterScreen(
        uiState = uiState,
        onBack = onBack,
        viewModel = viewModel,
        posterStorage = posterStorage,
    )
}

@Composable
fun FinancialReportPosterScreen(
    uiState: ReportUiState,
    onBack: () -> Unit,
    viewModel: FinancialReportPosterViewModel,
    posterStorage: PosterStorage,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val graphicsLayer = rememberGraphicsLayer()
    val context = LocalContext.current
    var periodKind by remember { mutableStateOf(PeriodKind.MONTH) }
    var busy by remember { mutableStateOf(false) }
    val savedMessage = stringResource(R.string.share_saved_toast)
    val saveFailedMessage = stringResource(R.string.share_save_failed)
    val shareText = stringResource(R.string.report_share_text)
    val today = uiState.generatedDate ?: LocalDate.now()

    val start = uiState.periodStart ?: today.withDayOfMonth(1)
    val end = uiState.periodEnd ?: today.withDayOfMonth(today.lengthOfMonth())
    val periodError = !uiState.periodValid

    Scaffold(
        topBar = { SubMarkTopAppBar(title = stringResource(R.string.report_title), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SectionCard(title = stringResource(R.string.report_period_title)) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SegmentedTabs(
                            items = listOf(PeriodKind.MONTH, PeriodKind.RANGE),
                            selected = periodKind,
                            onSelect = { periodKind = it },
                        ) { k ->
                            stringResource(if (k == PeriodKind.MONTH) R.string.report_period_month else R.string.report_period_range)
                        }
                        if (periodKind == PeriodKind.MONTH) {
                            DatePickerField(
                                label = stringResource(R.string.report_period_month),
                                date = start,
                                onDateChange = { viewModel.setMonth(YearMonth.from(it)) },
                                today = today,
                                maxDate = today,
                            )
                        } else {
                            DatePickerField(
                                label = stringResource(R.string.report_period_start),
                                date = start,
                                onDateChange = { viewModel.setRange(it, end) },
                                today = today,
                                maxDate = today,
                            )
                            DatePickerField(
                                label = stringResource(R.string.report_period_end),
                                date = end,
                                onDateChange = { viewModel.setRange(start, it) },
                                today = today,
                                minDate = start.plusDays(1),
                                maxDate = today,
                                isError = periodError,
                            )
                            if (periodError) {
                                Text(
                                    stringResource(R.string.report_period_error),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            }

            item {
                SettingsGroup(title = stringResource(R.string.report_display_mode)) {
                    SegmentedTabs(
                        items = listOf(PosterDisplayMode.SIMPLE, PosterDisplayMode.DETAILED),
                        selected = uiState.displayMode,
                        onSelect = viewModel::setDisplayMode,
                        modifier = Modifier.padding(12.dp),
                    ) { m ->
                        stringResource(if (m == PosterDisplayMode.SIMPLE) R.string.report_mode_simple else R.string.report_mode_detailed)
                    }
                }
            }

            item {
                SettingsGroup(title = stringResource(R.string.report_privacy)) {
                    SettingsSwitchRow(
                        title = stringResource(R.string.report_hide_amounts),
                        checked = uiState.hideAmounts,
                        onCheckedChange = viewModel::setHideAmounts,
                    )
                    SettingsSwitchRow(
                        title = stringResource(R.string.report_hide_names),
                        checked = uiState.hideNames,
                        onCheckedChange = viewModel::setHideNames,
                    )
                    SettingsSwitchRow(
                        title = stringResource(R.string.report_hide_notes),
                        checked = uiState.hideNotes,
                        onCheckedChange = viewModel::setHideNotes,
                    )
                    SettingsSwitchRow(
                        title = stringResource(R.string.report_hide_payment_details),
                        checked = uiState.hidePaymentDetails,
                        onCheckedChange = viewModel::setHidePaymentDetails,
                    )
                }
            }

            item {
                SettingsGroup(title = stringResource(R.string.report_options)) {
                    SettingsSwitchRow(
                        title = stringResource(R.string.report_show_logo),
                        checked = uiState.showLogo,
                        onCheckedChange = viewModel::setShowLogo,
                    )
                    SettingsSwitchRow(
                        title = stringResource(R.string.report_show_date),
                        checked = uiState.showGeneratedDate,
                        onCheckedChange = viewModel::setShowGeneratedDate,
                    )
                    SettingsSwitchRow(
                        title = stringResource(R.string.report_show_stats),
                        checked = uiState.showStatistics,
                        onCheckedChange = viewModel::setShowStatistics,
                    )
                }
            }

            item {
                SectionCard(title = stringResource(R.string.share_poster_style)) {
                    SegmentedTabs(
                        items = PosterStyle.entries,
                        selected = uiState.style,
                        onSelect = viewModel::setStyle,
                    ) { style ->
                        stringResource(
                            when (style) {
                                PosterStyle.MINIMAL -> R.string.share_style_minimal
                                PosterStyle.MODERN -> R.string.share_style_modern
                                PosterStyle.GRADIENT -> R.string.share_style_gradient
                                PosterStyle.COLORFUL -> R.string.share_style_colorful
                            },
                        )
                    }
                }
            }

            // Preview (zoomable)
            item {
                SectionCard(title = stringResource(R.string.share_preview_title)) {
                    Column {
                        Text(
                            stringResource(R.string.report_zoom_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.large)
                                .drawWithContent {
                                    graphicsLayer.record { this@drawWithContent.drawContent() }
                                    drawLayer(graphicsLayer)
                                },
                        ) {
                            FinancialReportPoster(state = uiState)
                        }
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            scope.launch {
                                busy = true
                                val bitmap = graphicsLayer.toImageBitmap().asAndroidBitmap()
                                val result = posterStorage.saveToGallery(bitmap, "submark_report_${start}_${end}")
                                busy = false
                                snackbarHostState.showSnackbar(
                                    when (result) {
                                        is PosterSaveResult.Saved -> savedMessage
                                        PosterSaveResult.Failed -> saveFailedMessage
                                    },
                                )
                            }
                        },
                        enabled = !busy && !periodError,
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.share_save)) }
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                busy = true
                                val bitmap = graphicsLayer.toImageBitmap().asAndroidBitmap()
                                val uri = posterStorage.shareableUri(bitmap, "submark_report_${start}_${end}")
                                busy = false
                                if (uri != null) {
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "image/png"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        putExtra(Intent.EXTRA_TEXT, shareText)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(intent, null))
                                } else {
                                    snackbarHostState.showSnackbar(saveFailedMessage)
                                }
                            }
                        },
                        enabled = !busy && !periodError,
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.share_send)) }
                }
            }
        }
    }
}
