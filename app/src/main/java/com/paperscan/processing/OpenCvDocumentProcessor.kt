package com.paperscan.processing

import android.graphics.Bitmap
import com.paperscan.image.SourceImageLoader
import com.paperscan.model.CornerPoints
import com.paperscan.model.DetectionConfidence
import com.paperscan.model.NormalizedPoint
import com.paperscan.model.PageItem
import com.paperscan.model.PerspectiveSizing
import com.paperscan.model.PixelPoint
import com.paperscan.model.ScanMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.sqrt

/** OpenCV-only implementation. All callers receive EXIF- and user-rotation-corrected bitmaps. */
class OpenCvDocumentProcessor(
    private val imageLoader: SourceImageLoader
) : DocumentProcessor {
    override suspend fun detect(page: PageItem): DetectionResult = withContext(Dispatchers.Default) {
        val preview = imageLoader.decodePreview(page, DETECTION_LONG_EDGE_PX)
        try {
            detectBitmap(preview)
        } finally {
            preview.recycle()
        }
    }

    override suspend fun loadOriginalPreview(page: PageItem): Bitmap =
        imageLoader.decodePreview(page, PREVIEW_LONG_EDGE_PX)

    override suspend fun createCorrectedPreview(page: PageItem): Bitmap = withContext(Dispatchers.Default) {
        val source = imageLoader.decodePreview(page, PREVIEW_LONG_EDGE_PX)
        process(source, page).also { if (it === source) source.recycle() }
    }

    override suspend fun processForExport(page: PageItem): Bitmap = withContext(Dispatchers.Default) {
        val source = imageLoader.decodeForExport(page, PerspectiveSizing.DEFAULT_MAX_LONG_EDGE_PX)
        process(source, page).also { if (it === source) source.recycle() }
    }

    /** Kept public for deterministic instrumented fixture tests; it does not retain the input bitmap. */
    fun detectBitmap(bitmap: Bitmap): DetectionResult {
        ensureInitialized()
        val rgba = Mat()
        val gray = Mat()
        val blurred = Mat()
        val edges = Mat()
        val hierarchy = Mat()
        val contours = mutableListOf<MatOfPoint>()
        try {
            Utils.bitmapToMat(bitmap, rgba)
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.GaussianBlur(gray, blurred, Size(5.0, 5.0), 0.0)
            val median = Core.mean(blurred).`val`[0]
            val lower = (median * 0.66).coerceIn(25.0, 110.0)
            Imgproc.Canny(blurred, edges, lower, (lower * 2.0).coerceAtMost(220.0))
            val morphologyKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
            try {
                Imgproc.morphologyEx(
                    edges,
                    edges,
                    Imgproc.MORPH_CLOSE,
                    morphologyKernel
                )
            } finally {
                morphologyKernel.release()
            }
            Imgproc.findContours(edges, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)
            val candidate = contours.mapNotNull { contour -> candidateFor(contour, bitmap.width, bitmap.height) }
                .maxByOrNull(Candidate::score)
            return candidate?.toResult() ?: DetectionResult(null, DetectionConfidence.LOW)
        } finally {
            contours.forEach(Mat::release)
            hierarchy.release()
            edges.release()
            blurred.release()
            gray.release()
            rgba.release()
        }
    }

    private fun process(source: Bitmap, page: PageItem): Bitmap {
        ensureInitialized()
        var working = source
        try {
            val corners = page.corners
            if (corners != null && corners.isValid()) {
                working = perspective(working, corners).also { source.recycle() }
            }
            return correctQuality(working, page).also { working.recycle() }
        } catch (failure: Throwable) {
            if (working !== source && !working.isRecycled) working.recycle()
            throw failure
        }
    }

    private fun candidateFor(contour: MatOfPoint, width: Int, height: Int): Candidate? {
        val contour2f = MatOfPoint2f(*contour.toArray())
        val approximation = MatOfPoint2f()
        try {
            val perimeter = Imgproc.arcLength(contour2f, true)
            Imgproc.approxPolyDP(contour2f, approximation, perimeter * 0.02, true)
            if (approximation.total() != 4L) return null
            val points = approximation.toArray()
            val contourArea = abs(Imgproc.contourArea(approximation))
            val imageArea = width.toDouble() * height.toDouble()
            val areaRatio = (contourArea / imageArea).toFloat()
            if (areaRatio < MINIMUM_AREA_RATIO) return null
            val polygon = MatOfPoint(*points)
            val isConvex = try {
                Imgproc.isContourConvex(polygon)
            } finally {
                polygon.release()
            }
            if (!isConvex) return null
            val corners = CornerPoints.fromUnordered(points.map { point ->
                NormalizedPoint((point.x / (width - 1)).toFloat(), (point.y / (height - 1)).toFloat())
            }) ?: return null
            val pixelCorners = corners.toPixels(width, height)
            val angles = rectangularity(pixelCorners)
            val shapeScore = ((angles + convexity(pixelCorners)) / 2f).coerceIn(0f, 1f)
            val areaScore = ((areaRatio - MINIMUM_AREA_RATIO) / (0.75f - MINIMUM_AREA_RATIO)).coerceIn(0f, 1f)
            val edgeScore = imageEdgeScore(corners)
            val score = 0.55f * areaScore + 0.35f * shapeScore + 0.10f * edgeScore
            return Candidate(corners, score)
        } finally {
            approximation.release()
            contour2f.release()
        }
    }

    private fun perspective(source: Bitmap, corners: CornerPoints): Bitmap {
        val sourceMat = Mat()
        val destinationMat = Mat()
        val sourcePoints = MatOfPoint2f()
        val destinationPoints = MatOfPoint2f()
        var transform: Mat? = null
        try {
            Utils.bitmapToMat(source, sourceMat)
            val pixels = corners.toPixels(source.width, source.height)
            val size = PerspectiveSizing.outputSize(pixels)
            sourcePoints.fromArray(*pixels.map { Point(it.x.toDouble(), it.y.toDouble()) }.toTypedArray())
            destinationPoints.fromArray(
                Point(0.0, 0.0),
                Point((size.width - 1).toDouble(), 0.0),
                Point((size.width - 1).toDouble(), (size.height - 1).toDouble()),
                Point(0.0, (size.height - 1).toDouble())
            )
            transform = Imgproc.getPerspectiveTransform(sourcePoints, destinationPoints)
            Imgproc.warpPerspective(
                sourceMat,
                destinationMat,
                transform,
                Size(size.width.toDouble(), size.height.toDouble()),
                Imgproc.INTER_CUBIC,
                Core.BORDER_REPLICATE
            )
            return Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888).also {
                Utils.matToBitmap(destinationMat, it)
            }
        } finally {
            transform?.release()
            destinationPoints.release()
            sourcePoints.release()
            destinationMat.release()
            sourceMat.release()
        }
    }

    private fun correctQuality(source: Bitmap, page: PageItem): Bitmap = when (page.scanMode) {
        ScanMode.AUTO -> correctAuto(source)
        ScanMode.TEXT -> correctText(source)
        ScanMode.COLOR -> correctColor(source)
    }

    /** Natural default: bounded white balance followed by deliberately mild contrast. */
    private fun correctAuto(source: Bitmap): Bitmap {
        val input = Mat()
        val rgb = Mat()
        val balanced = Mat()
        val output = Mat()
        val transform = Mat.eye(3, 3, CvType.CV_32F)
        try {
            Utils.bitmapToMat(source, input)
            Imgproc.cvtColor(input, rgb, Imgproc.COLOR_RGBA2RGB)
            val mean = Core.mean(rgb).`val`
            val target = (mean[0] + mean[1] + mean[2]) / 3.0
            // Bounded gray-world correction keeps coloured annotations from being over-corrected.
            for (channel in 0..2) {
                val gain = (target / mean[channel]).coerceIn(0.97, 1.03)
                transform.put(channel, channel, gain)
            }
            Core.transform(rgb, balanced, transform)
            balanced.convertTo(balanced, -1, 1.04, 3.0)
            Imgproc.cvtColor(balanced, output, Imgproc.COLOR_RGB2RGBA)
            return Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888).also {
                Utils.matToBitmap(output, it)
            }
        } finally {
            transform.release()
            output.release()
            balanced.release()
            rgb.release()
            input.release()
        }
    }

    /**
     * Local luminance contrast only.  This remains full-colour and avoids
     * destructive 1-bit thresholding so subscripts and thin circuit lines stay visible.
     */
    private fun correctText(source: Bitmap): Bitmap {
        val input = Mat()
        val rgb = Mat()
        val lab = Mat()
        val output = Mat()
        val channels = ArrayList<Mat>()
        var clahe: org.opencv.imgproc.CLAHE? = null
        try {
            Utils.bitmapToMat(source, input)
            Imgproc.cvtColor(input, rgb, Imgproc.COLOR_RGBA2RGB)
            Imgproc.cvtColor(rgb, lab, Imgproc.COLOR_RGB2Lab)
            Core.split(lab, channels)
            check(channels.size == 3) { "画像の色チャンネルを処理できませんでした。" }
            clahe = Imgproc.createCLAHE(1.5, Size(8.0, 8.0))
            clahe.apply(channels[0], channels[0])
            Core.merge(channels, lab)
            Imgproc.cvtColor(lab, rgb, Imgproc.COLOR_Lab2RGB)
            Imgproc.cvtColor(rgb, output, Imgproc.COLOR_RGB2RGBA)
            return Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888).also {
                Utils.matToBitmap(output, it)
            }
        } finally {
            clahe?.collectGarbage()
            channels.forEach(Mat::release)
            output.release()
            lab.release()
            rgb.release()
            input.release()
        }
    }

    /** Retains hue and saturation; only a nearly-neutral contrast lift is applied. */
    private fun correctColor(source: Bitmap): Bitmap {
        val input = Mat()
        val rgb = Mat()
        val output = Mat()
        try {
            Utils.bitmapToMat(source, input)
            Imgproc.cvtColor(input, rgb, Imgproc.COLOR_RGBA2RGB)
            rgb.convertTo(rgb, -1, 1.01, 1.0)
            Imgproc.cvtColor(rgb, output, Imgproc.COLOR_RGB2RGBA)
            return Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888).also {
                Utils.matToBitmap(output, it)
            }
        } finally {
            output.release()
            rgb.release()
            input.release()
        }
    }

    private fun rectangularity(points: List<PixelPoint>): Float {
        val values = points.indices.map { index ->
            val previous = points[(index + 3) % 4]
            val current = points[index]
            val next = points[(index + 1) % 4]
            val firstX = previous.x - current.x
            val firstY = previous.y - current.y
            val secondX = next.x - current.x
            val secondY = next.y - current.y
            val denominator = sqrt((firstX * firstX + firstY * firstY) * (secondX * secondX + secondY * secondY))
            (1f - abs((firstX * secondX + firstY * secondY) / denominator)).coerceIn(0f, 1f)
        }
        return values.average().toFloat()
    }

    private fun convexity(points: List<PixelPoint>): Float {
        val crossProducts = points.indices.map { index ->
            val a = points[index]
            val b = points[(index + 1) % 4]
            val c = points[(index + 2) % 4]
            (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x)
        }
        return if (crossProducts.all { it > 0f } || crossProducts.all { it < 0f }) 1f else 0f
    }

    private fun imageEdgeScore(corners: CornerPoints): Float {
        val nearest = corners.points.minOf { point -> minOf(point.x, point.y, 1f - point.x, 1f - point.y) }
        return (0.75f + nearest.coerceIn(0f, 0.25f)).coerceAtMost(1f)
    }

    private fun Candidate.toResult(): DetectionResult {
        val confidence = when {
            score >= 0.68f -> DetectionConfidence.HIGH
            score >= 0.48f -> DetectionConfidence.MEDIUM
            else -> DetectionConfidence.LOW
        }
        return DetectionResult(corners.takeIf { confidence != DetectionConfidence.LOW }, confidence, score)
    }

    private fun ensureInitialized() {
        check(OpenCVLoader.initLocal()) { "OpenCV の初期化に失敗しました。" }
    }

    private data class Candidate(val corners: CornerPoints, val score: Float)

    private companion object {
        const val PREVIEW_LONG_EDGE_PX = 1600
        const val DETECTION_LONG_EDGE_PX = 1600
        const val MINIMUM_AREA_RATIO = 0.12f
    }
}
