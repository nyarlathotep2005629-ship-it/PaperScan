package com.paperscan.pdf

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.paperscan.image.SourceImageLoader
import com.paperscan.model.PageItem
import com.paperscan.model.PageStatus
import com.paperscan.model.ScanMode
import com.paperscan.model.ScanSession
import com.paperscan.model.SourceType
import com.paperscan.processing.OpenCvDocumentProcessor
import com.paperscan.storage.WorkingFileStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Sequential 1/10/20/30 page regression using a deterministic, non-personal fixture. */
@RunWith(AndroidJUnit4::class)
class PdfExporterStressInstrumentedTest {
    @Test
    fun export_1_10_20_30Pages_completesWithoutRetainingAllPagesInMemory() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val testContext = InstrumentationRegistry.getInstrumentation().context
        val files = WorkingFileStore(context)
        val processor = OpenCvDocumentProcessor(SourceImageLoader(context, files))
        val exporter = PdfExporter(processor, files)

        listOf(1, 10, 20, 30).forEach { count ->
            files.deleteSessionFiles()
            val pages = (1..count).map { index ->
                val id = "stress-$count-$index"
                val source = files.fallbackImportFile(id, "png")
                testContext.assets.open("fixtures/overhead_a4.png").use { input -> source.outputStream().use(input::copyTo) }
                PageItem(
                    id = id,
                    sourceType = SourceType.IMPORT,
                    sourceUri = null,
                    workingRelativePath = files.relativeFallbackPath(source),
                    thumbnailRelativePath = null,
                    sourceExifRotationDegrees = 0,
                    sourceExifFlipped = false,
                    rotationQuarterTurns = 0,
                    scanMode = ScanMode.AUTO,
                    status = PageStatus.READY
                )
            }
            val session = ScanSession(
                id = "stress-$count",
                createdAtEpochMillis = 0,
                updatedAtEpochMillis = 0,
                pages = pages,
                selectedPageId = pages.first().id
            )
            val startedAt = android.os.SystemClock.elapsedRealtime()
            val result = exporter.export(session) { _, _ -> }
            val elapsed = android.os.SystemClock.elapsedRealtime() - startedAt

            assertTrue("$count pages must export", result is ExportResult.Success)
            val success = result as ExportResult.Success
            assertEquals(count, success.pageCount)
            assertTrue("$count pages must create a non-empty PDF", success.tempFile.length() > 0L)
            Log.i("PaperScanPerf", "pages=$count elapsedMs=$elapsed pdfBytes=${success.tempFile.length()}")

            success.tempFile.delete()
            files.deleteSessionFiles()
            files.cleanupStaleCache()
        }
    }
}
