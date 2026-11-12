package com.paperscan.pdf

import com.paperscan.model.ScanSession
import com.paperscan.model.OcrStatus
import com.paperscan.model.ocrRevision
import com.paperscan.processing.DocumentProcessor
import com.paperscan.storage.WorkingFileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import java.io.FileOutputStream

class PdfExporter(
    private val documentProcessor: DocumentProcessor,
    private val workingFileStore: WorkingFileStore
) {
    suspend fun export(
        session: ScanSession,
        includeSearchableText: Boolean = false,
        settings: PdfExportSettings = PdfExportSettings(),
        onProgress: (current: Int, total: Int) -> Unit
    ): ExportResult = withContext(Dispatchers.IO) {
        if (session.pages.isEmpty()) {
            return@withContext ExportResult.Failure(null, IllegalStateException("ページがありません。"))
        }
        val output = workingFileStore.exportTempFile(session.id)
        var exportingPageIndex: Int? = null
        try {
            output.delete()
            JpegPdfWriter(output, session.pages.size, settings).use { document ->
                session.pages.forEachIndexed { index, page ->
                    exportingPageIndex = index
                    val bitmap = documentProcessor.processForExport(page)
                    try {
                        val textLines = if (includeSearchableText) {
                            page.ocrResult?.takeIf {
                                it.revision == page.ocrRevision() && it.status == OcrStatus.COMPLETE
                            }?.lines.orEmpty()
                        } else {
                            emptyList()
                        }
                        document.writePage(bitmap, textLines)
                    } finally {
                        bitmap.recycle()
                    }
                    onProgress(index + 1, session.pages.size)
                }
                document.finish()
            }
            check(output.exists() && output.length() > 0) { "PDFファイルを生成できませんでした。" }
            ExportResult.Success(output, session.pages.size, output.length())
        } catch (cancelled: CancellationException) {
            output.delete()
            throw cancelled
        } catch (failure: Throwable) {
            output.delete()
            ExportResult.Failure(exportingPageIndex, failure)
        }
    }
}
