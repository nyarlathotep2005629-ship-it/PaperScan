package com.paperscan.model

import java.security.MessageDigest

enum class OcrStatus { COMPLETE, FAILED }

data class NormalizedTextBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    init {
        require(listOf(left, top, right, bottom).all(Float::isFinite))
        require(left in 0f..1f && top in 0f..1f && right in 0f..1f && bottom in 0f..1f)
        require(right > left && bottom > top)
    }
}

data class OcrTextLine(val text: String, val bounds: NormalizedTextBounds) {
    init { require(text.isNotBlank()) }
}

data class PageOcrResult(
    val revision: String,
    val status: OcrStatus,
    val lines: List<OcrTextLine>,
    val engine: String = ENGINE_VERSION,
    val updatedAtEpochMillis: Long
) {
    val text: String get() = lines.joinToString("\n", transform = OcrTextLine::text)

    companion object {
        const val ENGINE_VERSION = "Google ML Kit Text Recognition v2 / 16.0.1"
    }
}

/** Stable key for the immutable source image and all current image-processing settings. */
fun PageItem.ocrRevision(): String {
    val fields = buildString {
        append(id).append('|')
        append(workingRelativePath ?: sourceUri.orEmpty()).append('|')
        append(sourceExifRotationDegrees).append('|').append(sourceExifFlipped).append('|')
        append(rotationQuarterTurns).append('|').append(scanMode.name).append('|')
        append(ocrLanguage.name).append('|')
        corners?.points?.forEach { point -> append(point.x).append(',').append(point.y).append(';') }
    }
    return MessageDigest.getInstance("SHA-256").digest(fields.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xFF) }
}
