package com.paperscan.pdf

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Color
import android.os.ParcelFileDescriptor
import android.graphics.pdf.PdfRenderer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.paperscan.model.NormalizedTextBounds
import com.paperscan.model.OcrStatus
import com.paperscan.model.OcrTextLine
import com.paperscan.model.PageItem
import com.paperscan.model.PageOcrResult
import com.paperscan.model.PageStatus
import com.paperscan.model.ScanMode
import com.paperscan.model.ScanSession
import com.paperscan.model.SourceType
import com.paperscan.model.ocrRevision
import com.paperscan.processing.DetectionResult
import com.paperscan.processing.DocumentProcessor
import com.paperscan.storage.SessionStore
import com.paperscan.storage.WorkingFileStore
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SearchablePdfExporterInstrumentedTest {
    @Test
    fun export_TC_PDF_007_longCjkAndSupplementaryTextRoundTripsAcrossPages() = kotlinx.coroutines.runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        PDFBoxResourceLoader.init(object : ContextWrapper(context) {
            override fun getApplicationContext(): Context = instrumentation.context
        })
        val files = WorkingFileStore.forIsolatedTest(context)
        val firstText = "頁一 😀 " + "文".repeat(110)
        val secondText = "二ページ 한국어"
        val first = searchablePage("unicode-first", firstText)
        val second = searchablePage("unicode-second", secondText)
        val failed = searchablePage("ocr-failed", text = null).copy(
            ocrResult = PageOcrResult(
                revision = "failed-revision",
                status = OcrStatus.FAILED,
                lines = emptyList(),
                updatedAtEpochMillis = 1
            )
        )
        val processor = object : DocumentProcessor {
            override suspend fun detect(page: PageItem): DetectionResult = error("Not used")
            override suspend fun loadOriginalPreview(page: PageItem): Bitmap = error("Not used")
            override suspend fun createCorrectedPreview(page: PageItem): Bitmap = error("Not used")
            override suspend fun processForExport(page: PageItem): Bitmap =
                Bitmap.createBitmap(595, 842, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        }
        val session = ScanSession(
            id = "pdf-unicode-pages",
            createdAtEpochMillis = 0,
            updatedAtEpochMillis = 0,
            pages = listOf(first, second, failed),
            selectedPageId = first.id
        )

        val result = PdfExporter(processor, files).export(session, includeSearchableText = true) { _, _ -> }
        assertTrue(result is ExportResult.Success)
        val output = (result as ExportResult.Success).tempFile
        try {
            val raw = output.readBytes().toString(Charsets.ISO_8859_1)
            assertTrue(raw.startsWith("%PDF-1.4"))
            assertTrue(raw.contains("xref\n"))
            assertTrue(raw.contains("trailer\n"))
            PDDocument.load(output).use { document ->
                assertEquals(3, document.numberOfPages)
                val extracted = PDFTextStripper().getText(document)
                assertTrue("long CJK text and surrogate-pair Unicode must retain ToUnicode mapping", extracted.contains(firstText))
                assertTrue(extracted.contains(secondText))
                assertTrue(extracted.indexOf(firstText) < extracted.indexOf(secondText))
                assertFalse("failed OCR page must not create searchable text", extracted.contains("ocr-failed"))
            }
        } finally {
            output.delete()
            files.cleanupStaleCache()
        }
    }

    @Test
    fun export_cjkTextLayer_hasToUnicodeAndStaysInvisible() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        PDFBoxResourceLoader.init(object : ContextWrapper(context) {
            override fun getApplicationContext(): Context = instrumentation.context
        })
        val files = WorkingFileStore.forIsolatedTest(context)
        val expected = "PaperScan 日本語 简体中文 繁體中文 한국어"
        val basePage = PageItem(
            id = "searchable-pdf",
            sourceType = SourceType.IMPORT,
            sourceUri = null,
            workingRelativePath = null,
            thumbnailRelativePath = null,
            sourceExifRotationDegrees = 0,
            sourceExifFlipped = false,
            rotationQuarterTurns = 0,
            scanMode = ScanMode.COLOR,
            status = PageStatus.READY
        )
        val page = basePage.copy(ocrResult = PageOcrResult(
            revision = basePage.ocrRevision(),
            status = OcrStatus.COMPLETE,
            lines = listOf(OcrTextLine(expected, NormalizedTextBounds(0.05f, 0.08f, 0.9f, 0.13f))),
            updatedAtEpochMillis = 0
        ))
        val session = ScanSession(
            id = "searchable-pdf",
            createdAtEpochMillis = 0,
            updatedAtEpochMillis = 0,
            pages = listOf(page),
            selectedPageId = page.id
        )
        val processor = object : DocumentProcessor {
            override suspend fun detect(page: PageItem): DetectionResult = error("Not used")
            override suspend fun loadOriginalPreview(page: PageItem): Bitmap = error("Not used")
            override suspend fun createCorrectedPreview(page: PageItem): Bitmap = error("Not used")
            override suspend fun processForExport(page: PageItem): Bitmap =
                Bitmap.createBitmap(595, 842, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        }

        val result = kotlinx.coroutines.runBlocking {
            PdfExporter(processor, files).export(session, includeSearchableText = true) { _, _ -> }
        }
        assertTrue(result is ExportResult.Success)
        val output = (result as ExportResult.Success).tempFile
        PDDocument.load(output).use { document ->
            val pageResources = requireNotNull(document.getPage(0).resources)
            val fonts = pageResources.fontNames.mapNotNull(pageResources::getFont)
            assertTrue(fonts.any { it.cosObject.getDictionaryObject(COSName.TO_UNICODE) != null })
            val extracted = PDFTextStripper().getText(document).replace("\n", "").replace("\r", "")
            assertTrue("PDF text extraction must preserve all supported scripts", extracted.contains(expected))
        }
        ParcelFileDescriptor.open(output, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                assertEquals(1, renderer.pageCount)
                val pageBitmap = Bitmap.createBitmap(100, 142, Bitmap.Config.ARGB_8888)
                try {
                    renderer.openPage(0).use { it.render(pageBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY) }
                    val pixels = IntArray(pageBitmap.width * pageBitmap.height)
                    pageBitmap.getPixels(pixels, 0, pageBitmap.width, 0, 0, pageBitmap.width, pageBitmap.height)
                    assertFalse("Invisible text must not render above the page image", pixels.any { Color.red(it) < 245 })
                } finally {
                    pageBitmap.recycle()
                }
            }
        }
        output.delete()
        files.cleanupStaleCache()
    }

    private fun searchablePage(id: String, text: String?): PageItem {
        val base = PageItem(
            id = id,
            sourceType = SourceType.IMPORT,
            sourceUri = null,
            workingRelativePath = null,
            thumbnailRelativePath = null,
            sourceExifRotationDegrees = 0,
            sourceExifFlipped = false,
            rotationQuarterTurns = 0,
            scanMode = ScanMode.COLOR,
            status = PageStatus.READY
        )
        return if (text == null) base else base.copy(ocrResult = PageOcrResult(
            revision = base.ocrRevision(),
            status = OcrStatus.COMPLETE,
            lines = listOf(OcrTextLine(text, NormalizedTextBounds(0.05f, 0.08f, 0.9f, 0.13f))),
            updatedAtEpochMillis = 1
        ))
    }
}
