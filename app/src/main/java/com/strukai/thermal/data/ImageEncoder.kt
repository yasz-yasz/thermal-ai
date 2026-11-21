package com.strukai.thermal.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
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
        val rawBytes = readBytesFromUri(context, uri)
        if (rawBytes.isEmpty()) {
            throw IllegalArgumentException("File gambar kosong atau tidak dapat dibaca.")
        }

        val decoded = decodeBitmapSafely(rawBytes, maxDimension)
            ?: throw IllegalArgumentException("Format gambar tidak didukung atau file rusak.")

        val scaled = scaleDownIfNeeded(decoded, maxDimension)
        if (scaled !== decoded) {
            decoded.recycle()
        }

        val outputStream = ByteArrayOutputStream()
        val compressed = scaled.compress(Bitmap.CompressFormat.JPEG, jpegQuality, outputStream)
        if (!compressed) {
            throw IllegalStateException("Gagal mengonversi gambar ke format JPEG.")
        }
        val jpegBytes = outputStream.toByteArray()
        val base64 = Base64.encodeToString(jpegBytes, Base64.NO_WRAP)

        EncodedImage(base64 = base64, previewBitmap = scaled)
    }

    private fun readBytesFromUri(context: Context, uri: Uri): ByteArray {
        val resolver = context.contentResolver

        // 1. Cara utama: ContentResolver.openInputStream
        runCatching {
            resolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return it }

        // 2. Fallback: ParcelFileDescriptor (mengatasi beberapa OEM / MIUI / HyperOS gallery provider)
        runCatching {
            resolver.openFileDescriptor(uri, "r")?.use { pfd ->
                FileInputStream(pfd.fileDescriptor).use { it.readBytes() }
            }
        }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return it }

        // 3. Fallback untuk URI file:// langsung
        if (uri.scheme == "file" && !uri.path.isNullOrBlank()) {
            val file = File(uri.path!!)
            if (file.exists() && file.canRead()) {
                return file.readBytes()
            }
        }

        throw IllegalArgumentException(
            "Gagal membuka gambar screenshot. Coba pilih gambar dari Galeri/File atau bagikan ulang."
        )
    }

    private fun decodeBitmapSafely(bytes: ByteArray, maxDimension: Int): Bitmap? {
        // Pada Android 9+ (API 28+), ImageDecoder mendukung HEIC/HEIF, WebP, PNG, JPEG & rotasi EXIF otomatis
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val modernBitmap = runCatching {
                val source = ImageDecoder.createSource(ByteBuffer.wrap(bytes))
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    // Wajib software allocator agar aman untuk compress() dan Compose asImageBitmap()
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    val size = info.size
                    val longestSide = max(size.width, size.height)
                    if (longestSide > maxDimension) {
                        val ratio = maxDimension.toFloat() / longestSide.toFloat()
                        val targetW = (size.width * ratio).roundToInt().coerceAtLeast(1)
                        val targetH = (size.height * ratio).roundToInt().coerceAtLeast(1)
                        decoder.setTargetSize(targetW, targetH)
                    }
                }
            }.getOrNull()
            if (modernBitmap != null) {
                return modernBitmap
            }
        }

        // Fallback BitmapFactory (baca dimensi tanpa Elvis null bug, lalu decode dari ByteArray)
        val boundsOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOptions)

        val rawWidth = boundsOptions.outWidth
        val rawHeight = boundsOptions.outHeight
        if (rawWidth <= 0 || rawHeight <= 0) {
            return null
        }

        var sampleSize = 1
        while (max(rawWidth / sampleSize, rawHeight / sampleSize) > maxDimension * 2) {
            sampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
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
