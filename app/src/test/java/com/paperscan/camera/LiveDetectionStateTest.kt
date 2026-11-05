package com.paperscan.camera

import com.paperscan.model.CornerPoints
import com.paperscan.model.DetectionConfidence
import com.paperscan.model.NormalizedPoint
import com.paperscan.processing.DetectionResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveDetectionStateTest {
    @Test
    fun stableDetection_TC_CAM_001_requiresThreeConsistentFrames() {
        val filter = StableDocumentDetection()
        val result = DetectionResult(quad(), DetectionConfidence.HIGH)

        assertNull(filter.update(result))
        assertNull(filter.update(result))
        assertEquals(quad(), filter.update(result))
    }

    @Test
    fun stableDetection_TC_CAM_002_smoothsSmallFrameJitter() {
        val filter = StableDocumentDetection(requiredFrames = 3)
        filter.update(DetectionResult(quad(0f), DetectionConfidence.HIGH))
        filter.update(DetectionResult(quad(0.01f), DetectionConfidence.MEDIUM))

        val stable = requireNotNull(filter.update(DetectionResult(quad(0.02f), DetectionConfidence.HIGH)))

        assertEquals(0.11f, stable.topLeft.x, 0.0001f)
        assertEquals(0.11f, stable.topLeft.y, 0.0001f)
    }

    @Test
    fun stableDetection_TC_CAM_003_keepsLastOutlineForOneMissThenClears() {
        val filter = stableFilter()

        assertEquals(quad(), filter.update(noDetection()))
        assertNull(filter.update(noDetection()))
    }

    @Test
    fun stableDetection_TC_CAM_004_lowConfidenceCandidateNeverBecomesOverlay() {
        val filter = StableDocumentDetection()
        val result = DetectionResult(quad(), DetectionConfidence.LOW)

        repeat(4) { assertNull(filter.update(result)) }
    }

    @Test
    fun stableDetection_TC_CAM_005_largeJumpResetsAndRequiresNewStableRun() {
        val filter = stableFilter()

        assertNull(filter.update(DetectionResult(quad(0.081f), DetectionConfidence.HIGH)))
        assertNull(filter.update(DetectionResult(quad(0.081f), DetectionConfidence.HIGH)))
        assertEquals(quad(0.081f), filter.update(DetectionResult(quad(0.081f), DetectionConfidence.HIGH)))
    }

    @Test
    fun stableDetection_TC_CAM_006_invalidGeometryDoesNotReplaceValidOutline() {
        val filter = stableFilter()
        val invalid = CornerPoints(
            NormalizedPoint(-0.1f, 0.1f), NormalizedPoint(0.9f, 0.1f),
            NormalizedPoint(0.9f, 0.9f), NormalizedPoint(0.1f, 0.9f)
        )

        assertEquals(quad(), filter.update(DetectionResult(invalid, DetectionConfidence.HIGH)))
        assertNull(filter.update(DetectionResult(invalid, DetectionConfidence.HIGH)))
    }

    @Test
    fun stableDetection_TC_CAM_007_resetClearsPendingAndStableState() {
        val filter = stableFilter()
        filter.reset()

        assertNull(filter.update(DetectionResult(quad(), DetectionConfidence.HIGH)))
        assertNull(filter.update(DetectionResult(quad(), DetectionConfidence.HIGH)))
    }

    @Test
    fun stableDetection_TC_CAM_008_acceptsMediumConfidenceWithoutAutoCaptureSignal() {
        val filter = StableDocumentDetection(requiredFrames = 2)

        assertNull(filter.update(DetectionResult(quad(), DetectionConfidence.MEDIUM)))
        assertTrue(requireNotNull(filter.update(DetectionResult(quad(), DetectionConfidence.MEDIUM))).isValid())
    }

    @Test
    fun stableDetection_TC_CAM_009_clearsAfterReacquisitionOfDifferentDocument() {
        val filter = stableFilter()

        assertEquals(quad(), filter.update(DetectionResult(quad(), DetectionConfidence.HIGH)))
        assertEquals(quad(), filter.update(noDetection()))
        assertNull(filter.update(noDetection()))
        assertNull(filter.update(DetectionResult(quad(0.1f), DetectionConfidence.HIGH)))
    }

    private fun stableFilter() = StableDocumentDetection(requiredFrames = 3).apply {
        repeat(3) { update(DetectionResult(quad(), DetectionConfidence.HIGH)) }
    }

    private fun noDetection() = DetectionResult(null, DetectionConfidence.LOW)

    private fun quad(offset: Float = 0f) = CornerPoints(
        NormalizedPoint(0.1f + offset, 0.1f + offset),
        NormalizedPoint(0.9f + offset, 0.1f + offset),
        NormalizedPoint(0.9f + offset, 0.9f + offset),
        NormalizedPoint(0.1f + offset, 0.9f + offset)
    )
}

class FrameAnalysisThrottleTest {
    @Test
    fun throttle_TC_CAM_010_acceptsFirstFrame() {
        assertTrue(FrameAnalysisThrottle(250).shouldAnalyze(1_000))
    }

    @Test
    fun throttle_TC_CAM_011_dropsFrameBeforeInterval() {
        val throttle = FrameAnalysisThrottle(250)
        throttle.shouldAnalyze(1_000)

        assertFalse(throttle.shouldAnalyze(1_249))
    }

    @Test
    fun throttle_TC_CAM_012_acceptsFrameAtIntervalBoundary() {
        val throttle = FrameAnalysisThrottle(250)
        throttle.shouldAnalyze(1_000)

        assertTrue(throttle.shouldAnalyze(1_250))
    }

    @Test
    fun throttle_TC_CAM_013_zeroIntervalAcceptsSameTimestampFrames() {
        val throttle = FrameAnalysisThrottle(0)

        assertTrue(throttle.shouldAnalyze(10))
        assertTrue(throttle.shouldAnalyze(10))
    }

    @Test
    fun throttle_TC_CAM_014_clockRegressionStartsNewWindowSafely() {
        val throttle = FrameAnalysisThrottle(250)
        throttle.shouldAnalyze(1_000)

        assertTrue(throttle.shouldAnalyze(500))
        assertFalse(throttle.shouldAnalyze(600))
    }

    @Test
    fun throttle_TC_CAM_015_rejectsNegativeInterval() {
        try {
            FrameAnalysisThrottle(-1)
            throw AssertionError("negative intervals must be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected: invalid configuration must fail immediately.
        }
    }

    @Test
    fun frameLifecycle_TC_CAM_016_closesFrameAfterSuccessfulAnalysis() {
        val frame = CloseTrackedFrame()

        withClosedFrame(frame) { }

        assertTrue(frame.closed)
    }

    @Test
    fun frameLifecycle_TC_CAM_017_closesFrameWhenAnalysisThrows() {
        val frame = CloseTrackedFrame()

        try {
            withClosedFrame(frame) { throw IllegalStateException("decode failed") }
            throw AssertionError("analysis exception should propagate")
        } catch (failure: IllegalStateException) {
            assertEquals("decode failed", failure.message)
        }
        assertTrue(frame.closed)
    }

    private class CloseTrackedFrame : AutoCloseable {
        var closed = false
        override fun close() { closed = true }
    }
}
