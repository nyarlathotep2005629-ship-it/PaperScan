package com.paperscan.processing

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.FileOutputStream
import android.util.Log
import org.json.JSONObject
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc

@RunWith(AndroidJUnit4::class)
class OpenCvDocumentProcessorInstrumentedTest {
    @Test
    fun detectGroundTruth_TC_DET_001_measuresIndependentSyntheticCornerErrorAndIoU() {
        val context = InstrumentationRegistry.getInstrumentation().context
        val dataset = context.assets.open("fixtures/detection_ground_truth.json").bufferedReader().use { reader ->
            JSONObject(reader.readText())
        }
        val truth = dataset.getJSONArray("fixtures").getJSONObject(0)
        val datasetId = dataset.getString("datasetId")
        val bitmap = fixture(truth.getString("file"))
        try {
            assertEquals(truth.getInt("width"), bitmap.width)
            assertEquals(truth.getInt("height"), bitmap.height)
            val truthArray = truth.getJSONArray("corners")
            val expected = List(4) { index ->
                val point = truthArray.getJSONArray(index)
                NormalizedPoint(point.getDouble(0).toFloat(), point.getDouble(1).toFloat())
            }
            val actual = requireNotNull(processor().detectBitmap(bitmap).corners)
            val meanCornerErrorPx = expected.zip(actual.points).map { (a, b) ->
                kotlin.math.hypot(((a.x - b.x) * bitmap.width).toDouble(), ((a.y - b.y) * bitmap.height).toDouble())
            }.average()
            val iou = polygonIou(expected, actual.points, bitmap.width, bitmap.height)

            Log.i("PaperScanDetectionMetric", "dataset=$datasetId fixture=${truth.getString("file")} meanCornerErrorPx=$meanCornerErrorPx iou=$iou")
            assertTrue("independently annotated paper should reach baseline IoU >= 0.80; measured $iou", iou >= 0.80)
            assertTrue("mean corner error must stay below 5% of the long edge; measured $meanCornerErrorPx", meanCornerErrorPx < bitmap.width * 0.05)
        } finally {
            bitmap.recycle()
        }
    }

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
    fun detectBitmap_representativeDocuments_returnValidCandidates() {
        val processor = processor()
        listOf("overhead_a4.png", "perspective_a4.png", "white_table.png", "wood_table.png", "shadow.png")
            .forEach { name ->
                val bitmap = fixture(name)
                try {
                    val result = processor.detectBitmap(bitmap)
                    assertNotNull("$name must produce a candidate", result.corners)
                    assertTrue("$name candidate must be valid", requireNotNull(result.corners).isValid())
                } finally {
                    bitmap.recycle()
                }
            }
    }

    @Test
    fun detectBitmap_rotatedDocument_returnsCoordinatesInRotatedBitmapSpace() {
        val source = fixture("perspective_a4.png")
        val rotated = Bitmap.createBitmap(
            source,
            0,
            0,
            source.width,
            source.height,
            Matrix().apply { postRotate(90f) },
            true
        )
        try {
            val result = processor().detectBitmap(rotated)

            assertNotNull(result.corners)
            assertTrue(requireNotNull(result.corners).isValid())
            assertEquals(rotated.width, result.previewWidth)
            assertEquals(rotated.height, result.previewHeight)
        } finally {
            rotated.recycle()
            if (!source.isRecycled) source.recycle()
        }
    }

    @Test
    fun processForExport_manualCorners_appliesPerspectiveAndKeepsBoundedSize() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val files = WorkingFileStore.forIsolatedTest(context)
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

        val activeProcessor = processor(files)
        val before = activeProcessor.processForExport(page.copy(corners = CornerPoints.fullImage()))
        val output = activeProcessor.processForExport(page)

        try {
            assertTrue(maxOf(output.width, output.height) <= 3000)
            assertTrue(output.width > 300 && output.height > 400)
            assertNotEquals("manual corners must change perspective output width", before.width, output.width)
        } finally {
            before.recycle()
            output.recycle()
            source.delete()
        }
    }

    @Test
    fun detect_largeSource_usesDownscaledWorkingBitmap() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val files = WorkingFileStore.forIsolatedTest(context)
        val pageId = "large-detection"
        val sourceFile = files.fallbackImportFile(pageId, "png")
        val fixture = fixture("perspective_a4.png")
        val large = Bitmap.createScaledBitmap(fixture, 4000, 3200, true)
        try {
            FileOutputStream(sourceFile).use { output ->
                assertTrue(large.compress(Bitmap.CompressFormat.PNG, 100, output))
            }
        } finally {
            large.recycle()
            fixture.recycle()
        }
        val page = PageItem(
            id = pageId,
            sourceType = SourceType.IMPORT,
            sourceUri = null,
            workingRelativePath = files.relativeFallbackPath(sourceFile),
            thumbnailRelativePath = null,
            sourceExifRotationDegrees = 0,
            sourceExifFlipped = false,
            rotationQuarterTurns = 0,
            scanMode = ScanMode.AUTO,
            status = PageStatus.NEEDS_REVIEW
        )

        try {
            val result = processor(files).detect(page)
            assertTrue(result.previewWidth <= 1600)
            assertTrue(result.previewHeight <= 1600)
            assertNotNull(result.corners)
        } finally {
            sourceFile.delete()
        }
    }

    @Test
    fun processForExport_allDeterministicFixturesAndModes_returnsDisplayableBitmap() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val files = WorkingFileStore.forIsolatedTest(context)
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

    private fun processor(files: WorkingFileStore = WorkingFileStore.forIsolatedTest(InstrumentationRegistry.getInstrumentation().targetContext)): OpenCvDocumentProcessor {
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

    private fun polygonIou(
        expected: List<NormalizedPoint>,
        actual: List<NormalizedPoint>,
        width: Int,
        height: Int
    ): Double {
        fun polygon(points: List<NormalizedPoint>): MatOfPoint = MatOfPoint(
            *points.map { point -> Point(point.x.toDouble() * (width - 1), point.y.toDouble() * (height - 1)) }.toTypedArray()
        )
        val expectedPolygon = polygon(expected)
        val actualPolygon = polygon(actual)
        val expectedMask = Mat.zeros(height, width, CvType.CV_8UC1)
        val actualMask = Mat.zeros(height, width, CvType.CV_8UC1)
        val intersection = Mat()
        val union = Mat()
        try {
            Imgproc.fillPoly(expectedMask, listOf(expectedPolygon), Scalar(255.0))
            Imgproc.fillPoly(actualMask, listOf(actualPolygon), Scalar(255.0))
            Core.bitwise_and(expectedMask, actualMask, intersection)
            Core.bitwise_or(expectedMask, actualMask, union)
            return Core.countNonZero(intersection).toDouble() / Core.countNonZero(union).coerceAtLeast(1)
        } finally {
            union.release()
            intersection.release()
            actualMask.release()
            expectedMask.release()
            actualPolygon.release()
            expectedPolygon.release()
        }
    }
}
