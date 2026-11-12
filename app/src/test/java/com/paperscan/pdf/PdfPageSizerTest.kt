package com.paperscan.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfPageSizerTest {
    @Test
    fun calculatePageSize_TC_PDF_001_fixedA4AutoOrientationFollowsLandscapeSource() {
        assertEquals(
            PdfPageSize(842, 595),
            PdfPageSizer.pageSizeFor(1600, 900, PdfPaperSize.A4, PdfOrientation.AUTO)
        )
    }

    @Test
    fun calculatePageSize_TC_PDF_002_fixedLetterAutoOrientationFollowsPortraitSource() {
        assertEquals(
            PdfPageSize(612, 792),
            PdfPageSizer.pageSizeFor(900, 1600, PdfPaperSize.LETTER, PdfOrientation.AUTO)
        )
    }

    @Test
    fun calculatePageSize_TC_PDF_003_autoPaperSquareSourceUsesSquarePage() {
        assertEquals(PdfPageSize(595, 595), PdfPageSizer.pageSizeFor(1000, 1000))
    }

    @Test
    fun calculatePageSize_TC_PDF_004_fixedA5LandscapeUsesSelectedPaperDimensions() {
        assertEquals(
            PdfPageSize(595, 420),
            PdfPageSizer.pageSizeFor(1200, 700, PdfPaperSize.A5, PdfOrientation.AUTO)
        )
    }

    @Test
    fun fitRect_TC_PDF_005_tallImageIsCenteredHorizontallyOnLandscapePage() {
        val page = PdfPageSize(842, 595)

        val draw = PdfPageSizer.fitRect(page, 1000, 2000)

        assertEquals((page.widthPoints - (draw.right - draw.left)) / 2f, draw.left, 0.001f)
        assertEquals(0f, draw.top, 0.001f)
        assertEquals(page.heightPoints.toFloat(), draw.bottom, 0.001f)
    }

    @Test
    fun fitRect_TC_PDF_006_nonPositivePageSizeIsRejected() {
        try {
            PdfPageSizer.fitRect(PdfPageSize(0, 842), 100, 100)
            throw AssertionError("invalid page dimensions must be rejected")
        } catch (_: IllegalArgumentException) { }
    }

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

    @Test
    fun calculatePageSize_fixedPaperAndManualOrientation_useSelectedDimensions() {
        assertEquals(PdfPageSize(420, 595), PdfPageSizer.pageSizeFor(1000, 1400, PdfPaperSize.A5))
        assertEquals(
            PdfPageSize(792, 612),
            PdfPageSizer.pageSizeFor(1000, 1400, PdfPaperSize.LETTER, PdfOrientation.LANDSCAPE)
        )
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
