package com.paperscan.storage

import android.content.Context
import com.paperscan.model.PageItem
import java.io.File
import java.util.UUID

class WorkingFileStore(context: Context) {
    private val filesRoot = File(context.filesDir, "scan_session")
    private val cacheRoot = File(context.cacheDir, "paperscan")

    val sessionFile: File
        get() = File(filesRoot, "session.json")

    fun fallbackImportFile(pageId: String, extension: String): File {
        val safeExtension = extension.lowercase().filter { it.isLetterOrDigit() }.ifBlank { "jpg" }
        return File(File(filesRoot, "fallback_imports").also { it.mkdirs() }, "$pageId.$safeExtension")
    }

    fun captureFile(pageId: String): File =
        File(File(filesRoot, "captures").also { it.mkdirs() }, "page_$pageId.jpg")

    fun relativeCapturePath(file: File): String = "captures/${file.name}"

    fun thumbnailFile(pageId: String): File =
        File(File(cacheRoot, "thumbnails").also { it.mkdirs() }, "$pageId.jpg")

    fun exportTempFile(sessionId: String): File =
        File(File(cacheRoot, "export").also { it.mkdirs() }, "paperscan_$sessionId.pdf")

    fun relativeThumbnailPath(pageId: String): String = "thumbnails/$pageId.jpg"

    fun relativeFallbackPath(file: File): String = "fallback_imports/${file.name}"

    fun resolveWorkingFile(relativePath: String): File {
        val root = filesRoot.canonicalFile
        val candidate = File(root, relativePath).canonicalFile
        require(candidate.path.startsWith(root.path + File.separator)) { "不正な作業ファイルパスです。" }
        return candidate
    }

    fun resolveThumbnail(relativePath: String): File {
        val root = cacheRoot.canonicalFile
        val candidate = File(root, relativePath).canonicalFile
        require(candidate.path.startsWith(root.path + File.separator)) { "不正なサムネイルパスです。" }
        return candidate
    }

    fun createTemporaryFallbackId(): String = UUID.randomUUID().toString()

    fun deletePageFiles(page: PageItem) {
        page.workingRelativePath?.let { runCatching { resolveWorkingFile(it).delete() } }
        page.thumbnailRelativePath?.let { runCatching { resolveThumbnail(it).delete() } }
    }

    fun deleteSessionFiles() {
        filesRoot.deleteRecursively()
    }

    fun cleanupStaleCache() {
        cacheRoot.deleteRecursively()
    }
}
