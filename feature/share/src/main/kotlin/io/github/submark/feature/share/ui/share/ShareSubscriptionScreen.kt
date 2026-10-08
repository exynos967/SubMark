package io.github.submark.feature.share.ui.share

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import io.github.submark.core.data.settings.PosterStyle
import io.github.submark.core.ui.component.ErrorState
import io.github.submark.core.ui.component.LoadingState
import io.github.submark.core.ui.component.SectionCard
import io.github.submark.core.ui.component.SegmentedTabs
import io.github.submark.core.ui.component.SettingsSwitchRow
import io.github.submark.core.ui.component.SubMarkTopAppBar
import io.github.submark.feature.share.R
import io.github.submark.feature.share.data.PosterSaveResult
import io.github.submark.feature.share.data.PosterStorage
import io.github.submark.feature.share.data.QrBitmap
import io.github.submark.feature.share.ui.poster.SharePoster
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun ShareSubscriptionRoute(
    onBack: () -> Unit,
    viewModel: ShareSubscriptionViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val posterStorage = remember { PosterStorage(context.applicationContext) }
    ShareSubscriptionScreen(
        uiState = uiState,
        onBack = onBack,
        onSharerNameChange = viewModel::setSharerName,
        onDescriptionChange = viewModel::setDescription,
        onShowQrChange = viewModel::setShowQr,
        onQrPrivacyConfirmed = viewModel::confirmQrPrivacy,
        onStyleChange = viewModel::setPosterStyle,
        posterStorage = posterStorage,
    )
}

@Composable
fun ShareSubscriptionScreen(
    uiState: ShareUiState,
    onBack: () -> Unit,
    onSharerNameChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onShowQrChange: (Boolean) -> Unit,
    onQrPrivacyConfirmed: (Boolean) -> Unit,
    onStyleChange: (PosterStyle) -> Unit,
    posterStorage: PosterStorage,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val graphicsLayer = rememberGraphicsLayer()
    val context = LocalContext.current
    val savedMessage = stringResource(R.string.share_saved_toast)
    val saveFailedMessage = stringResource(R.string.share_save_failed)
    var busy by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { SubMarkTopAppBar(title = stringResource(R.string.share_title), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            uiState.loading -> LoadingState(Modifier.padding(padding))
            uiState.notFound || uiState.subscription == null -> ErrorState(
                modifier = Modifier.padding(padding),
                message = stringResource(R.string.share_not_found),
                onRetry = null,
            )
            else -> {
                val sub = uiState.subscription
                val qrBitmap = remember(uiState.qrContent) {
                    uiState.qrContent?.let { QrBitmap.encode(it, sizePx = 512) }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item {
                        SectionCard(title = stringResource(R.string.share_config_title)) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedTextField(
                                    value = uiState.sharerName,
                                    onValueChange = onSharerNameChange,
                                    label = { Text(stringResource(R.string.share_sharer_name)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                )
                                OutlinedTextField(
                                    value = uiState.description,
                                    onValueChange = onDescriptionChange,
                                    label = { Text(stringResource(R.string.share_description)) },
                                    placeholder = { Text(stringResource(R.string.share_description_placeholder)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    minLines = 2,
                                )
                                SettingsSwitchRow(
                                    title = stringResource(R.string.share_show_qr),
                                    checked = uiState.showQr,
                                    onCheckedChange = { checked ->
                                        if (checked && !uiState.qrPrivacyAcknowledged) {
                                            showPrivacyDialog = true
                                        } else {
                                            onShowQrChange(checked)
                                        }
                                    },
                                )
                            }
                        }
                    }

                    item {
                        SectionCard(title = stringResource(R.string.share_poster_style)) {
                            SegmentedTabs(
                                items = PosterStyle.entries,
                                selected = uiState.posterStyle,
                                onSelect = onStyleChange,
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

                    // Live preview: the same poster at screen scale; recorded into the graphics
                    // layer on every draw so Save/Share can capture it as a Bitmap.
                    item {
                        SectionCard(title = stringResource(R.string.share_preview_title)) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(9f / 16f)
                                    .clip(MaterialTheme.shapes.large)
                                    .drawWithContent {
                                        graphicsLayer.record { this@drawWithContent.drawContent() }
                                        drawLayer(graphicsLayer)
                                    },
                            ) {
                                SharePoster(
                                    subscription = sub,
                                    category = uiState.category,
                                    childrenCount = uiState.children.size,
                                    sharerName = uiState.sharerName,
                                    description = uiState.description,
                                    style = uiState.posterStyle,
                                    qrBitmap = qrBitmap,
                                    today = LocalDate.now(),
                                )
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
                                        val result = posterStorage.saveToGallery(bitmap, "submark_share_${sub.id}")
                                        busy = false
                                        snackbarHostState.showSnackbar(
                                            when (result) {
                                                is PosterSaveResult.Saved -> savedMessage
                                                PosterSaveResult.Failed -> saveFailedMessage
                                            },
                                        )
                                    }
                                },
                                enabled = !busy,
                                modifier = Modifier.weight(1f),
                            ) { Text(stringResource(R.string.share_save)) }
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        busy = true
                                        val bitmap = graphicsLayer.toImageBitmap().asAndroidBitmap()
                                        val uri = posterStorage.shareableUri(bitmap, "submark_share_${sub.id}")
                                        busy = false
                                        if (uri != null) {
                                            val intent = Intent(Intent.ACTION_SEND).apply {
                                                type = "image/png"
                                                putExtra(Intent.EXTRA_STREAM, uri)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(Intent.createChooser(intent, null))
                                        } else {
                                            snackbarHostState.showSnackbar(saveFailedMessage)
                                        }
                                    }
                                },
                                enabled = !busy,
                                modifier = Modifier.weight(1f),
                            ) { Text(stringResource(R.string.share_send)) }
                        }
                    }
                }
            }
        }
    }

    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            title = { Text(stringResource(R.string.share_privacy_title)) },
            text = { Text(stringResource(R.string.share_privacy_message)) },
            confirmButton = {
                TextButton(onClick = {
                    onQrPrivacyConfirmed(true)
                    showPrivacyDialog = false
                }) { Text(stringResource(R.string.share_privacy_include)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    onQrPrivacyConfirmed(false)
                    showPrivacyDialog = false
                }) { Text(stringResource(R.string.share_privacy_exclude)) }
            },
        )
    }
}
