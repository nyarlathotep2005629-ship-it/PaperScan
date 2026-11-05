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
        require(pageSize.widthPoints > 0 && pageSize.heightPoints > 0) { "PDFページ寸法が不正です。" }
        require(sourceWidthPixels > 0 && sourceHeightPixels > 0) { "画像寸法が不正です。" }
        require(targetDpi > 0) { "PDFのDPIが不正です。" }

        val maximumWidth = pixelsAtDpi(pageSize.widthPoints, targetDpi)
        val maximumHeight = pixelsAtDpi(pageSize.heightPoints, targetDpi)
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

    /** Preserves the established Float rounding, while widening only before an Int overflow. */
    private fun pixelsAtDpi(points: Int, targetDpi: Int): Int {
        val product = points.toLong() * targetDpi
        return if (product <= Int.MAX_VALUE) {
            (product.toInt() / POINTS_PER_INCH).roundToInt()
        } else {
            (product.toDouble() / POINTS_PER_INCH.toDouble()).roundToInt()
        }
    }
}
