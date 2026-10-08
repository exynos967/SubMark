package io.github.submark.feature.share.data

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter

/** ZXing helpers for generating and decoding QR codes. */
object QrBitmap {

    /** Renders [content] as a square QR bitmap of [sizePx]. */
    fun encode(content: String, sizePx: Int = 720): Bitmap {
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        for (x in 0 until sizePx) {
            for (y in 0 until sizePx) {
                bitmap.setPixel(x, y, if (matrix.get(x, y)) Color.BLACK else Color.WHITE)
            }
        }
        return bitmap
    }

    sealed interface DecodeResult {
        data class Success(val text: String) : DecodeResult
        data object NotFound : DecodeResult
        data object LoadFailed : DecodeResult
    }

    /**
     * Tries to decode any 1D/2D code from [source], first on a downscaled grayscale pass, then
     * with [DecodeHintType.INVERTED] for dark-background codes.
     */
    fun decode(source: Bitmap): DecodeResult {
        val scaled = downscale(source, maxDimension = 1024)
        decodeOnce(scaled, inverted = false)?.let { return DecodeResult.Success(it) }
        decodeOnce(scaled, inverted = true)?.let { return DecodeResult.Success(it) }
        return DecodeResult.NotFound
    }

    private fun decodeOnce(bitmap: Bitmap, inverted: Boolean): String? {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val source: LuminanceSource = RGBLuminanceSource(width, height, pixels)
        val binary = BinaryBitmap(HybridBinarizer(source))
        val reader = MultiFormatReader()
        val hints = mutableMapOf<DecodeHintType, Any>(
            DecodeHintType.TRY_HARDER to true,
        )
        if (inverted) hints[DecodeHintType.ALSO_INVERTED] = true
        return try {
            reader.decode(binary, hints).text
        } catch (e: NotFoundException) {
            null
        } catch (e: Exception) {
            null
        } finally {
            reader.reset()
        }
    }

    private fun downscale(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val max = maxOf(bitmap.width, bitmap.height)
        if (max <= maxDimension) return bitmap
        val scale = maxDimension.toFloat() / max
        val w = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val h = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, w, h, true)
    }
}
