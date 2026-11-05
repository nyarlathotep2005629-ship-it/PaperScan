package com.paperscan.model

import org.junit.Assert.assertEquals
import org.junit.Test

class DisplayImageCoordinatesTest {
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
}
