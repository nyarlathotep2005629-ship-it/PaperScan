package com.paperscan.pdf

import kotlin.math.roundToInt

data class PdfRasterSize(val widthPixels: Int, val heightPixels: Int)

/**
 * Bounds the raster embedded in a PDF page without changing its logical point size.
 *
 * A4 at [TARGET_DPI] is 1653 x 2339 px. This is sufficient for readable paper
 * documents while avoiding the previous 3000 px export raster being embedded as-is.
 */
object PdfRasterSizer {
    const val TARGET_DPI = 200
    private const val POINTS_PER_INCH = 72f

    fun sizeFor(
        pageSize: PdfPageSize,
        sourceWidthPixels: Int,
        sourceHeightPixels: Int,
        targetDpi: Int = TARGET_DPI
    ): PdfRasterSize {
        require(sourceWidthPixels > 0 && sourceHeightPixels > 0) { "画像寸法が不正です。" }
        require(targetDpi > 0) { "PDFのDPIが不正です。" }

        val maximumWidth = (pageSize.widthPoints * targetDpi / POINTS_PER_INCH).roundToInt()
        val maximumHeight = (pageSize.heightPoints * targetDpi / POINTS_PER_INCH).roundToInt()
        val scale = minOf(
            1f,
            maximumWidth.toFloat() / sourceWidthPixels,
            maximumHeight.toFloat() / sourceHeightPixels
        )
        return PdfRasterSize(
            widthPixels = (sourceWidthPixels * scale).roundToInt().coerceAtLeast(1),
            heightPixels = (sourceHeightPixels * scale).roundToInt().coerceAtLeast(1)
        )
    }
}
