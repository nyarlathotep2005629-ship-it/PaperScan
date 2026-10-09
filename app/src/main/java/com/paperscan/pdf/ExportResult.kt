package com.paperscan.pdf

import java.io.File

sealed interface ExportResult {
    data class Success(val tempFile: File, val pageCount: Int, val sizeBytes: Long) : ExportResult
    data class Failure(val pageIndex: Int?, val cause: Throwable) : ExportResult
}
