package com.paperscan.vnext

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.paperscan.model.OcrStatus
import com.paperscan.ocr.MlKitOcrService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/** Small, synthetic technical proof; it does not use personal document images. */
@RunWith(AndroidJUnit4::class)
class MultilingualOcrInstrumentedTest {
    @Test
    fun bundledMlKitRecognizers_readRequiredScriptsAndReturnTextBounds() = runBlocking(Dispatchers.IO) {
        val cases = listOf(
            OcrCase("English", "PaperScan", "PaperScan", { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }),
            OcrCase("Chinese-Simplified", "简体中文测试", "文", {
                TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
            }),
            OcrCase("Chinese-Traditional", "繁體中文測試", "文", {
                TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
            }),
            OcrCase("Japanese", "日本語テスト", "日本", {
                TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
            }),
            OcrCase("Korean", "한국어", "한국", {
                TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
            })
        )

        cases.forEach { case ->
            val bitmap = textImage(case.expectedText)
            val recognizer = case.createRecognizer()
            try {
                val started = android.os.SystemClock.elapsedRealtime()
                val result = Tasks.await(
                    recognizer.process(InputImage.fromBitmap(bitmap, 0)),
                    30,
                    TimeUnit.SECONDS
                )
                val elapsed = android.os.SystemClock.elapsedRealtime() - started
                val recognizedCodePoints = result.text.codePoints().toArray().joinToString(",") { "U+%04X".format(it) }
                assertTrue(
                    "${case.label}: expected '${case.expectedToken}' in recognized codepoints [$recognizedCodePoints]",
                    result.text.contains(case.expectedToken)
                )
                assertTrue("${case.label}: expected text bounds", result.textBlocks.any { it.boundingBox != null })
                Log.i("PaperScanOcrPoc", "script=${case.label} elapsedMs=$elapsed text=${result.text}")
            } finally {
                recognizer.close()
                bitmap.recycle()
            }
        }
    }

    @Test
    fun productionOcrService_handlesMixedScriptsAndKeepsNormalizedLineBounds() = runBlocking(Dispatchers.IO) {
        val bitmap = mixedTextImage()
        try {
            val started = android.os.SystemClock.elapsedRealtime()
            val result = MlKitOcrService().recognize(bitmap, "poc-revision", 1)
            val elapsed = android.os.SystemClock.elapsedRealtime() - started
            val text = result.text
            assertTrue("production OCR must return complete text state", result.status == OcrStatus.COMPLETE)
            assertTrue("production OCR must recognize Latin", text.contains("PaperScan"))
            assertTrue("production OCR must recognize Japanese", text.contains("日本"))
            assertTrue("production OCR must recognize Chinese", text.contains("文"))
            assertTrue("production OCR must recognize Korean", text.contains("한국"))
            assertTrue("overlapping script recognizers must not duplicate the same lines", result.lines.size <= 5)
            assertTrue(result.lines.all { line ->
                line.bounds.left in 0f..1f && line.bounds.top in 0f..1f &&
                    line.bounds.right in 0f..1f && line.bounds.bottom in 0f..1f
            })
            Log.i("PaperScanOcrPoc", "script=mixed-production elapsedMs=$elapsed lines=${result.lines.size} text=${result.text}")
        } finally {
            bitmap.recycle()
        }
        Unit
    }

    private fun textImage(text: String): Bitmap = Bitmap.createBitmap(1800, 260, Bitmap.Config.ARGB_8888).apply {
        eraseColor(Color.WHITE)
        Canvas(this).drawText(
            text,
            48f,
            190f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = 110f
                typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            }
        )
    }

    private fun mixedTextImage(): Bitmap = Bitmap.createBitmap(3200, 900, Bitmap.Config.ARGB_8888).apply {
        eraseColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 100f
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }
        Canvas(this).apply {
            drawText("PaperScan", 48f, 160f, paint)
            drawText("日本語テスト", 48f, 340f, paint)
            drawText("简体中文测试 / 繁體中文測試", 48f, 520f, paint)
            drawText("한국어", 48f, 700f, paint)
        }
    }

    private data class OcrCase(
        val label: String,
        val expectedText: String,
        val expectedToken: String,
        val createRecognizer: () -> TextRecognizer
    )

}
