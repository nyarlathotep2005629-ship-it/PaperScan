package com.paperscan.importer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import com.paperscan.image.SourceImageLoader
import com.paperscan.model.PageItem
import com.paperscan.model.PageStatus
import com.paperscan.model.ScanMode
import com.paperscan.model.SourceType
import com.paperscan.storage.WorkingFileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileOutputStream
import java.util.UUID

data class ImportResult(
    val pages: List<PageItem>,
    val duplicateCount: Int,
    val failures: Int
)

class PhotoImportService(
    private val context: Context,
    private val workingFileStore: WorkingFileStore,
    private val imageLoader: SourceImageLoader
) {
    suspend fun importUris(uris: List<Uri>, existingUris: Set<String>): ImportResult =
        withContext(Dispatchers.IO) {
            val distinctUris = uris.distinctBy(Uri::toString)
            val newUris = distinctUris.filter { it.toString() !in existingUris }
            val pages = buildList {
                newUris.forEach { uri ->
                    runCatching { createPage(uri) }.onSuccess(::add)
                }
            }
            ImportResult(
                pages = pages,
                duplicateCount = uris.size - newUris.size,
                failures = newUris.size - pages.size
            )
        }

    private suspend fun createPage(uri: Uri): PageItem {
        val id = UUID.randomUUID().toString()
        var fallbackRelativePath: String? = null
        try {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) {
                val extension = MimeTypeMap.getSingleton()
                    .getExtensionFromMimeType(context.contentResolver.getType(uri))
                    ?: "jpg"
                val fallback = workingFileStore.fallbackImportFile(id, extension)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(fallback).use { output -> input.copyTo(output) }
                } ?: error("選択画像を読み込めませんでした。")
                fallbackRelativePath = workingFileStore.relativeFallbackPath(fallback)
            }

            var page = PageItem(
                id = id,
                sourceType = SourceType.IMPORT,
                sourceUri = uri.toString(),
                workingRelativePath = fallbackRelativePath,
                thumbnailRelativePath = workingFileStore.relativeThumbnailPath(id),
                sourceExifRotationDegrees = 0,
                sourceExifFlipped = false,
                rotationQuarterTurns = 0,
                scanMode = ScanMode.AUTO,
                status = PageStatus.READY
            )
            val (rotation, flipped) = imageLoader.readExif(page)
            page = page.copy(sourceExifRotationDegrees = rotation, sourceExifFlipped = flipped)
            imageLoader.createThumbnail(page)
            return page
        } catch (failure: Throwable) {
            fallbackRelativePath?.let { runCatching { workingFileStore.resolveWorkingFile(it).delete() } }
            runCatching { workingFileStore.thumbnailFile(id).delete() }
            throw failure
        }
    }
}
