package com.example.myapplication.data.image

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import java.io.ByteArrayOutputStream

data class ImagePayload(
    val bytes: ByteArray,
    val mimeType: String,
    val fileName: String
)

object ImagePreprocessor {
    fun prepareForUpload(
        context: Context,
        uri: Uri,
        maxSize: Int = 640,
        quality: Int = 70
    ): ImagePayload {
        val resolver = context.contentResolver
        val fileName = normalizeFileName(queryDisplayName(resolver, uri))

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
        }
        val sampleSize = calculateInSampleSize(bounds, maxSize, maxSize)
        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val bitmap = resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        } ?: throw IllegalStateException("Unable to decode image.")

        val scaled = scaleBitmap(bitmap, maxSize)
        if (scaled !== bitmap) {
            bitmap.recycle()
        }

        val output = ByteArrayOutputStream()
        val ok = scaled.compress(Bitmap.CompressFormat.JPEG, quality, output)
        if (!ok) {
            scaled.recycle()
            throw IllegalStateException("Image compression failed.")
        }
        val bytes = output.toByteArray()
        scaled.recycle()
        return ImagePayload(bytes = bytes, mimeType = "image/jpeg", fileName = fileName)
    }

    private fun queryDisplayName(resolver: ContentResolver, uri: Uri): String? {
        val projection = arrayOf(OpenableColumns.DISPLAY_NAME)
        resolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) {
                    return cursor.getString(index)
                }
            }
        }
        return null
    }

    private fun normalizeFileName(raw: String?): String {
        val name = raw?.trim().orEmpty()
        if (name.isEmpty()) {
            return "image.jpg"
        }
        val base = name.substringBeforeLast('.', name)
        return "$base.jpg"
    }

    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while (
                halfHeight / inSampleSize >= reqHeight &&
                halfWidth / inSampleSize >= reqWidth
            ) {
                inSampleSize *= 2
            }
        }
        return inSampleSize.coerceAtLeast(1)
    }

    private fun scaleBitmap(bitmap: Bitmap, maxSize: Int): Bitmap {
        val width = bitmap.width.toFloat()
        val height = bitmap.height.toFloat()
        val maxDim = maxOf(width, height)
        if (maxDim <= maxSize) {
            return bitmap
        }
        val scale = maxSize / maxDim
        val targetW = (width * scale).toInt()
        val targetH = (height * scale).toInt()
        return Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
    }
}
