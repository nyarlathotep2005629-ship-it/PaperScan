package com.paperscan.model

import org.junit.Assert.assertEquals
import org.junit.Test

class DisplayImageCoordinatesTest {
    @Test
    fun displayedBounds_TC_COORD_004_landscapeImageInTallViewUsesVerticalLetterbox() {
        val bounds = DisplayedImageBounds.fit(600f, 1000f, 800, 400)

        assertEquals(0f, bounds.left, 0.001f)
        assertEquals(350f, bounds.top, 0.001f)
        assertEquals(600f, bounds.width, 0.001f)
        assertEquals(300f, bounds.height, 0.001f)
    }

    @Test
    fun displayedBounds_TC_COORD_005_equalAspectRatioUsesEntireContainer() {
        val bounds = DisplayedImageBounds.fit(900f, 600f, 1200, 800)

        assertEquals(0f, bounds.left, 0.001f)
        assertEquals(0f, bounds.top, 0.001f)
        assertEquals(900f, bounds.width, 0.001f)
        assertEquals(600f, bounds.height, 0.001f)
    }

    @Test
    fun conversion_TC_COORD_006_allImageCornersRoundTrip() {
        val bounds = DisplayedImageBounds.fit(700f, 900f, 1200, 800)

        listOf(
            NormalizedPoint(0f, 0f), NormalizedPoint(1f, 0f),
            NormalizedPoint(1f, 1f), NormalizedPoint(0f, 1f)
        ).forEach { point ->
            val actual = bounds.toNormalized(bounds.toDisplay(point))
            assertEquals(point.x, actual.x, 0.0001f)
            assertEquals(point.y, actual.y, 0.0001f)
        }
    }

    @Test
    fun conversion_TC_COORD_007_pointBeyondLowerRightClampsToBottomRight() {
        val bounds = DisplayedImageBounds.fit(600f, 1000f, 800, 400)

        assertEquals(NormalizedPoint(1f, 1f), bounds.toNormalized(DisplayPoint(700f, 1100f)))
    }

    @Test
    fun displayedBounds_TC_COORD_008_zeroContainerWidthIsRejected() {
        expectIllegalArgument { DisplayedImageBounds.fit(0f, 100f, 100, 100) }
    }

    @Test
    fun displayedBounds_TC_COORD_009_nonFiniteContainerHeightIsRejected() {
        expectIllegalArgument { DisplayedImageBounds.fit(100f, Float.NaN, 100, 100) }
    }

    @Test
    fun displayedBounds_TC_COORD_010_zeroImageWidthIsRejected() {
        expectIllegalArgument { DisplayedImageBounds.fit(100f, 100f, 0, 100) }
    }

    @Test
    fun fit_portraitImageInWideContainer_usesActualLetterboxedImageBounds() {
        val bounds = DisplayedImageBounds.fit(
            containerWidth = 1000f,
            containerHeight = 600f,
            imageWidth = 400,
            imageHeight = 800
        )

        assertEquals(350f, bounds.left, 0.001f)
        assertEquals(0f, bounds.top, 0.001f)
        assertEquals(300f, bounds.width, 0.001f)
        assertEquals(600f, bounds.height, 0.001f)
        assertEquals(NormalizedPoint(0.5f, 0.5f), bounds.toNormalized(DisplayPoint(500f, 300f)))
    }

    @Test
    fun displayAndNormalizedConversions_areInverseInsideImage() {
        val bounds = DisplayedImageBounds.fit(700f, 900f, 1200, 800)
        val expected = NormalizedPoint(0.23f, 0.81f)

        val actual = bounds.toNormalized(bounds.toDisplay(expected))

        assertEquals(expected.x, actual.x, 0.0001f)
        assertEquals(expected.y, actual.y, 0.0001f)
    }

    @Test
    fun toNormalized_letterboxPoint_clampsToImageEdge() {
        val bounds = DisplayedImageBounds.fit(1000f, 600f, 400, 800)

        assertEquals(
            NormalizedPoint(0f, 0.5f),
            bounds.toNormalized(DisplayPoint(20f, 300f))
        )
    }

    private fun expectIllegalArgument(block: () -> Unit) {
        try {
            block()
            throw AssertionError("invalid dimensions must be rejected")
        } catch (_: IllegalArgumentException) { }
    }
}
