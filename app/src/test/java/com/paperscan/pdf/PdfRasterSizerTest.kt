package com.paperscan.pdf

import org.junit.Assert.assertEquals
import org.junit.Test

class PdfRasterSizerTest {
    @Test
    fun a4At200Dpi_isBoundedToReadableRaster() {
        val actual = PdfRasterSizer.sizeFor(PdfPageSize(595, 842), 3000, 4242)

        // The source is only A4-like, so the raster must fit the target box without distortion.
        assertEquals(PdfRasterSize(1653, 2337), actual)
    }

    @Test
    fun smallSource_isNotUpscaled() {
        val actual = PdfRasterSizer.sizeFor(PdfPageSize(595, 842), 595, 842)

        assertEquals(PdfRasterSize(595, 842), actual)
    }

    @Test
    fun landscapeA4_preservesOrientation() {
        val actual = PdfRasterSizer.sizeFor(PdfPageSize(842, 595), 4242, 3000)

        assertEquals(PdfRasterSize(2337, 1653), actual)
    }

    @Test
    fun selectedQuality_changesDpiBoundWithoutUpscalingSource() {
        val page = PdfPageSize(595, 842)
        val largeSource = PdfRasterSizer.sizeFor(page, 3000, 4242, PdfQuality.COMPACT.targetDpi)
        val smallSource = PdfRasterSizer.sizeFor(page, 300, 424, PdfQuality.HIGH.targetDpi)

        assertEquals(PdfRasterSize(992, 1403), largeSource)
        assertEquals(PdfRasterSize(300, 424), smallSource)
    }
}
