package com.paperscan.processing

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.paperscan.image.SourceImageLoader
import com.paperscan.model.CornerPoints
import com.paperscan.model.DetectionConfidence
import com.paperscan.model.NormalizedPoint
import com.paperscan.model.PageItem
import com.paperscan.model.PageStatus
import com.paperscan.model.ScanMode
import com.paperscan.model.SourceType
import com.paperscan.storage.WorkingFileStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class OpenCvDocumentProcessorInstrumentedTest {
    @Test
    fun detectBitmap_perspectivePaperFixture_returnsUsableCorners() {
        val processor = processor()

        val fixture = fixture("perspective_a4.png")
        try {
            val result = processor.detectBitmap(fixture)

            assertTrue(result.confidence == DetectionConfidence.HIGH || result.confidence == DetectionConfidence.MEDIUM)
            assertNotNull(result.corners)
            val corners = requireNotNull(result.corners)
            assertTrue(corners.topLeft.x < 0.25f && corners.topLeft.y < 0.30f)
            assertTrue(corners.bottomRight.x > 0.70f && corners.bottomRight.y > 0.70f)
        } finally {
            fixture.recycle()
        }
    }

    @Test
    fun detectBitmap_blankFixture_doesNotReportHighConfidencePaper() {
        val bitmap = fixture("no_paper.png")
        try {
            val result = processor().detectBitmap(bitmap)

            assertEquals(DetectionConfidence.LOW, result.confidence)
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun processForExport_manualCorners_appliesPerspectiveAndKeepsBoundedSize() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val files = WorkingFileStore(context)
        val pageId = "perspective-export"
        val source = files.fallbackImportFile(pageId, "png")
        val bitmap = fixture("perspective_a4.png")
        try {
            FileOutputStream(source).use { output -> assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) }
        } finally {
            bitmap.recycle()
        }
        val page = PageItem(
            id = pageId,
            sourceType = SourceType.IMPORT,
            sourceUri = null,
            workingRelativePath = files.relativeFallbackPath(source),
            thumbnailRelativePath = null,
            sourceExifRotationDegrees = 0,
            sourceExifFlipped = false,
            rotationQuarterTurns = 0,
            scanMode = ScanMode.AUTO,
            status = PageStatus.READY,
            corners = CornerPoints(
                NormalizedPoint(0.14f, 0.13f), NormalizedPoint(0.86f, 0.10f),
                NormalizedPoint(0.80f, 0.88f), NormalizedPoint(0.18f, 0.84f)
            )
        )

        val output = processor(files).processForExport(page)

        try {
            assertTrue(maxOf(output.width, output.height) <= 3000)
            assertTrue(output.width > 300 && output.height > 400)
        } finally {
            output.recycle()
            source.delete()
        }
    }

    @Test
    fun processForExport_allDeterministicFixturesAndModes_returnsDisplayableBitmap() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val files = WorkingFileStore(context)
        val names = listOf(
            "overhead_a4.png", "perspective_a4.png", "white_table.png", "wood_table.png",
            "dark_document.png", "shadow.png", "image_edge.png", "colored_document.png",
            "circuit_lines.png", "math_thin_lines.png", "no_paper.png"
        )

        names.forEach { name ->
            ScanMode.entries.forEach { mode ->
                val id = "mode-${name.substringBefore('.')}-${mode.name.lowercase()}"
                val source = files.fallbackImportFile(id, "png")
                val input = fixture(name)
                try {
                    FileOutputStream(source).use { output -> assertTrue(input.compress(Bitmap.CompressFormat.PNG, 100, output)) }
                    val page = PageItem(
                        id = id,
                        sourceType = SourceType.IMPORT,
                        sourceUri = null,
                        workingRelativePath = files.relativeFallbackPath(source),
                        thumbnailRelativePath = null,
                        sourceExifRotationDegrees = 0,
                        sourceExifFlipped = false,
                        rotationQuarterTurns = 0,
                        scanMode = mode,
                        status = PageStatus.READY
                    )
                    val output = processor(files).processForExport(page)
                    try {
                        assertTrue("$name / $mode must have pixels", output.width > 0 && output.height > 0)
                        assertTrue("$name / $mode must remain bounded", maxOf(output.width, output.height) <= 3000)
                        if (name == "colored_document.png" && mode == ScanMode.COLOR) {
                            assertTrue("COLOR must retain chromatic information", containsChromaticPixels(output))
                        }
                    } finally {
                        output.recycle()
                    }
                } finally {
                    input.recycle()
                    source.delete()
                }
            }
        }
    }

    private fun processor(files: WorkingFileStore = WorkingFileStore(InstrumentationRegistry.getInstrumentation().targetContext)): OpenCvDocumentProcessor {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return OpenCvDocumentProcessor(SourceImageLoader(context, files))
    }

    private fun fixture(name: String): Bitmap =
        InstrumentationRegistry.getInstrumentation().context.assets.open("fixtures/$name").use {
            requireNotNull(BitmapFactory.decodeStream(it))
        }

    private fun containsChromaticPixels(bitmap: Bitmap): Boolean {
        val stride = maxOf(1, minOf(bitmap.width, bitmap.height) / 40)
        var count = 0
        for (y in 0 until bitmap.height step stride) {
            for (x in 0 until bitmap.width step stride) {
                val color = bitmap.getPixel(x, y)
                val spread = maxOf(android.graphics.Color.red(color), android.graphics.Color.green(color), android.graphics.Color.blue(color)) -
                    minOf(android.graphics.Color.red(color), android.graphics.Color.green(color), android.graphics.Color.blue(color))
                if (spread > 24) count++
            }
        }
        return count >= 3
    }
}
