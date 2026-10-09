package com.paperscan.pdf

import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import com.paperscan.model.ScanSession
import com.paperscan.processing.DocumentProcessor
import com.paperscan.storage.WorkingFileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileOutputStream

class PdfExporter(
    private val documentProcessor: DocumentProcessor,
    private val workingFileStore: WorkingFileStore
) {
    suspend fun export(
        session: ScanSession,
        onProgress: (current: Int, total: Int) -> Unit
    ): ExportResult = withContext(Dispatchers.IO) {
        if (session.pages.isEmpty()) {
            return@withContext ExportResult.Failure(null, IllegalStateException("ページがありません。"))
        }
        val output = workingFileStore.exportTempFile(session.id)
        var exportingPageIndex: Int? = null
        try {
            output.delete()
            val document = PdfDocument()
            try {
                session.pages.forEachIndexed { index, page ->
                    exportingPageIndex = index
                    val bitmap = documentProcessor.processForExport(page)
                    try {
                        val size = PdfPageSizer.pageSizeFor(bitmap.width, bitmap.height)
                        val pageInfo = PdfDocument.PageInfo.Builder(
                            size.widthPoints,
                            size.heightPoints,
                            index + 1
                        ).create()
                        val pdfPage = document.startPage(pageInfo)
                        try {
                            pdfPage.canvas.drawColor(Color.WHITE)
                            val draw = PdfPageSizer.fitRect(size, bitmap.width, bitmap.height)
                            pdfPage.canvas.drawBitmap(
                                bitmap,
                                null,
                                RectF(draw.left, draw.top, draw.right, draw.bottom),
                                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                            )
                        } finally {
                            document.finishPage(pdfPage)
                        }
                    } finally {
                        bitmap.recycle()
                    }
                    onProgress(index + 1, session.pages.size)
                }
                FileOutputStream(output).use(document::writeTo)
            } finally {
                document.close()
            }
            check(output.exists() && output.length() > 0) { "PDFファイルを生成できませんでした。" }
            ExportResult.Success(output, session.pages.size, output.length())
        } catch (failure: Throwable) {
            output.delete()
            ExportResult.Failure(exportingPageIndex, failure)
        }
    }
}
