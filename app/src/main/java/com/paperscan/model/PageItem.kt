package com.paperscan.model

enum class SourceType { CAMERA, IMPORT }

enum class PageStatus { READY, NEEDS_REVIEW, ERROR }

enum class ScanMode { AUTO, TEXT, COLOR }

enum class DetectionConfidence { HIGH, MEDIUM, LOW }

enum class OcrLanguage(val label: String) {
    AUTO("自動"), LATIN("英語・ラテン文字"), CHINESE("中国語"), JAPANESE("日本語"), KOREAN("韓国語")
}

data class PageItem(
    val id: String,
    val sourceType: SourceType,
    val sourceUri: String?,
    val workingRelativePath: String?,
    val thumbnailRelativePath: String?,
    val sourceExifRotationDegrees: Int,
    val sourceExifFlipped: Boolean,
    val rotationQuarterTurns: Int,
    val scanMode: ScanMode,
    val status: PageStatus,
    val corners: CornerPoints? = null,
    val detectionConfidence: DetectionConfidence? = null,
    val ocrLanguage: OcrLanguage = OcrLanguage.AUTO,
    val ocrResult: PageOcrResult? = null
)
