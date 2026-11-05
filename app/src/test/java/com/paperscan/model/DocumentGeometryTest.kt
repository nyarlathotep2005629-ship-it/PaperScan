package com.paperscan.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentGeometryTest {
    @Test
    fun corners_TC_GEO_001_reversedWindingIsRejected() {
        val reversed = CornerPoints(
            NormalizedPoint(0f, 0f), NormalizedPoint(0f, 1f),
            NormalizedPoint(1f, 1f), NormalizedPoint(1f, 0f)
        )

        assertFalse(reversed.isValid())
    }

    @Test
    fun corners_TC_GEO_002_documentTouchingImageEdgesIsValid() {
        assertTrue(CornerPoints.fullImage().isValid())
    }

    @Test
    fun corners_TC_GEO_003_duplicateCornerIsRejected() {
        val repeated = CornerPoints(
            NormalizedPoint(0.1f, 0.1f), NormalizedPoint(0.9f, 0.1f),
            NormalizedPoint(0.9f, 0.9f), NormalizedPoint(0.9f, 0.1f)
        )

        assertFalse(repeated.isValid())
    }

    @Test
    fun corners_TC_GEO_004_collinearEdgeIsRejected() {
        val collinear = CornerPoints(
            NormalizedPoint(0.1f, 0.1f), NormalizedPoint(0.5f, 0.1f),
            NormalizedPoint(0.9f, 0.1f), NormalizedPoint(0.1f, 0.9f)
        )

        assertFalse(collinear.isValid())
    }

    @Test
    fun corners_TC_GEO_005_shuffledDuplicatePointsAreRejected() {
        assertNull(CornerPoints.fromUnordered(List(4) { NormalizedPoint(0.5f, 0.5f) }))
    }

    @Test
    fun corners_TC_GEO_006_exactImageBoundaryCoordinatesRemainValid() {
        val corners = CornerPoints(
            NormalizedPoint(0f, 0f), NormalizedPoint(1f, 0f),
            NormalizedPoint(1f, 1f), NormalizedPoint(0f, 1f)
        )

        assertTrue(corners.isValid())
    }

    @Test
    fun projection_TC_GEO_007_nonFinitePixelCoordinateIsRejected() {
        try {
            PerspectiveSizing.outputSize(
                listOf(PixelPoint(Float.NaN, 0f), PixelPoint(100f, 0f), PixelPoint(100f, 200f), PixelPoint(0f, 200f))
            )
            throw AssertionError("non-finite input must be rejected")
        } catch (_: IllegalArgumentException) { }
    }

    @Test
    fun projection_TC_GEO_008_zeroLengthEdgeIsRejected() {
        try {
            PerspectiveSizing.outputSize(
                listOf(PixelPoint(0f, 0f), PixelPoint(0f, 0f), PixelPoint(100f, 200f), PixelPoint(0f, 200f))
            )
            throw AssertionError("degenerate quad must be rejected")
        } catch (_: IllegalArgumentException) { }
    }

    @Test
    fun projection_TC_GEO_009_customLongEdgeLimitIsRespected() {
        val size = PerspectiveSizing.outputSize(
            listOf(PixelPoint(0f, 0f), PixelPoint(4000f, 0f), PixelPoint(4000f, 2000f), PixelPoint(0f, 2000f)),
            maxLongEdgePx = 800
        )

        assertEquals(800, size.width)
        assertEquals(400, size.height)
    }

    @Test
    fun projection_TC_GEO_010_heightDominantQuadKeepsLandscapeEdgesCorrectly() {
        val size = PerspectiveSizing.outputSize(
            listOf(PixelPoint(0f, 0f), PixelPoint(1000f, 0f), PixelPoint(1000f, 3000f), PixelPoint(0f, 3000f)),
            maxLongEdgePx = 1500
        )

        assertEquals(500, size.width)
        assertEquals(1500, size.height)
    }

    @Test
    fun fromUnordered_shuffledQuad_normalizesToTlTrBrBl() {
        val corners = requireNotNull(
            CornerPoints.fromUnordered(
                listOf(
                    NormalizedPoint(0.86f, 0.88f),
                    NormalizedPoint(0.10f, 0.15f),
                    NormalizedPoint(0.14f, 0.82f),
                    NormalizedPoint(0.90f, 0.12f)
                )
            )
        )

        assertEquals(NormalizedPoint(0.10f, 0.15f), corners.topLeft)
        assertEquals(NormalizedPoint(0.90f, 0.12f), corners.topRight)
        assertEquals(NormalizedPoint(0.86f, 0.88f), corners.bottomRight)
        assertEquals(NormalizedPoint(0.14f, 0.82f), corners.bottomLeft)
    }

    @Test
    fun fromUnordered_threePoints_returnsNull() {
        assertNull(CornerPoints.fromUnordered(List(3) { NormalizedPoint(0.5f, 0.5f) }))
    }

    @Test
    fun isValid_outOfRangePoint_returnsFalse() {
        val corners = CornerPoints(
            NormalizedPoint(-0.01f, 0f), NormalizedPoint(1f, 0f),
            NormalizedPoint(1f, 1f), NormalizedPoint(0f, 1f)
        )

        assertFalse(corners.isValid())
    }

    @Test
    fun isValid_selfCrossingQuad_returnsFalse() {
        val corners = CornerPoints(
            NormalizedPoint(0.1f, 0.1f), NormalizedPoint(0.9f, 0.9f),
            NormalizedPoint(0.9f, 0.1f), NormalizedPoint(0.1f, 0.9f)
        )

        assertFalse(corners.isValid())
    }

    @Test
    fun isValid_tinyQuad_returnsFalse() {
        val corners = CornerPoints(
            NormalizedPoint(0.1f, 0.1f), NormalizedPoint(0.11f, 0.1f),
            NormalizedPoint(0.11f, 0.11f), NormalizedPoint(0.1f, 0.11f)
        )

        assertFalse(corners.isValid())
    }

    @Test
    fun isValid_concaveQuad_returnsFalse() {
        val corners = CornerPoints(
            NormalizedPoint(0.1f, 0.1f), NormalizedPoint(0.9f, 0.1f),
            NormalizedPoint(0.45f, 0.45f), NormalizedPoint(0.1f, 0.9f)
        )

        assertFalse(corners.isValid())
    }

    @Test
    fun isValid_nonFiniteCoordinate_returnsFalse() {
        val corners = CornerPoints(
            NormalizedPoint(Float.NaN, 0.1f), NormalizedPoint(0.9f, 0.1f),
            NormalizedPoint(0.9f, 0.9f), NormalizedPoint(0.1f, 0.9f)
        )

        assertFalse(corners.isValid())
    }

    @Test
    fun rotateClockwise_mapsBackToCanonicalOrder() {
        val source = CornerPoints(
            NormalizedPoint(0.1f, 0.2f), NormalizedPoint(0.8f, 0.1f),
            NormalizedPoint(0.9f, 0.7f), NormalizedPoint(0.2f, 0.8f)
        )

        val rotated = requireNotNull(source.rotateClockwise())

        assertPointEquals(NormalizedPoint(0.2f, 0.2f), rotated.topLeft)
        assertPointEquals(NormalizedPoint(0.8f, 0.1f), rotated.topRight)
        assertPointEquals(NormalizedPoint(0.9f, 0.8f), rotated.bottomRight)
        assertPointEquals(NormalizedPoint(0.3f, 0.9f), rotated.bottomLeft)
    }

    @Test
    fun toPixels_fullImage_mapsToImageBounds() {
        val actual = CornerPoints.fullImage().toPixels(100, 200)

        assertEquals(PixelPoint(0f, 0f), actual[0])
        assertEquals(PixelPoint(99f, 0f), actual[1])
        assertEquals(PixelPoint(99f, 199f), actual[2])
        assertEquals(PixelPoint(0f, 199f), actual[3])
    }

    @Test
    fun outputSize_largePixels_capsLongEdgeWithoutChangingAspect() {
        val size = PerspectiveSizing.outputSize(
            listOf(PixelPoint(0f, 0f), PixelPoint(6000f, 0f), PixelPoint(6000f, 4000f), PixelPoint(0f, 4000f))
        )

        assertEquals(3000, size.width)
        assertEquals(2000, size.height)
    }

    @Test
    fun fullImage_hasValidSimpleOrder() {
        assertTrue(CornerPoints.fullImage().isValid())
        assertNotNull(CornerPoints.fullImage().points)
    }

    private fun assertPointEquals(expected: NormalizedPoint, actual: NormalizedPoint) {
        assertEquals(expected.x, actual.x, 0.0001f)
        assertEquals(expected.y, actual.y, 0.0001f)
    }
}
