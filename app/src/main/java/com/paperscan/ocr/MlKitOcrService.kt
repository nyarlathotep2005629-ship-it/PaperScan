package com.paperscan.ocr

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.paperscan.model.NormalizedTextBounds
import com.paperscan.model.OcrTextLine
import com.paperscan.model.PageOcrResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/** On-device OCR over the corrected page image; it does not make network calls. */
class MlKitOcrService {
    suspend fun recognize(bitmap: Bitmap, revision: String, nowEpochMillis: Long): PageOcrResult =
        withContext(Dispatchers.IO) {
            require(!bitmap.isRecycled)
            val input = InputImage.fromBitmap(bitmap, 0)
            val engines = listOf(
                { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) },
                { TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build()) },
                { TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build()) },
                { TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build()) }
            )
            val lines = mutableListOf<OcrCandidate>()
            engines.forEach { createRecognizer ->
                val recognizer = createRecognizer()
                try {
                    val result = Tasks.await(recognizer.process(input), OCR_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    result.textBlocks.forEach { block ->
                        block.lines.forEach { line ->
                            val text = line.text.trim()
                            val bounds = (line.boundingBox ?: block.boundingBox)?.toNormalizedBounds(bitmap.width, bitmap.height)
                            val confidence = line.confidence
                            if (text.isNotEmpty() && bounds != null && confidence >= MIN_LINE_CONFIDENCE) {
                                val candidate = OcrCandidate(OcrTextLine(text, bounds), confidence)
                                if (lines.none { it.line.overlaps(candidate.line) }) lines += candidate
                            }
                        }
                    }
                } finally {
                    recognizer.close()
                }
            }
            val ordered = lines.map(OcrCandidate::line)
                .sortedWith(compareBy<OcrTextLine> { it.bounds.top }.thenBy { it.bounds.left })
            PageOcrResult(
                revision = revision,
                status = if (ordered.isEmpty()) com.paperscan.model.OcrStatus.FAILED else com.paperscan.model.OcrStatus.COMPLETE,
                lines = ordered,
                updatedAtEpochMillis = nowEpochMillis
            )
        }

    private fun Rect.toNormalizedBounds(width: Int, height: Int): NormalizedTextBounds? {
        val left = (left.toFloat() / width).coerceIn(0f, 1f)
        val top = (top.toFloat() / height).coerceIn(0f, 1f)
        val right = (right.toFloat() / width).coerceIn(0f, 1f)
        val bottom = (bottom.toFloat() / height).coerceIn(0f, 1f)
        if (right <= left || bottom <= top) return null
        return NormalizedTextBounds(left, top, right, bottom)
    }

    /** Different recognizers can return competing transcriptions for the same physical line. */
    private fun OcrTextLine.overlaps(other: OcrTextLine): Boolean {
        val left = maxOf(bounds.left, other.bounds.left)
        val top = maxOf(bounds.top, other.bounds.top)
        val right = minOf(bounds.right, other.bounds.right)
        val bottom = minOf(bounds.bottom, other.bounds.bottom)
        val intersection = (right - left).coerceAtLeast(0f) * (bottom - top).coerceAtLeast(0f)
        val area = bounds.area() + other.bounds.area() - intersection
        return area > 0f && intersection / area >= DUPLICATE_IOU
    }

    private fun NormalizedTextBounds.area(): Float = (right - left) * (bottom - top)

    private data class OcrCandidate(val line: OcrTextLine, val confidence: Float?)

    private companion object {
        const val OCR_TIMEOUT_SECONDS = 60L
        const val MIN_LINE_CONFIDENCE = 0.20f
        const val DUPLICATE_IOU = 0.85f
    }
}
