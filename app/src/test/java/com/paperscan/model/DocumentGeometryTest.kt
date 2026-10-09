package com.paperscan.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentGeometryTest {
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
}
