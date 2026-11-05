package com.paperscan.storage

import com.paperscan.model.PageItem
import com.paperscan.model.PageStatus
import com.paperscan.model.ScanMode
import com.paperscan.model.ScanSession
import com.paperscan.model.SourceType
import com.paperscan.model.CornerPoints
import com.paperscan.model.DetectionConfidence
import com.paperscan.model.NormalizedPoint
import com.paperscan.model.NormalizedTextBounds
import com.paperscan.model.OcrStatus
import com.paperscan.model.OcrTextLine
import com.paperscan.model.PageOcrResult
import com.paperscan.model.OcrLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class SessionStore(private val workingFileStore: WorkingFileStore) {
    private val saveMutex = Mutex()

    sealed interface LoadResult {
        data object Missing : LoadResult
        data class Loaded(val session: ScanSession) : LoadResult
        data class Corrupted(val cause: Throwable) : LoadResult
    }

    fun exists(): Boolean = workingFileStore.sessionFile.exists()

    suspend fun load(): LoadResult = withContext(Dispatchers.IO) {
        val file = workingFileStore.sessionFile
        if (!file.exists()) return@withContext LoadResult.Missing
        runCatching { parse(file.readText(Charsets.UTF_8)) }
            .fold(
                onSuccess = { LoadResult.Loaded(it) },
                onFailure = { LoadResult.Corrupted(it) }
            )
    }

    suspend fun saveAtomic(session: ScanSession): Result<Unit> = withContext(Dispatchers.IO) {
        saveMutex.withLock {
            val target = workingFileStore.sessionFile
            val temporary = File(target.parentFile, "${target.name}.tmp")
            runCatching {
                target.parentFile?.mkdirs()
                FileOutputStream(temporary).use { output ->
                    output.write(serialize(session).toByteArray(Charsets.UTF_8))
                    output.fd.sync()
                }
                parse(temporary.readText(Charsets.UTF_8))
                replacePreservingOldFile(temporary, target)
            }.onFailure {
                temporary.delete()
            }
        }
    }

    suspend fun delete(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val file = workingFileStore.sessionFile
            if (file.exists() && !file.delete()) error("セッション情報を削除できませんでした。")
        }
    }

    private fun replacePreservingOldFile(temporary: File, target: File) {
        try {
            Files.move(
                temporary.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
            return
        } catch (_: Exception) {
            // Some Android file systems do not support atomic replacement. Use a recoverable fallback.
        }

        val backup = File(target.parentFile, "${target.name}.bak")
        if (backup.exists() && !backup.delete()) error("以前のバックアップを削除できませんでした。")
        val hadTarget = target.exists()
        if (hadTarget && !target.renameTo(backup)) error("既存セッションを退避できませんでした。")
        try {
            if (!temporary.renameTo(target)) error("新しいセッション情報を配置できませんでした。")
            if (backup.exists() && !backup.delete()) error("バックアップを削除できませんでした。")
        } catch (failure: Throwable) {
            if (hadTarget && backup.exists() && !target.exists()) {
                backup.renameTo(target)
            }
            throw failure
        }
    }

    private fun serialize(session: ScanSession): String = JSONObject().apply {
        put("schemaVersion", session.schemaVersion)
        put("id", session.id)
        put("createdAtEpochMillis", session.createdAtEpochMillis)
        put("updatedAtEpochMillis", session.updatedAtEpochMillis)
        put("selectedPageId", session.selectedPageId)
        put("pages", JSONArray().apply {
            session.pages.forEach { page -> put(page.toJson()) }
        })
    }.toString()

    private fun parse(text: String): ScanSession {
        val root = JSONObject(text)
        val schemaVersion = root.getInt("schemaVersion")
        require(schemaVersion in 1..ScanSession.CURRENT_SCHEMA_VERSION) { "未対応のセッション形式です。" }
        val pages = root.getJSONArray("pages").toPageItems()
        return ScanSession(
            schemaVersion = ScanSession.CURRENT_SCHEMA_VERSION,
            id = root.getString("id"),
            createdAtEpochMillis = root.getLong("createdAtEpochMillis"),
            updatedAtEpochMillis = root.getLong("updatedAtEpochMillis"),
            pages = pages,
            selectedPageId = root.optString("selectedPageId").takeIf { it.isNotBlank() }
        )
    }

    private fun PageItem.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("sourceType", sourceType.name)
        put("sourceUri", sourceUri)
        put("workingRelativePath", workingRelativePath)
        put("thumbnailRelativePath", thumbnailRelativePath)
        put("sourceExifRotationDegrees", sourceExifRotationDegrees)
        put("sourceExifFlipped", sourceExifFlipped)
        put("rotationQuarterTurns", rotationQuarterTurns)
        put("scanMode", scanMode.name)
        put("status", status.name)
        put("ocrLanguage", ocrLanguage.name)
        corners?.let { points ->
            put("corners", JSONArray().apply {
                points.points.forEach { point ->
                    put(JSONObject().apply {
                        put("x", point.x)
                        put("y", point.y)
                    })
                }
            })
        }
        detectionConfidence?.let { put("detectionConfidence", it.name) }
        ocrResult?.let { result ->
            put("ocrResult", JSONObject().apply {
                put("revision", result.revision)
                put("status", result.status.name)
                put("engine", result.engine)
                put("updatedAtEpochMillis", result.updatedAtEpochMillis)
                put("lines", JSONArray().apply {
                    result.lines.forEach { line -> put(JSONObject().apply {
                        put("text", line.text)
                        put("bounds", JSONObject().apply {
                            put("left", line.bounds.left)
                            put("top", line.bounds.top)
                            put("right", line.bounds.right)
                            put("bottom", line.bounds.bottom)
                        })
                    }) }
                })
            })
        }
    }

    private fun JSONArray.toPageItems(): List<PageItem> = List(length()) { index ->
        getJSONObject(index).let { json ->
            PageItem(
                id = json.getString("id"),
                sourceType = SourceType.valueOf(json.getString("sourceType")),
                sourceUri = json.optString("sourceUri").takeIf { it.isNotBlank() },
                workingRelativePath = json.optString("workingRelativePath").takeIf { it.isNotBlank() },
                thumbnailRelativePath = json.optString("thumbnailRelativePath").takeIf { it.isNotBlank() },
                sourceExifRotationDegrees = json.optInt("sourceExifRotationDegrees", 0),
                sourceExifFlipped = json.optBoolean("sourceExifFlipped", false),
                rotationQuarterTurns = json.optInt("rotationQuarterTurns", 0),
                scanMode = ScanMode.valueOf(json.optString("scanMode", ScanMode.AUTO.name)),
                status = PageStatus.valueOf(json.optString("status", PageStatus.READY.name)),
                corners = json.optCorners(),
                detectionConfidence = json.optString("detectionConfidence")
                    .takeIf { it.isNotBlank() }
                    ?.let(DetectionConfidence::valueOf),
                ocrLanguage = OcrLanguage.valueOf(json.optString("ocrLanguage", OcrLanguage.AUTO.name)),
                ocrResult = json.optJSONObject("ocrResult")?.let { result ->
                    PageOcrResult(
                        revision = result.getString("revision"),
                        status = OcrStatus.valueOf(result.getString("status")),
                        engine = result.optString("engine", PageOcrResult.ENGINE_VERSION),
                        updatedAtEpochMillis = result.optLong("updatedAtEpochMillis", 0),
                        lines = result.optJSONArray("lines")?.let { lines ->
                            List(lines.length()) { lineIndex ->
                                val line = lines.getJSONObject(lineIndex)
                                val bounds = line.getJSONObject("bounds")
                                OcrTextLine(
                                    text = line.getString("text"),
                                    bounds = NormalizedTextBounds(
                                        left = bounds.getDouble("left").toFloat(),
                                        top = bounds.getDouble("top").toFloat(),
                                        right = bounds.getDouble("right").toFloat(),
                                        bottom = bounds.getDouble("bottom").toFloat()
                                    )
                                )
                            }
                        } ?: emptyList()
                    )
                }
            )
        }
    }

    private fun JSONObject.optCorners(): CornerPoints? {
        if (!has("corners")) return null
        val array = optJSONArray("corners") ?: return null
        if (array.length() != 4) return null
        val points = List(4) { index ->
            array.getJSONObject(index).let { NormalizedPoint(it.getDouble("x").toFloat(), it.getDouble("y").toFloat()) }
        }
        return CornerPoints(points[0], points[1], points[2], points[3]).takeIf(CornerPoints::isValid)
    }
}
