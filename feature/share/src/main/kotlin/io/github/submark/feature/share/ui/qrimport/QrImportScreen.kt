package io.github.submark.feature.share.ui.qrimport

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.submark.core.ui.component.ErrorState
import io.github.submark.core.ui.component.LoadingState
import io.github.submark.core.ui.component.SectionCard
import io.github.submark.core.ui.component.SubMarkTopAppBar
import io.github.submark.feature.share.R
import io.github.submark.feature.share.data.QrPayloadCodec

@Composable
fun QrImportRoute(
    onBack: () -> Unit,
    onImport: (prefillJson: String) -> Unit,
    viewModel: QrImportViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        viewModel.onImagePicked(uri)
    }
    QrImportScreen(
        uiState = uiState,
        onBack = onBack,
        onPickImage = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        onPaste = viewModel::onTextPasted,
        onImport = { payload -> onImport(viewModel.prefillJson(payload)) },
        onReset = viewModel::reset,
    )
}

@Composable
fun QrImportScreen(
    uiState: QrImportUiState,
    onBack: () -> Unit,
    onPickImage: () -> Unit,
    onPaste: (String) -> Unit,
    onImport: (QrPayloadCodec.Payload) -> Unit,
    onReset: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    Scaffold(
        topBar = { SubMarkTopAppBar(title = stringResource(R.string.qr_import_title), onBack = onBack) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (uiState.decoding) {
                item { LoadingState(message = stringResource(R.string.qr_import_recognizing)) }
            } else if (uiState.payload != null) {
                val payload = uiState.payload
                item {
                    Text(
                        stringResource(R.string.qr_import_step2),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item {
                    SectionCard(title = payload.name) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            PreviewRow(
                                stringResource(R.string.report_col_amount),
                                payload.price?.let { "$it ${payload.currency.orEmpty()}".trim() },
                            )
                            PreviewRow(stringResource(R.string.qr_field_cycle), cycleLabel(payload))
                            payload.category?.let { PreviewRow(stringResource(R.string.qr_field_category), it) }
                            payload.website?.let { PreviewRow(stringResource(R.string.qr_field_website), it) }
                            payload.notes?.let { PreviewRow(stringResource(R.string.qr_field_notes), it) }
                            payload.sharer?.let { PreviewRow(stringResource(R.string.share_sharer_name), it) }
                            payload.description?.let { PreviewRow(stringResource(R.string.share_description), it) }
                            if (payload.children.isNotEmpty()) {
                                PreviewRow(
                                    stringResource(R.string.share_type_bundle),
                                    payload.children.size.toString(),
                                )
                            }
                        }
                    }
                }
                item {
                    Text(
                        stringResource(R.string.qr_import_success_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onReset, modifier = Modifier.weight(1f)) {
                            Text(stringResource(io.github.submark.core.ui.R.string.ui_action_cancel))
                        }
                        Button(onClick = { onImport(payload) }, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.qr_import_button))
                        }
                    }
                }
            } else {
                item {
                    Text(
                        stringResource(R.string.qr_import_step1),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                uiState.error?.let { error ->
                    item {
                        ErrorState(
                            message = stringResource(
                                when (error) {
                                    QrImportError.LoadFailed -> R.string.qr_import_error_load
                                    QrImportError.NoQrFound -> R.string.qr_import_error_no_qr
                                    QrImportError.NotSubMark -> R.string.qr_import_error_not_submark
                                    QrImportError.UnsupportedVersion -> R.string.qr_import_error_version
                                    QrImportError.ProcessingFailed -> R.string.qr_import_error_processing
                                },
                            ),
                            onRetry = null,
                        )
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onPickImage, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.qr_import_pick))
                        }
                        OutlinedButton(
                            onClick = {
                                clipboard.getText()?.text?.takeIf { it.isNotBlank() }?.let(onPaste)
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text(stringResource(R.string.qr_import_paste)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewRow(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun cycleLabel(payload: QrPayloadCodec.Payload): String = when (payload.cycle) {
    null -> "-"
    io.github.submark.core.model.BillingCycle.WEEKLY -> stringResource(R.string.qr_cycle_weekly)
    io.github.submark.core.model.BillingCycle.MONTHLY -> stringResource(R.string.qr_cycle_monthly)
    io.github.submark.core.model.BillingCycle.QUARTERLY -> stringResource(R.string.qr_cycle_quarterly)
    io.github.submark.core.model.BillingCycle.SEMIANNUALLY -> stringResource(R.string.qr_cycle_semiannual)
    io.github.submark.core.model.BillingCycle.ANNUALLY -> stringResource(R.string.qr_cycle_annual)
    io.github.submark.core.model.BillingCycle.CUSTOM -> stringResource(
        R.string.qr_cycle_custom,
        payload.cycleCount ?: 1,
        stringResource(
            when (payload.cycleUnit) {
                io.github.submark.core.model.CycleUnit.DAY -> R.string.qr_unit_day
                io.github.submark.core.model.CycleUnit.WEEK -> R.string.qr_unit_week
                io.github.submark.core.model.CycleUnit.YEAR -> R.string.qr_unit_year
                else -> R.string.qr_unit_month
            },
        ),
    )
}
