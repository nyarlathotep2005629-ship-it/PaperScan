package com.paperscan.camera

import com.paperscan.model.CornerPoints
import com.paperscan.model.DetectionConfidence
import com.paperscan.processing.DetectionResult

/** Accepts a document outline only after consecutive, geometrically consistent frames. */
internal class StableDocumentDetection(
    private val requiredFrames: Int = 3,
    private val maximumCornerDelta: Float = 0.08f,
    private val missingFramesToClear: Int = 2
) {
    private val candidates = ArrayDeque<CornerPoints>()
    private var stable: CornerPoints? = null
    private var misses = 0

    init {
        require(requiredFrames >= 2)
        require(maximumCornerDelta > 0f)
        require(missingFramesToClear >= 1)
    }

    fun update(result: DetectionResult): CornerPoints? {
        val corners = result.corners
        if (corners == null || result.confidence == DetectionConfidence.LOW || !corners.isValid()) {
            misses++
            if (misses >= missingFramesToClear) reset()
            return stable
        }

        misses = 0
        val previous = candidates.lastOrNull() ?: stable
        if (previous != null && maximumDelta(previous, corners) > maximumCornerDelta) {
            candidates.clear()
            stable = null
        }
        candidates.addLast(corners)
        while (candidates.size > requiredFrames) candidates.removeFirst()
        if (candidates.size < requiredFrames) return null

        stable = average(candidates.toList())
        return stable
    }

    fun reset() {
        candidates.clear()
        stable = null
        misses = 0
    }

    private fun maximumDelta(first: CornerPoints, second: CornerPoints): Float =
        first.points.zip(second.points).maxOf { (a, b) -> maxOf(kotlin.math.abs(a.x - b.x), kotlin.math.abs(a.y - b.y)) }

    private fun average(values: List<CornerPoints>): CornerPoints? {
        val points = (0..3).map { index ->
            val candidates = values.map { it.points[index] }
            com.paperscan.model.NormalizedPoint(
                x = candidates.map { it.x }.average().toFloat(),
                y = candidates.map { it.y }.average().toFloat()
            )
        }
        return CornerPoints(points[0], points[1], points[2], points[3]).takeIf(CornerPoints::isValid)
    }
}

/** Limits expensive CV work while still allowing the preview to show the newest frame. */
internal class FrameAnalysisThrottle(private val minimumIntervalMillis: Long = 250L) {
    private var lastAcceptedAt: Long? = null

    init { require(minimumIntervalMillis >= 0L) }

    fun shouldAnalyze(nowMillis: Long): Boolean {
        val previous = lastAcceptedAt
        if (previous == null || nowMillis < previous || nowMillis - previous >= minimumIntervalMillis) {
            lastAcceptedAt = nowMillis
            return true
        }
        return false
    }
}

/** Keeps the CameraX image buffer lifecycle correct on success, throttling and exceptions. */
internal inline fun <T : AutoCloseable> withClosedFrame(frame: T, analyze: (T) -> Unit) {
    try {
        analyze(frame)
    } finally {
        frame.close()
    }
}
