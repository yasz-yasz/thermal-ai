package com.strukai.thermal.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

data class EncodedImage(
    val base64: String,
    val previewBitmap: Bitmap
)

object ImageEncoder {

    suspend fun loadAndEncode(
        context: Context,
        uri: Uri,
        maxDimension: Int = 1280,
        jpegQuality: Int = 85
    ): EncodedImage = withContext(Dispatchers.IO) {
        val boundsOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, boundsOptions)
        } ?: throw IllegalArgumentException("Gagal membuka gambar screenshot.")

        val rawWidth = boundsOptions.outWidth
        val rawHeight = boundsOptions.outHeight
        if (rawWidth <= 0 || rawHeight <= 0) {
            throw IllegalArgumentException("File gambar tidak valid.")
        }

        var sampleSize = 1
        while (max(rawWidth / sampleSize, rawHeight / sampleSize) > maxDimension * 2) {
            sampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
        }
        val decoded = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        } ?: throw IllegalArgumentException("Gagal membaca bitmap gambar.")

        val scaled = scaleDownIfNeeded(decoded, maxDimension)
        if (scaled !== decoded) {
            decoded.recycle()
        }

        val outputStream = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, jpegQuality, outputStream)
        val jpegBytes = outputStream.toByteArray()
        val base64 = Base64.encodeToString(jpegBytes, Base64.NO_WRAP)

        EncodedImage(base64 = base64, previewBitmap = scaled)
    }

    private fun scaleDownIfNeeded(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val longestSide = max(bitmap.width, bitmap.height)
        if (longestSide <= maxDimension) return bitmap

        val ratio = maxDimension.toFloat() / longestSide.toFloat()
        val targetWidth = (bitmap.width * ratio).roundToInt().coerceAtLeast(1)
        val targetHeight = (bitmap.height * ratio).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }
}
