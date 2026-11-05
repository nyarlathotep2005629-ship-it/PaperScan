package com.paperscan.pdf

interface PdfExportOption {
    val label: String
}

enum class PdfQuality(override val label: String, val targetDpi: Int, val jpegQuality: Int) : PdfExportOption {
    HIGH("高画質", targetDpi = 300, jpegQuality = 92),
    STANDARD("標準", targetDpi = 200, jpegQuality = 85),
    COMPACT("軽量", targetDpi = 120, jpegQuality = 70)
}

enum class PdfPaperSize(override val label: String) : PdfExportOption {
    AUTO("自動"), A4("A4"), A5("A5"), LETTER("Letter")
}

enum class PdfOrientation(override val label: String) : PdfExportOption {
    AUTO("自動"), PORTRAIT("縦"), LANDSCAPE("横")
}

enum class PdfColorMode(override val label: String) : PdfExportOption {
    AUTO("自動"), COLOR("カラー"), GRAYSCALE("グレースケール"), BLACK_WHITE("白黒")
}

data class PdfExportSettings(
    val quality: PdfQuality = PdfQuality.STANDARD,
    val paperSize: PdfPaperSize = PdfPaperSize.AUTO,
    val orientation: PdfOrientation = PdfOrientation.AUTO,
    val colorMode: PdfColorMode = PdfColorMode.AUTO
)
