package com.paperscan.model

import kotlin.math.min

/** Actual image rectangle after applying a ContentScale.Fit-equivalent transform. */
data class DisplayedImageBounds(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float
) {
    init {
        require(left.isFinite() && top.isFinite())
        require(width.isFinite() && height.isFinite() && width > 0f && height > 0f)
    }

    fun toDisplay(point: NormalizedPoint): DisplayPoint = DisplayPoint(
        x = left + point.x * width,
        y = top + point.y * height
    )

    fun toNormalized(point: DisplayPoint): NormalizedPoint = NormalizedPoint(
        x = ((point.x - left) / width).coerceIn(0f, 1f),
        y = ((point.y - top) / height).coerceIn(0f, 1f)
    )

    companion object {
        fun fit(
            containerWidth: Float,
            containerHeight: Float,
            imageWidth: Int,
            imageHeight: Int
        ): DisplayedImageBounds {
            require(containerWidth.isFinite() && containerHeight.isFinite())
            require(containerWidth > 0f && containerHeight > 0f)
            require(imageWidth > 0 && imageHeight > 0)
            val scale = min(containerWidth / imageWidth, containerHeight / imageHeight)
            val fittedWidth = imageWidth * scale
            val fittedHeight = imageHeight * scale
            return DisplayedImageBounds(
                left = (containerWidth - fittedWidth) / 2f,
                top = (containerHeight - fittedHeight) / 2f,
                width = fittedWidth,
                height = fittedHeight
            )
        }
    }
}

data class DisplayPoint(val x: Float, val y: Float)
