package com.paperscan.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Risk-based pairwise settings coverage without a full Cartesian product. */
class PdfSettingsMatrixTest {
    @Test
    fun settings_TC_PDF_012_highA4PortraitColorKeeps300DpiBound() {
        val settings = PdfExportSettings(PdfQuality.HIGH, PdfPaperSize.A4, PdfOrientation.PORTRAIT, PdfColorMode.COLOR)

        assertSettings(settings, 3000, 4242, PdfPageSize(595, 842), PdfRasterSize(2479, 3505))
    }

    @Test
    fun settings_TC_PDF_013_standardA5LandscapeGrayscaleKeeps200DpiBound() {
        val settings = PdfExportSettings(PdfQuality.STANDARD, PdfPaperSize.A5, PdfOrientation.LANDSCAPE, PdfColorMode.GRAYSCALE)

        assertSettings(settings, 4242, 3000, PdfPageSize(595, 420), PdfRasterSize(1650, 1167))
    }

    @Test
    fun settings_TC_PDF_014_compactLetterPortraitBlackAndWhiteKeeps120DpiBound() {
        val settings = PdfExportSettings(PdfQuality.COMPACT, PdfPaperSize.LETTER, PdfOrientation.PORTRAIT, PdfColorMode.BLACK_WHITE)

        assertSettings(settings, 3000, 4000, PdfPageSize(612, 792), PdfRasterSize(990, 1320))
    }

    @Test
    fun settings_TC_PDF_015_highLetterLandscapeGrayscaleFitsTallSource() {
        val settings = PdfExportSettings(PdfQuality.HIGH, PdfPaperSize.LETTER, PdfOrientation.LANDSCAPE, PdfColorMode.GRAYSCALE)

        assertSettings(settings, 2000, 3000, PdfPageSize(792, 612), PdfRasterSize(1700, 2550))
    }

    @Test
    fun settings_TC_PDF_016_standardAutoPaperPortraitUsesSourceOrientation() {
        val settings = PdfExportSettings(PdfQuality.STANDARD, PdfPaperSize.AUTO, PdfOrientation.AUTO, PdfColorMode.AUTO)

        assertSettings(settings, 1200, 1800, PdfPageSize(595, 893), PdfRasterSize(1200, 1800))
    }

    @Test
    fun settings_TC_PDF_017_compactAutoPaperLandscapeUsesSourceOrientation() {
        val settings = PdfExportSettings(PdfQuality.COMPACT, PdfPaperSize.AUTO, PdfOrientation.AUTO, PdfColorMode.COLOR)

        assertSettings(settings, 1800, 1200, PdfPageSize(893, 595), PdfRasterSize(1488, 992))
    }

    private fun assertSettings(
        settings: PdfExportSettings,
        sourceWidth: Int,
        sourceHeight: Int,
        expectedPage: PdfPageSize,
        expectedRaster: PdfRasterSize
    ) {
        val page = PdfPageSizer.pageSizeFor(sourceWidth, sourceHeight, settings.paperSize, settings.orientation)
        val raster = PdfRasterSizer.sizeFor(page, sourceWidth, sourceHeight, settings.quality.targetDpi)

        assertEquals(expectedPage, page)
        assertEquals(expectedRaster, raster)
        assertTrue(settings.quality.jpegQuality in 1..100)
    }
}
