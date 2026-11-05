package com.paperscan.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.paperscan.image.SourceImageLoader
import com.paperscan.model.PageItem
import com.paperscan.model.PageStatus
import com.paperscan.model.ScanMode
import com.paperscan.model.ScanSession
import com.paperscan.model.SourceType
import com.paperscan.processing.DetectionResult
import com.paperscan.processing.DocumentProcessor
import com.paperscan.storage.WorkingFileStore
import com.paperscan.processing.OpenCvDocumentProcessor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.FileOutputStream
import java.security.MessageDigest
import android.util.Log

@RunWith(AndroidJUnit4::class)
class PdfExporterInstrumentedTest {
    @Test
    fun export_qualityPresets_measureSyntheticA4RasterAndPdfSize() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val files = WorkingFileStore.forIsolatedTest(context)
        val source = Bitmap.createBitmap(3000, 4242, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.WHITE)
            Canvas(this).apply {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; strokeWidth = 3f }
                for (y in 180..4060 step 120) drawLine(120f, y.toFloat(), 2880f, y.toFloat(), paint)
                for (x in 120..2880 step 160) drawLine(x.toFloat(), 180f, x.toFloat(), 4060f, paint)
                paint.textSize = 72f
                paint.color = Color.rgb(20, 70, 180)
                drawText("PaperScan PDF quality fixture 0123456789", 150f, 120f, paint)
                paint.color = Color.rgb(180, 30, 30)
                drawRect(800f, 1400f, 2200f, 2700f, paint)
            }
        }
        val page = PageItem(
            id = "quality-measurement",
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
        val processor = object : DocumentProcessor {
            override suspend fun detect(page: PageItem): DetectionResult = error("Not used")
            override suspend fun loadOriginalPreview(page: PageItem): Bitmap = error("Not used")
            override suspend fun createCorrectedPreview(page: PageItem): Bitmap = error("Not used")
            override suspend fun processForExport(page: PageItem): Bitmap =
                requireNotNull(source.copy(Bitmap.Config.ARGB_8888, false))
        }
        val measured = mutableListOf<Pair<PdfQuality, Long>>()
        try {
            PdfQuality.entries.forEach { quality ->
                val session = ScanSession(
                    id = "quality-${quality.name.lowercase()}",
                    createdAtEpochMillis = 0,
                    updatedAtEpochMillis = 0,
                    pages = listOf(page),
                    selectedPageId = page.id
                )
                val result = PdfExporter(processor, files).export(
                    session,
                    settings = PdfExportSettings(quality = quality)
                ) { _, _ -> }
                assertTrue("${quality.name} PDF must be created", result is ExportResult.Success)
                val output = (result as ExportResult.Success).tempFile
                val pdfBytes = output.readBytes().toString(Charsets.ISO_8859_1)
                val dimensions = Regex("/Width (\\d+) /Height (\\d+)").find(pdfBytes)
                assertTrue("${quality.name} must embed a raster", dimensions != null)
                Log.i(
                    "PaperScanPdfQuality",
                    "quality=${quality.name} raster=${dimensions!!.groupValues[1]}x${dimensions.groupValues[2]} bytes=${output.length()}"
                )
                measured += quality to output.length()
                output.delete()
            }
        } finally {
            source.recycle()
            files.cleanupStaleCache()
        }
        assertTrue("higher quality should produce a larger fixture PDF", measured[0].second > measured[1].second)
        assertTrue("standard quality should produce a larger fixture PDF than compact", measured[1].second > measured[2].second)
    }

    @Test
    fun export_selectedSettings_appliesLetterLandscapeAndGrayscale() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val files = WorkingFileStore.forIsolatedTest(context)
        val page = PageItem(
            id = "configured-export",
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
        val session = ScanSession(
            id = "configured-export",
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
                Bitmap.createBitmap(595, 842, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(20, 100, 230)) }
        }

        val result = PdfExporter(processor, files).export(
            session,
            settings = PdfExportSettings(
                quality = PdfQuality.COMPACT,
                paperSize = PdfPaperSize.LETTER,
                orientation = PdfOrientation.LANDSCAPE,
                colorMode = PdfColorMode.GRAYSCALE
            )
        ) { _, _ -> }

        assertTrue(result is ExportResult.Success)
        val output = (result as ExportResult.Success).tempFile
        ParcelFileDescriptor.open(output, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                renderer.openPage(0).use { rendered ->
                    assertEquals(792, rendered.width)
                    assertEquals(612, rendered.height)
                    val bitmap = Bitmap.createBitmap(200, 155, Bitmap.Config.ARGB_8888)
                    try {
                        rendered.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val center = bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
                        assertTrue(kotlin.math.abs(Color.red(center) - Color.green(center)) < 8)
                        assertTrue(kotlin.math.abs(Color.green(center) - Color.blue(center)) < 8)
                    } finally {
                        bitmap.recycle()
                    }
                }
            }
        }
        output.delete()
        files.cleanupStaleCache()
    }

    @Test
    fun export_blackWhiteModeThresholdsRenderedPage() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val files = WorkingFileStore.forIsolatedTest(context)
        val page = PageItem(
            id = "black-white-export",
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
        val session = ScanSession(
            id = "black-white-export",
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
                Bitmap.createBitmap(595, 842, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(20, 100, 230)) }
        }
        val result = PdfExporter(processor, files).export(
            session,
            settings = PdfExportSettings(colorMode = PdfColorMode.BLACK_WHITE)
        ) { _, _ -> }

        assertTrue(result is ExportResult.Success)
        val output = (result as ExportResult.Success).tempFile
        ParcelFileDescriptor.open(output, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                val bitmap = Bitmap.createBitmap(100, 142, Bitmap.Config.ARGB_8888)
                try {
                    renderer.openPage(0).use { it.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY) }
                    val center = bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
                    assertTrue("dark source pixels must become black", Color.red(center) < 32)
                    assertTrue(kotlin.math.abs(Color.red(center) - Color.green(center)) < 8)
                } finally {
                    bitmap.recycle()
                }
            }
        }
        output.delete()
        files.cleanupStaleCache()
    }

    @Test
    fun export_twoPages_createsA4PointSizedPdfInSessionOrder() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val files = WorkingFileStore.forIsolatedTest(context)
        files.deleteSessionFiles()
        files.cleanupStaleCache()
        val first = createPage(files, "first", Color.RED)
        val second = createPage(files, "second", Color.BLUE)
        val session = ScanSession(
            id = "pdf-test",
            createdAtEpochMillis = 1,
            updatedAtEpochMillis = 1,
            pages = listOf(first, second),
            selectedPageId = first.id
        )
        val exporter = PdfExporter(OpenCvDocumentProcessor(SourceImageLoader(context, files)), files)
        val firstSource = files.resolveWorkingFile(requireNotNull(first.workingRelativePath))
        val sourceDigest = sha256(firstSource.readBytes())

        val result = exporter.export(session) { _, _ -> }

        assertTrue(result is ExportResult.Success)
        val file = (result as ExportResult.Success).tempFile
        assertEquals("PDF export must not alter the source image", sourceDigest, sha256(firstSource.readBytes()))
        val objectText = file.readBytes().toString(Charsets.ISO_8859_1)
        assertTrue("PDF images must use DCT JPEG encoding", objectText.contains("/Filter /DCTDecode"))
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                assertEquals(2, renderer.pageCount)
                renderer.openPage(0).use { page ->
                    assertEquals(595, page.width)
                    assertEquals(842, page.height)
                    assertDominantColor(page, Color.RED)
                }
                renderer.openPage(1).use { page ->
                    assertDominantColor(page, Color.BLUE)
                }
            }
        }
        files.deleteSessionFiles()
        files.cleanupStaleCache()
    }

    @Test
    fun export_largeProcessedPage_embeds200DpiBoundedJpegRaster() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val files = WorkingFileStore.forIsolatedTest(context)
        files.cleanupStaleCache()
        val page = PageItem(
            id = "large-export",
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
        val processor = object : DocumentProcessor {
            override suspend fun detect(page: PageItem): DetectionResult = error("Not used by PDF export")

            override suspend fun loadOriginalPreview(page: PageItem): Bitmap = error("Not used by PDF export")

            override suspend fun createCorrectedPreview(page: PageItem): Bitmap = error("Not used by PDF export")

            override suspend fun processForExport(page: PageItem): Bitmap =
                Bitmap.createBitmap(3000, 4242, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(10, 120, 220)) }
        }
        val session = ScanSession(
            id = "large-export",
            createdAtEpochMillis = 0,
            updatedAtEpochMillis = 0,
            pages = listOf(page),
            selectedPageId = page.id
        )

        val result = PdfExporter(processor, files).export(session) { _, _ -> }

        assertTrue(result is ExportResult.Success)
        val file = (result as ExportResult.Success).tempFile
        val objectText = file.readBytes().toString(Charsets.ISO_8859_1)
        assertTrue(objectText.contains("/Width 1653 /Height 2337"))
        assertTrue(objectText.contains("/Filter /DCTDecode"))
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                assertEquals(1, renderer.pageCount)
                renderer.openPage(0).use { rendered -> assertDominantColor(rendered, Color.rgb(10, 120, 220)) }
            }
        }
        files.cleanupStaleCache()
    }

    @Test
    fun export_textAndColorFixtures_rendersBothScanModes() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val files = WorkingFileStore.forIsolatedTest(context)
        files.deleteSessionFiles()
        files.cleanupStaleCache()
        val text = createFixturePage(files, instrumentation.context, "text", "math_thin_lines.png", ScanMode.TEXT)
        val color = createFixturePage(files, instrumentation.context, "color", "colored_document.png", ScanMode.COLOR)
        val session = ScanSession(
            id = "quality-fixtures",
            createdAtEpochMillis = 0,
            updatedAtEpochMillis = 0,
            pages = listOf(text, color),
            selectedPageId = text.id
        )

        val result = PdfExporter(OpenCvDocumentProcessor(SourceImageLoader(context, files)), files).export(session) { _, _ -> }

        assertTrue(result is ExportResult.Success)
        val file = (result as ExportResult.Success).tempFile
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                assertEquals(2, renderer.pageCount)
                (0 until renderer.pageCount).forEach { index ->
                    renderer.openPage(index).use(::assertContainsVisibleContent)
                }
            }
        }
        files.deleteSessionFiles()
        files.cleanupStaleCache()
    }

    private fun createPage(files: WorkingFileStore, id: String, color: Int): PageItem {
        val file = files.fallbackImportFile(id, "png")
        val bitmap = Bitmap.createBitmap(595, 842, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        try {
            FileOutputStream(file).use { output ->
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            }
        } finally {
            bitmap.recycle()
        }
        return PageItem(
            id = id,
            sourceType = SourceType.IMPORT,
            sourceUri = null,
            workingRelativePath = files.relativeFallbackPath(file),
            thumbnailRelativePath = null,
            sourceExifRotationDegrees = 0,
            sourceExifFlipped = false,
            rotationQuarterTurns = 0,
            scanMode = ScanMode.AUTO,
            status = PageStatus.READY
        )
    }

    private fun createFixturePage(
        files: WorkingFileStore,
        testContext: android.content.Context,
        id: String,
        assetName: String,
        mode: ScanMode
    ): PageItem {
        val file = files.fallbackImportFile(id, "png")
        testContext.assets.open("fixtures/$assetName").use { input -> file.outputStream().use(input::copyTo) }
        return PageItem(
            id = id,
            sourceType = SourceType.IMPORT,
            sourceUri = null,
            workingRelativePath = files.relativeFallbackPath(file),
            thumbnailRelativePath = null,
            sourceExifRotationDegrees = 0,
            sourceExifFlipped = false,
            rotationQuarterTurns = 0,
            scanMode = mode,
            status = PageStatus.READY
        )
    }

    private fun assertDominantColor(page: PdfRenderer.Page, expected: Int) {
        val rendered = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        try {
            page.render(rendered, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            val actual = rendered.getPixel(16, 16)
            when (expected) {
                Color.RED -> assertTrue("red page must remain red", Color.red(actual) > Color.blue(actual) * 2)
                else -> assertTrue("blue page must remain blue", Color.blue(actual) > Color.red(actual) * 2)
            }
        } finally {
            rendered.recycle()
        }
    }

    private fun assertContainsVisibleContent(page: PdfRenderer.Page) {
        val rendered = Bitmap.createBitmap(100, 142, Bitmap.Config.ARGB_8888)
        try {
            page.render(rendered, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            val pixels = IntArray(rendered.width * rendered.height)
            rendered.getPixels(pixels, 0, rendered.width, 0, 0, rendered.width, rendered.height)
            assertTrue("rendered fixture must contain content", pixels.any { it != Color.WHITE })
        } finally {
            rendered.recycle()
        }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }
}
