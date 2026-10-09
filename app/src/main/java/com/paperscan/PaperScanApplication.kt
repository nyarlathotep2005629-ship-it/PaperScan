package com.paperscan

import android.app.Application
import android.content.Context
import androidx.core.content.ContextCompat
import com.paperscan.camera.CameraCaptureService
import com.paperscan.image.SourceImageLoader
import com.paperscan.importer.PhotoImportService
import com.paperscan.pdf.PdfExporter
import com.paperscan.processing.OpenCvDocumentProcessor
import com.paperscan.storage.SessionStore
import com.paperscan.storage.WorkingFileStore

class PaperScanApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class AppContainer(context: Context) {
    val workingFileStore = WorkingFileStore(context)
    val sessionStore = SessionStore(workingFileStore)
    val sourceImageLoader = SourceImageLoader(context, workingFileStore)
    val documentProcessor = OpenCvDocumentProcessor(sourceImageLoader)
    val photoImportService = PhotoImportService(context, workingFileStore, sourceImageLoader)
    val pdfExporter = PdfExporter(documentProcessor, workingFileStore)
    val cameraCaptureService = CameraCaptureService(workingFileStore, ContextCompat.getMainExecutor(context))
}
