package com.paperscan.pdf

import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

data class PdfPageSize(val widthPoints: Int, val heightPoints: Int)

data class PdfDrawRect(val left: Float, val top: Float, val right: Float, val bottom: Float)

object PdfPageSizer {
    const val A4_PORTRAIT_WIDTH_POINTS = 595
    const val A4_PORTRAIT_HEIGHT_POINTS = 842
    private const val A_SERIES_RATIO = 1.41421356f
    private const val A_SERIES_TOLERANCE = 0.05f

    fun pageSizeFor(
        imageWidthPx: Int,
        imageHeightPx: Int,
        paperSize: PdfPaperSize = PdfPaperSize.AUTO,
        orientation: PdfOrientation = PdfOrientation.AUTO
    ): PdfPageSize {
        require(imageWidthPx > 0 && imageHeightPx > 0) { "画像寸法が不正です。" }
        val ratio = imageWidthPx.toFloat() / imageHeightPx.toFloat()
        val automatic = if (paperSize == PdfPaperSize.AUTO) {
            val autoPortrait = ratio <= 1f
            val longToShort = if (autoPortrait) 1f / ratio else ratio
            if (abs(longToShort - A_SERIES_RATIO) <= A_SERIES_TOLERANCE) {
                if (autoPortrait) {
                    PdfPageSize(A4_PORTRAIT_WIDTH_POINTS, A4_PORTRAIT_HEIGHT_POINTS)
                } else {
                    PdfPageSize(A4_PORTRAIT_HEIGHT_POINTS, A4_PORTRAIT_WIDTH_POINTS)
                }
            } else if (autoPortrait) {
                PdfPageSize(A4_PORTRAIT_WIDTH_POINTS, (A4_PORTRAIT_WIDTH_POINTS / ratio).roundToInt())
            } else {
                PdfPageSize((A4_PORTRAIT_WIDTH_POINTS * ratio).roundToInt(), A4_PORTRAIT_WIDTH_POINTS)
            }
        } else {
            val portrait = when (paperSize) {
                PdfPaperSize.A4 -> PdfPageSize(A4_PORTRAIT_WIDTH_POINTS, A4_PORTRAIT_HEIGHT_POINTS)
                PdfPaperSize.A5 -> PdfPageSize(420, 595)
                PdfPaperSize.LETTER -> PdfPageSize(612, 792)
                PdfPaperSize.AUTO -> error("unreachable")
            }
            portrait
        }

        val useLandscape = when (orientation) {
            PdfOrientation.AUTO -> ratio > 1f
            PdfOrientation.PORTRAIT -> false
            PdfOrientation.LANDSCAPE -> true
        }
        val shortEdge = minOf(automatic.widthPoints, automatic.heightPoints)
        val longEdge = maxOf(automatic.widthPoints, automatic.heightPoints)
        return if (useLandscape) {
            PdfPageSize(longEdge, shortEdge)
        } else {
            PdfPageSize(shortEdge, longEdge)
        }
    }

    fun fitRect(pageSize: PdfPageSize, imageWidthPx: Int, imageHeightPx: Int): PdfDrawRect {
        require(pageSize.widthPoints > 0 && pageSize.heightPoints > 0) { "PDFページ寸法が不正です。" }
        require(imageWidthPx > 0 && imageHeightPx > 0) { "画像寸法が不正です。" }
        val scale = min(
            pageSize.widthPoints.toFloat() / imageWidthPx,
            pageSize.heightPoints.toFloat() / imageHeightPx
        )
        val width = imageWidthPx * scale
        val height = imageHeightPx * scale
        val left = (pageSize.widthPoints - width) / 2f
        val top = (pageSize.heightPoints - height) / 2f
        return PdfDrawRect(left, top, left + width, top + height)
    }
}
