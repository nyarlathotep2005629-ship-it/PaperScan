package com.paperscan.model

import kotlin.math.atan2
import kotlin.math.hypot

/** A point expressed in the EXIF- and user-rotation-corrected image coordinate system. */
data class NormalizedPoint(val x: Float, val y: Float) {
    fun isInsideUnitSquare(): Boolean = x in 0f..1f && y in 0f..1f
}

/**
 * Paper corners in the only order accepted by the processing pipeline:
 * P0=top-left, P1=top-right, P2=bottom-right, P3=bottom-left.
 */
data class CornerPoints(
    val topLeft: NormalizedPoint,
    val topRight: NormalizedPoint,
    val bottomRight: NormalizedPoint,
    val bottomLeft: NormalizedPoint
) {
    val points: List<NormalizedPoint>
        get() = listOf(topLeft, topRight, bottomRight, bottomLeft)

    fun isValid(): Boolean = points.all(NormalizedPoint::isInsideUnitSquare) &&
        isSimple() && area() >= MINIMUM_NORMALIZED_AREA &&
        points.zipWithNextCircular().all { (first, second) -> distance(first, second) >= MINIMUM_EDGE_LENGTH }

    fun area(): Float = kotlin.math.abs(
        points.indices.sumOf { index ->
            val current = points[index]
            val next = points[(index + 1) % points.size]
            (current.x * next.y - next.x * current.y).toDouble()
        }.toFloat() / 2f
    )

    fun isSimple(): Boolean =
        !segmentsIntersect(topLeft, topRight, bottomRight, bottomLeft) &&
            !segmentsIntersect(topRight, bottomRight, bottomLeft, topLeft)

    fun toPixels(width: Int, height: Int): List<PixelPoint> {
        require(width > 1 && height > 1)
        return points.map { PixelPoint(it.x * (width - 1), it.y * (height - 1)) }
    }

    companion object {
        const val MINIMUM_NORMALIZED_AREA = 0.01f
        const val MINIMUM_EDGE_LENGTH = 0.02f

        fun fullImage() = CornerPoints(
            NormalizedPoint(0f, 0f),
            NormalizedPoint(1f, 0f),
            NormalizedPoint(1f, 1f),
            NormalizedPoint(0f, 1f)
        )

        /** Normalizes an arbitrary four-point contour to P0..P3, or rejects degenerate input. */
        fun fromUnordered(input: List<NormalizedPoint>): CornerPoints? {
            if (input.size != 4) return null
            val centerX = input.map(NormalizedPoint::x).average().toFloat()
            val centerY = input.map(NormalizedPoint::y).average().toFloat()
            val ordered = input.sortedBy { point -> atan2(point.y - centerY, point.x - centerX) }.toMutableList()
            val firstIndex = ordered.indices.minBy { ordered[it].x + ordered[it].y }
            val rotated = (ordered.drop(firstIndex) + ordered.take(firstIndex)).toMutableList()
            if (signedArea(rotated) < 0f) {
                val first = rotated.first()
                val clockwise = listOf(first, rotated[3], rotated[2], rotated[1])
                rotated.clear()
                rotated += clockwise
            }
            return CornerPoints(rotated[0], rotated[1], rotated[2], rotated[3]).takeIf(CornerPoints::isValid)
        }

        private fun signedArea(points: List<NormalizedPoint>): Float =
            points.indices.sumOf { index ->
                val current = points[index]
                val next = points[(index + 1) % points.size]
                (current.x * next.y - next.x * current.y).toDouble()
            }.toFloat() / 2f

        private fun distance(first: NormalizedPoint, second: NormalizedPoint): Float =
            hypot((first.x - second.x).toDouble(), (first.y - second.y).toDouble()).toFloat()

        private fun segmentsIntersect(
            a: NormalizedPoint,
            b: NormalizedPoint,
            c: NormalizedPoint,
            d: NormalizedPoint
        ): Boolean {
            fun direction(p: NormalizedPoint, q: NormalizedPoint, r: NormalizedPoint): Float =
                (q.x - p.x) * (r.y - p.y) - (q.y - p.y) * (r.x - p.x)
            val first = direction(a, b, c)
            val second = direction(a, b, d)
            val third = direction(c, d, a)
            val fourth = direction(c, d, b)
            return first * second < 0f && third * fourth < 0f
        }
    }
}

data class PixelPoint(val x: Float, val y: Float)

object PerspectiveSizing {
    const val DEFAULT_MAX_LONG_EDGE_PX = 3000

    fun outputSize(corners: List<PixelPoint>, maxLongEdgePx: Int = DEFAULT_MAX_LONG_EDGE_PX): ImageSize {
        require(corners.size == 4)
        require(maxLongEdgePx > 0)
        val top = distance(corners[0], corners[1])
        val bottom = distance(corners[3], corners[2])
        val left = distance(corners[0], corners[3])
        val right = distance(corners[1], corners[2])
        val unboundedWidth = maxOf(top, bottom).coerceAtLeast(1f)
        val unboundedHeight = maxOf(left, right).coerceAtLeast(1f)
        val scale = minOf(1f, maxLongEdgePx.toFloat() / maxOf(unboundedWidth, unboundedHeight))
        return ImageSize(
            width = (unboundedWidth * scale).toInt().coerceAtLeast(1),
            height = (unboundedHeight * scale).toInt().coerceAtLeast(1)
        )
    }

    private fun distance(first: PixelPoint, second: PixelPoint): Float =
        hypot((first.x - second.x).toDouble(), (first.y - second.y).toDouble()).toFloat()
}

data class ImageSize(val width: Int, val height: Int)

private fun <T> List<T>.zipWithNextCircular(): List<Pair<T, T>> =
    indices.map { index -> this[index] to this[(index + 1) % size] }
