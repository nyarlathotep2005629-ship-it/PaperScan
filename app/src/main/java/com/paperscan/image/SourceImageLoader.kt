package com.paperscan.image

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.paperscan.model.PageItem
import com.paperscan.storage.WorkingFileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class SourceImageLoader(
    context: Context,
    private val workingFileStore: WorkingFileStore
) {
    private val contentResolver: ContentResolver = context.contentResolver

    suspend fun readExif(page: PageItem): Pair<Int, Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            openInputStream(page).use { input ->
                val exif = ExifInterface(input)
                exif.rotationDegrees to exif.isFlipped
            }
        }.getOrDefault(0 to false)
    }

    suspend fun decodePreview(page: PageItem, maxLongEdgePx: Int = 320): Bitmap =
        decodeSampled(page, maxLongEdgePx)

    suspend fun decodeForExport(page: PageItem, maxLongEdgePx: Int = 3000): Bitmap =
        decodeSampled(page, maxLongEdgePx)

    suspend fun createThumbnail(page: PageItem): File = withContext(Dispatchers.IO) {
        val bitmap = decodeSampled(page, 320)
        try {
            val target = workingFileStore.thumbnailFile(page.id)
            FileOutputStream(target).use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 75, output)) { "サムネイルを保存できませんでした。" }
            }
            target
        } finally {
            bitmap.recycle()
        }
    }

    suspend fun loadThumbnail(page: PageItem): Bitmap? = withContext(Dispatchers.IO) {
        val stored = page.thumbnailRelativePath?.let { workingFileStore.resolveThumbnail(it) }
        when {
            stored?.exists() == true -> BitmapFactory.decodeFile(stored.path)
            else -> runCatching { decodeSampled(page, 320) }.getOrNull()
        }
    }

    fun openInputStream(page: PageItem): InputStream {
        val workingPath = page.workingRelativePath
        if (workingPath != null) return workingFileStore.resolveWorkingFile(workingPath).inputStream()
        val uri = page.sourceUri?.let(Uri::parse) ?: error("画像の参照先がありません。")
        return contentResolver.openInputStream(uri) ?: error("画像を開けませんでした。")
    }

    private suspend fun decodeSampled(page: PageItem, maxLongEdgePx: Int): Bitmap = withContext(Dispatchers.IO) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        openInputStream(page).use { BitmapFactory.decodeStream(it, null, bounds) }
        check(bounds.outWidth > 0 && bounds.outHeight > 0) { "画像形式を読み取れませんでした。" }
        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight, maxLongEdgePx)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = openInputStream(page).use { BitmapFactory.decodeStream(it, null, options) }
            ?: error("画像をデコードできませんでした。")
        applyOrientation(decoded, page.sourceExifRotationDegrees, page.sourceExifFlipped, page.rotationQuarterTurns)
    }

    private fun calculateSampleSize(width: Int, height: Int, maxLongEdgePx: Int): Int {
        var sample = 1
        var longest = maxOf(width, height)
        while (longest / 2 >= maxLongEdgePx) {
            sample *= 2
            longest /= 2
        }
        return sample
    }

    private fun applyOrientation(
        source: Bitmap,
        exifRotationDegrees: Int,
        exifFlipped: Boolean,
        userQuarterTurns: Int
    ): Bitmap {
        val rotation = ((exifRotationDegrees + (userQuarterTurns % 4) * 90) % 360 + 360) % 360
        if (!exifFlipped && rotation == 0) return source
        val matrix = Matrix().apply {
            if (exifFlipped) postScale(-1f, 1f)
            if (rotation != 0) postRotate(rotation.toFloat())
        }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true).also {
            if (it !== source) source.recycle()
        }
    }
}
