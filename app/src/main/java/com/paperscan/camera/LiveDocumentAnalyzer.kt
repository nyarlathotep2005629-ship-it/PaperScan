package com.paperscan.camera

import android.graphics.Bitmap
import android.graphics.PointF
import android.os.SystemClock
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.view.PreviewView
import androidx.camera.view.TransformExperimental
import androidx.camera.view.transform.CoordinateTransform
import androidx.camera.view.transform.ImageProxyTransformFactory
import com.paperscan.model.CornerPoints
import com.paperscan.processing.DetectionResult
import java.util.concurrent.atomic.AtomicBoolean

/** Runs OpenCV on a background CameraX executor and maps the outline to PreviewView coordinates. */
@androidx.annotation.OptIn(markerClass = [TransformExperimental::class])
internal class LiveDocumentAnalyzer(
    private val previewView: PreviewView,
    private val detect: (Bitmap) -> DetectionResult,
    private val onCorners: (List<PointF>?) -> Unit,
    minimumIntervalMillis: Long = 250L,
    private val clockMillis: () -> Long = SystemClock::elapsedRealtime
) : ImageAnalysis.Analyzer, AutoCloseable {
    private val throttle = FrameAnalysisThrottle(minimumIntervalMillis)
    private val stabilizer = StableDocumentDetection()
    private val transformFactory = ImageProxyTransformFactory().apply {
        setUsingCropRect(true)
        setUsingRotationDegrees(true)
    }
    private val closed = AtomicBoolean(false)

    override fun analyze(image: ImageProxy) {
        withClosedFrame(image) { frame ->
            if (closed.get() || !throttle.shouldAnalyze(clockMillis())) return@withClosedFrame
            var bitmap: Bitmap? = null
            try {
                bitmap = frame.toBitmap()
                val stableCorners = stabilizer.update(detect(bitmap))
                val target = previewView.outputTransform
                if (stableCorners == null || target == null) {
                    postCorners(null)
                    return@withClosedFrame
                }
                val source = transformFactory.getOutputTransform(frame)
                val points = stableCorners.toBufferPoints(bitmap.width, bitmap.height)
                CoordinateTransform(source, target).mapPoints(points)
                if (points.any { !it.isFinite() }) {
                    stabilizer.reset()
                    postCorners(null)
                } else {
                    postCorners(points.toPointList())
                }
            } catch (_: Exception) {
                stabilizer.reset()
                postCorners(null)
            } finally {
                bitmap?.takeUnless(Bitmap::isRecycled)?.recycle()
            }
        }
    }

    fun resetForRebind() {
        stabilizer.reset()
        postCorners(null)
    }

    override fun close() {
        closed.set(true)
        resetForRebind()
    }

    private fun CornerPoints.toBufferPoints(width: Int, height: Int): FloatArray =
        points.flatMap { listOf(it.x * width, it.y * height) }.toFloatArray()

    private fun FloatArray.toPointList(): List<PointF> =
        (indices step 2).map { index -> PointF(this[index], this[index + 1]) }

    private fun postCorners(points: List<PointF>?) {
        if (!closed.get()) previewView.post { if (!closed.get()) onCorners(points) }
    }
}
