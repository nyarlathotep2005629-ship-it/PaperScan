package com.paperscan.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfPageSizerTest {
    @Test
    fun calculatePageSize_sameAspectDifferentPixels_returnsSamePointSize() {
        val lowResolution = PdfPageSizer.pageSizeFor(595, 842)
        val highResolution = PdfPageSizer.pageSizeFor(2975, 4210)

        assertEquals(PdfPageSize(595, 842), lowResolution)
        assertEquals(lowResolution, highResolution)
    }

    @Test
    fun calculatePageSize_landscapeA4_returnsLandscapePoints() {
        assertEquals(PdfPageSize(842, 595), PdfPageSizer.pageSizeFor(4210, 2975))
    }

    @Test(expected = IllegalArgumentException::class)
    fun calculatePageSize_zeroWidth_rejectsInvalidInput() {
        PdfPageSizer.pageSizeFor(0, 100)
    }

    @Test
    fun fitRect_squareImage_keepsAspectRatioInsidePage() {
        val page = PdfPageSizer.pageSizeFor(595, 842)
        val draw = PdfPageSizer.fitRect(page, 1000, 1000)

        assertEquals(draw.right - draw.left, draw.bottom - draw.top, 0.001f)
        assertTrue(draw.left >= 0f && draw.top >= 0f)
        assertTrue(draw.right <= page.widthPoints && draw.bottom <= page.heightPoints)
    }
}
