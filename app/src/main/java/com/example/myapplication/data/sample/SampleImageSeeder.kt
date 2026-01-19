package com.example.myapplication.data.sample

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object SampleImageSeeder {
    private const val prefsName = "menu_analyzer_samples"
    private const val prefsKeySeeded = "seeded_v2"
    private const val imageSize = 1080
    private const val albumName = "MenuAnalyzer"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun seedIfNeeded(context: Context) {
        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        if (prefs.getBoolean(prefsKeySeeded, false)) {
            return
        }
        scope.launch {
            var success = true
            val seededAssets = seedAssets(context)
            if (seededAssets == 0) {
                success = seedGenerated(context)
            }
            if (success) {
                prefs.edit().putBoolean(prefsKeySeeded, true).apply()
            }
        }
    }

    private fun seedAssets(context: Context): Int {
        var seededCount = 0
        for (spec in assetSamples) {
            if (findExistingUri(context, spec.displayName) != null) {
                continue
            }
            val uri = insertAssetImage(
                context = context,
                assetPath = spec.assetPath,
                displayName = spec.displayName
            )
            if (uri != null) {
                seededCount += 1
            }
        }
        return seededCount
    }

    private fun seedGenerated(context: Context): Boolean {
        var success = true
        for (spec in generatedSamples) {
            if (findExistingUri(context, spec.fileName) != null) {
                continue
            }
            val bitmap = renderSample(spec)
            if (insertBitmap(context.contentResolver, bitmap, spec.fileName) == null) {
                success = false
            }
        }
        return success
    }

    private fun findExistingUri(context: Context, fileName: String): Uri? {
        val projection = arrayOf(MediaStore.Images.Media._ID)
        val selection = "${MediaStore.Images.Media.DISPLAY_NAME}=?"
        val selectionArgs = arrayOf(fileName)
        val resolver = context.contentResolver
        resolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(0)
                return ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id
                )
            }
        }
        return null
    }

    private fun insertAssetImage(
        context: Context,
        assetPath: String,
        displayName: String
    ): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, guessMimeType(displayName))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    "${Environment.DIRECTORY_PICTURES}/$albumName"
                )
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return null
        try {
            context.assets.open(assetPath).use { input ->
                resolver.openOutputStream(uri)?.use { output ->
                    input.copyTo(output)
                } ?: run {
                    resolver.delete(uri, null, null)
                    return null
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val done = ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                }
                resolver.update(uri, done, null, null)
            }
            return uri
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            return null
        }
    }

    private fun insertBitmap(
        resolver: ContentResolver,
        bitmap: Bitmap,
        fileName: String
    ): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    "${Environment.DIRECTORY_PICTURES}/$albumName"
                )
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return null
        try {
            resolver.openOutputStream(uri)?.use { stream ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                    resolver.delete(uri, null, null)
                    return null
                }
            } ?: run {
                resolver.delete(uri, null, null)
                return null
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val done = ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                }
                resolver.update(uri, done, null, null)
            }
            return uri
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            return null
        }
    }

    private fun guessMimeType(fileName: String): String {
        val lower = fileName.lowercase()
        return when {
            lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "image/jpeg"
            lower.endsWith(".png") -> "image/png"
            else -> "image/*"
        }
    }

    private fun renderSample(spec: SampleImageSpec): Bitmap {
        val bitmap = Bitmap.createBitmap(imageSize, imageSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(spec.background)

        val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = spec.accent }
        val accentPaint2 = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = spec.accent2 }
        canvas.drawCircle(320f, 350f, 260f, accentPaint)
        canvas.drawCircle(760f, 420f, 190f, accentPaint2)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 64f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 44f
        }

        var y = 720f
        canvas.drawText(spec.title, 80f, y, titlePaint)
        y += 70f
        for (line in spec.lines) {
            canvas.drawText(line, 80f, y, bodyPaint)
            y += 58f
        }
        return bitmap
    }

    private data class SampleImageSpec(
        val fileName: String,
        val title: String,
        val lines: List<String>,
        val background: Int,
        val accent: Int,
        val accent2: Int
    )

    private data class AssetImageSpec(
        val assetPath: String,
        val displayName: String
    )

    private val assetSamples = listOf(
        AssetImageSpec(
            assetPath = "sample_photos/food1.png",
            displayName = "menu_analyzer_food1.png"
        ),
        AssetImageSpec(
            assetPath = "sample_photos/food2.png",
            displayName = "menu_analyzer_food2.png"
        ),
        AssetImageSpec(
            assetPath = "sample_photos/food3.png",
            displayName = "menu_analyzer_food3.png"
        )
    )

    private val generatedSamples = listOf(
        SampleImageSpec(
            fileName = "menu_analyzer_sample_noodles.png",
            title = "Spicy Noodles",
            lines = listOf("peanut, soy, chili"),
            background = Color.parseColor("#D9552E"),
            accent = Color.parseColor("#F2B441"),
            accent2 = Color.parseColor("#8C2A1C")
        ),
        SampleImageSpec(
            fileName = "menu_analyzer_sample_salad.png",
            title = "Chicken Salad",
            lines = listOf("lettuce, chicken, milk dressing"),
            background = Color.parseColor("#3E8C4A"),
            accent = Color.parseColor("#A3D977"),
            accent2 = Color.parseColor("#2C6B36")
        ),
        SampleImageSpec(
            fileName = "menu_analyzer_sample_tea.png",
            title = "Fruit Tea",
            lines = listOf("sugar, honey, citrus"),
            background = Color.parseColor("#2E5AAC"),
            accent = Color.parseColor("#F26B8A"),
            accent2 = Color.parseColor("#1D3E7A")
        )
    )
}
