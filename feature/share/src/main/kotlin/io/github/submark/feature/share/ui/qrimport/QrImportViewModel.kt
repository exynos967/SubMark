package io.github.submark.feature.share.ui.qrimport

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.submark.core.model.SubscriptionPrefill
import io.github.submark.feature.share.data.QrBitmap
import io.github.submark.feature.share.data.QrPayloadCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject

sealed interface QrImportError {
    data object LoadFailed : QrImportError
    data object NoQrFound : QrImportError
    data object NotSubMark : QrImportError
    data object UnsupportedVersion : QrImportError
    data object ProcessingFailed : QrImportError
}

data class QrImportUiState(
    val decoding: Boolean = false,
    val error: QrImportError? = null,
    val payload: QrPayloadCodec.Payload? = null,
)

@HiltViewModel
class QrImportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(QrImportUiState())
    val uiState: StateFlow<QrImportUiState> = _uiState

    private val json = Json { ignoreUnknownKeys = true }

    /** Photo-picker result; decodes off the main thread. */
    fun onImagePicked(uri: Uri?) {
        if (uri == null) return
        _uiState.value = QrImportUiState(decoding = true)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                val bitmap = loadBitmap(uri) ?: return@withContext QrImportUiState(error = QrImportError.LoadFailed)
                when (val decoded = QrBitmap.decode(bitmap)) {
                    is QrBitmap.DecodeResult.Success -> parse(decoded.text)
                    QrBitmap.DecodeResult.NotFound -> QrImportUiState(error = QrImportError.NoQrFound)
                    QrBitmap.DecodeResult.LoadFailed -> QrImportUiState(error = QrImportError.LoadFailed)
                }
            }
            _uiState.value = result
        }
    }

    /** Pasted `submark://import` text. */
    fun onTextPasted(text: String) {
        _uiState.value = QrImportUiState(decoding = true)
        viewModelScope.launch {
            _uiState.value = withContext(Dispatchers.Default) { parse(text) }
        }
    }

    private fun parse(text: String): QrImportUiState = when (val r = QrPayloadCodec.decode(text)) {
        is QrPayloadCodec.DecodeResult.Success -> QrImportUiState(payload = r.payload)
        QrPayloadCodec.DecodeResult.NotSubMark -> QrImportUiState(error = QrImportError.NotSubMark)
        QrPayloadCodec.DecodeResult.UnsupportedVersion -> QrImportUiState(error = QrImportError.UnsupportedVersion)
        QrPayloadCodec.DecodeResult.Malformed -> QrImportUiState(error = QrImportError.ProcessingFailed)
    }

    /** JSON of the [SubscriptionPrefill] for `SubscriptionEditRoute(prefillJson)`. */
    fun prefillJson(payload: QrPayloadCodec.Payload): String {
        val prefill = QrPayloadCodec.toPrefill(payload)
        return json.encodeToString(SubscriptionPrefill.serializer(), prefill)
    }

    fun reset() {
        _uiState.value = QrImportUiState()
    }

    private fun loadBitmap(uri: Uri): Bitmap? = try {
        if (Build.VERSION.SDK_INT >= 28) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }
    } catch (e: Exception) {
        null
    }
}
