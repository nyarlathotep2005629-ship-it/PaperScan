package com.paperscan.processing

import android.graphics.Bitmap
import com.paperscan.model.CornerPoints
import com.paperscan.model.DetectionConfidence
import com.paperscan.model.PageItem

data class DetectionResult(
    val corners: CornerPoints?,
    val confidence: DetectionConfidence,
    val score: Float = 0f
)

interface DocumentProcessor {
    suspend fun detect(page: PageItem): DetectionResult
    suspend fun loadOriginalPreview(page: PageItem): Bitmap
    suspend fun createCorrectedPreview(page: PageItem): Bitmap
    suspend fun processForExport(page: PageItem): Bitmap
}
