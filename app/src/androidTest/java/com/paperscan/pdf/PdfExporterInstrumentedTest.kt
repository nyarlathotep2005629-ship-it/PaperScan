package com.paperscan.pdf

import android.graphics.Bitmap
import android.graphics.Color
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
import com.paperscan.storage.WorkingFileStore
import com.paperscan.processing.OpenCvDocumentProcessor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class PdfExporterInstrumentedTest {
    @Test
    fun export_twoPages_createsA4PointSizedPdfInSessionOrder() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val files = WorkingFileStore(context)
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

        val result = exporter.export(session) { _, _ -> }

        assertTrue(result is ExportResult.Success)
        val file = (result as ExportResult.Success).tempFile
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                assertEquals(2, renderer.pageCount)
                renderer.openPage(0).use { page ->
                    assertEquals(595, page.width)
                    assertEquals(842, page.height)
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
}
