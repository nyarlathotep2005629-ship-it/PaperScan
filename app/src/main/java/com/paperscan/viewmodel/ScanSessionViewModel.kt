package com.paperscan.viewmodel

import android.content.Context
import android.net.Uri
import androidx.camera.core.ImageCapture
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.paperscan.AppContainer
import com.paperscan.camera.CameraCaptureService
import com.paperscan.image.SourceImageLoader
import com.paperscan.importer.PhotoImportService
import com.paperscan.model.ScanSession
import com.paperscan.model.SessionOperations
import com.paperscan.model.CornerPoints
import com.paperscan.model.DetectionConfidence
import com.paperscan.model.NormalizedPoint
import com.paperscan.model.PageItem
import com.paperscan.model.PageStatus
import com.paperscan.model.ScanMode
import com.paperscan.model.SourceType
import com.paperscan.pdf.ExportResult
import com.paperscan.pdf.PdfExporter
import com.paperscan.processing.DocumentProcessor
import com.paperscan.storage.SessionStore
import com.paperscan.storage.WorkingFileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.util.UUID

data class UiError(val message: String)

data class ScanSessionUiState(
    val initialized: Boolean = false,
    val session: ScanSession? = null,
    val busy: Boolean = false,
    val currentOperation: String? = null,
    val error: UiError? = null,
    val recoveryAvailable: Boolean = false
)

data class PageEditUiState(
    val pageId: String? = null,
    val original: android.graphics.Bitmap? = null,
    val corrected: android.graphics.Bitmap? = null,
    val loading: Boolean = false,
    val error: String? = null
)

sealed interface ExportUiState {
    data object Idle : ExportUiState
    data class Processing(val current: Int, val total: Int) : ExportUiState
    data object ReadyToSave : ExportUiState
    data object Saving : ExportUiState
    data object Completed : ExportUiState
    data class Failed(val message: String) : ExportUiState
}

class ScanSessionViewModel(
    private val appContext: Context,
    private val workingFileStore: WorkingFileStore,
    private val sessionStore: SessionStore,
    private val photoImportService: PhotoImportService,
    private val sourceImageLoader: SourceImageLoader,
    private val documentProcessor: DocumentProcessor,
    private val pdfExporter: PdfExporter,
    private val cameraCaptureService: CameraCaptureService
) : ViewModel() {
    private val _uiState = MutableStateFlow(ScanSessionUiState())
    val uiState: StateFlow<ScanSessionUiState> = _uiState.asStateFlow()

    private val _exportUiState = MutableStateFlow<ExportUiState>(ExportUiState.Idle)
    val exportUiState: StateFlow<ExportUiState> = _exportUiState.asStateFlow()

    private val _pageEditUiState = MutableStateFlow(PageEditUiState())
    val pageEditUiState: StateFlow<PageEditUiState> = _pageEditUiState.asStateFlow()

    private var tempPdfFile: File? = null

    fun initialize() = viewModelScope.launch {
        workingFileStore.cleanupStaleCache()
        when (val result = sessionStore.load()) {
            SessionStore.LoadResult.Missing -> _uiState.value = ScanSessionUiState(initialized = true)
            is SessionStore.LoadResult.Loaded -> _uiState.value = ScanSessionUiState(
                initialized = true,
                session = result.session,
                recoveryAvailable = result.session.pages.isNotEmpty()
            )
            is SessionStore.LoadResult.Corrupted -> _uiState.value = ScanSessionUiState(
                initialized = true,
                error = UiError("作業データを読み込めませんでした。破棄して新しく開始できます。")
            )
        }
    }

    fun importImages(uris: List<Uri>) {
        if (uris.isEmpty() || _uiState.value.busy) return
        viewModelScope.launch {
            val current = _uiState.value
            _uiState.value = current.copy(busy = true, currentOperation = "画像を追加しています", error = null)
            val base = current.session ?: newSession()
            val result = photoImportService.importUris(
                uris = uris,
                existingUris = base.pages.mapNotNull { it.sourceUri }.toSet()
            )
            val importedIds = result.pages.map(PageItem::id).toSet()
            val detectedPages = (base.pages + result.pages).map { page ->
                if (page.id !in importedIds) {
                    page
                } else {
                    val detection = runCatching { documentProcessor.detect(page) }
                        .getOrElse { com.paperscan.processing.DetectionResult(null, DetectionConfidence.LOW) }
                    page.copy(
                        corners = detection.corners,
                        detectionConfidence = detection.confidence,
                        status = if (detection.confidence == DetectionConfidence.HIGH) PageStatus.READY else PageStatus.NEEDS_REVIEW
                    )
                }
            }
            val updated = base.copy(
                pages = detectedPages,
                selectedPageId = base.selectedPageId ?: result.pages.firstOrNull()?.id,
                updatedAtEpochMillis = now()
            )
            if (result.pages.isNotEmpty() && persist(updated)) {
                _uiState.value = ScanSessionUiState(
                    initialized = true,
                    session = updated,
                    recoveryAvailable = true,
                    error = importMessage(result)
                )
            } else {
                _uiState.value = current.copy(
                    busy = false,
                    currentOperation = null,
                    error = UiError("追加できる画像がありませんでした。")
                )
            }
        }
    }

    fun captureImage(
        imageCapture: ImageCapture,
        onCaptureAccepted: () -> Unit,
        onCaptureFailed: () -> Unit
    ) {
        if (_uiState.value.busy) return
        cameraCaptureService.capture(
            imageCapture = imageCapture,
            onSaved = { file ->
                addCapturedPage(file)
                onCaptureAccepted()
            },
            onFailure = { message ->
                _uiState.value = _uiState.value.copy(error = UiError(message))
                onCaptureFailed()
            }
        )
    }

    private fun addCapturedPage(file: File) {
        viewModelScope.launch {
            val current = _uiState.value
            _uiState.value = current.copy(busy = true, currentOperation = "撮影画像を追加しています", error = null)
            val pageId = UUID.randomUUID().toString()
            val result = runCatching {
                var page = PageItem(
                    id = pageId,
                    sourceType = SourceType.CAMERA,
                    sourceUri = null,
                    workingRelativePath = workingFileStore.relativeCapturePath(file),
                    thumbnailRelativePath = workingFileStore.relativeThumbnailPath(pageId),
                    sourceExifRotationDegrees = 0,
                    sourceExifFlipped = false,
                    rotationQuarterTurns = 0,
                    scanMode = ScanMode.AUTO,
                    status = PageStatus.NEEDS_REVIEW
                )
                val (rotation, flipped) = sourceImageLoader.readExif(page)
                page = page.copy(sourceExifRotationDegrees = rotation, sourceExifFlipped = flipped)
                sourceImageLoader.createThumbnail(page)
                val detection = documentProcessor.detect(page)
                page.copy(
                    corners = detection.corners,
                    detectionConfidence = detection.confidence,
                    status = if (detection.confidence == DetectionConfidence.HIGH) PageStatus.READY else PageStatus.NEEDS_REVIEW
                )
            }
            result.onSuccess { page ->
                val base = current.session ?: newSession()
                val updated = SessionOperations.appendPage(base, page, now())
                if (persist(updated)) {
                    _uiState.value = ScanSessionUiState(initialized = true, session = updated, recoveryAvailable = true)
                }
            }.onFailure {
                file.delete()
                workingFileStore.thumbnailFile(pageId).delete()
                _uiState.value = current.copy(
                    busy = false,
                    currentOperation = null,
                    error = UiError("撮影画像を追加できませんでした。")
                )
            }
        }
    }

    fun deletePage(pageId: String) {
        val current = _uiState.value
        val session = current.session ?: return
        if (current.busy) return
        viewModelScope.launch {
            val updated = SessionOperations.deletePage(session, pageId, now()) ?: return@launch
            _uiState.value = current.copy(busy = true, currentOperation = "ページを削除しています", error = null)
            if (persist(updated)) {
                session.pages.firstOrNull { it.id == pageId }?.let(workingFileStore::deletePageFiles)
                _uiState.value = ScanSessionUiState(
                    initialized = true,
                    session = updated,
                    recoveryAvailable = updated.pages.isNotEmpty()
                )
            }
        }
    }

    fun movePage(fromIndex: Int, toIndex: Int) {
        val current = _uiState.value
        val session = current.session ?: return
        if (current.busy) return
        viewModelScope.launch {
            val updated = SessionOperations.movePage(session, fromIndex, toIndex, now()) ?: return@launch
            _uiState.value = current.copy(busy = true, currentOperation = "ページ順を変更しています", error = null)
            if (persist(updated)) {
                _uiState.value = ScanSessionUiState(
                    initialized = true,
                    session = updated,
                    recoveryAvailable = true
                )
            }
        }
    }

    fun openEditor(pageId: String) {
        val page = _uiState.value.session?.pages?.firstOrNull { it.id == pageId } ?: return
        viewModelScope.launch {
            _pageEditUiState.value = _pageEditUiState.value.copy(pageId = pageId, loading = true, error = null)
            val previews = runCatching {
                val original = documentProcessor.loadOriginalPreview(page)
                try {
                    original to documentProcessor.createCorrectedPreview(page)
                } catch (failure: Throwable) {
                    original.recycle()
                    throw failure
                }
            }
            previews.onSuccess { (original, corrected) ->
                releaseEditorBitmaps()
                _pageEditUiState.value = PageEditUiState(pageId, original, corrected)
            }.onFailure { failure ->
                _pageEditUiState.value = PageEditUiState(pageId = pageId, error = "画像補正プレビューを作成できませんでした。")
            }
        }
    }

    fun closeEditor() {
        releaseEditorBitmaps()
        _pageEditUiState.value = PageEditUiState()
    }

    fun saveCorners(pageId: String, corners: CornerPoints) {
        if (!corners.isValid()) {
            _pageEditUiState.value = _pageEditUiState.value.copy(error = "四隅が交差しているか、範囲外または小さすぎます。")
            return
        }
        updatePage(pageId) { page ->
            page.copy(corners = corners, detectionConfidence = DetectionConfidence.MEDIUM, status = PageStatus.READY)
        }
    }

    fun rotatePageClockwise(pageId: String) {
        updatePage(pageId) { page ->
            val rotatedCorners = page.corners?.let(::rotateCornersClockwise)
            page.copy(
                rotationQuarterTurns = (page.rotationQuarterTurns + 1) % 4,
                corners = rotatedCorners
            )
        }
    }

    fun updateScanMode(pageId: String, mode: ScanMode) {
        updatePage(pageId, refreshEditor = true) { page -> page.copy(scanMode = mode) }
    }

    fun redetectPage(pageId: String) {
        val page = _uiState.value.session?.pages?.firstOrNull { it.id == pageId } ?: return
        if (_uiState.value.busy) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(busy = true, currentOperation = "紙面を検出しています", error = null)
            val result = runCatching { documentProcessor.detect(page) }
            result.onSuccess { detection ->
                updatePage(pageId, refreshEditor = true) { current ->
                    current.copy(
                        corners = detection.corners,
                        detectionConfidence = detection.confidence,
                        status = if (detection.confidence == DetectionConfidence.HIGH) PageStatus.READY else PageStatus.NEEDS_REVIEW
                    )
                }
            }.onFailure {
                _uiState.value = _uiState.value.copy(
                    busy = false,
                    currentOperation = null,
                    error = UiError("紙面を自動検出できませんでした。四隅を手動で指定してください。")
                )
            }
        }
    }

    fun generatePdf() {
        val session = _uiState.value.session ?: return
        if (_uiState.value.busy || session.pages.isEmpty()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(busy = true, currentOperation = "PDFを作成しています", error = null)
            _exportUiState.value = ExportUiState.Processing(0, session.pages.size)
            when (val result = pdfExporter.export(session) { current, total ->
                _exportUiState.value = ExportUiState.Processing(current, total)
            }) {
                is ExportResult.Success -> {
                    tempPdfFile = result.tempFile
                    _uiState.value = _uiState.value.copy(busy = false, currentOperation = null)
                    _exportUiState.value = ExportUiState.ReadyToSave
                }
                is ExportResult.Failure -> {
                    _uiState.value = _uiState.value.copy(
                        busy = false,
                        currentOperation = null,
                        error = UiError("PDFを作成できませんでした。作業データは保持されています。")
                    )
                    _exportUiState.value = ExportUiState.Failed("PDFの作成に失敗しました。")
                }
            }
        }
    }

    fun savePdfTo(destination: Uri) {
        val session = _uiState.value.session ?: return
        val source = tempPdfFile?.takeIf(File::exists) ?: run {
            _exportUiState.value = ExportUiState.Failed("保存するPDFがありません。もう一度作成してください。")
            return
        }
        if (_uiState.value.busy) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(busy = true, currentOperation = "PDFを保存しています", error = null)
            _exportUiState.value = ExportUiState.Saving
            val saved = runCatching {
                withContext(Dispatchers.IO) {
                    appContext.contentResolver.openOutputStream(destination, "w")?.use { output ->
                        source.inputStream().use { input -> input.copyTo(output) }
                    } ?: error("保存先を開けませんでした。")
                }
            }
            if (saved.isSuccess) {
                sessionStore.delete()
                workingFileStore.deleteSessionFiles()
                source.delete()
                tempPdfFile = null
                _uiState.value = ScanSessionUiState(initialized = true)
                _exportUiState.value = ExportUiState.Completed
            } else {
                runCatching { appContext.contentResolver.delete(destination, null, null) }
                _uiState.value = _uiState.value.copy(
                    busy = false,
                    currentOperation = null,
                    error = UiError("PDFを保存できませんでした。作業データは保持されています。")
                )
                _exportUiState.value = ExportUiState.ReadyToSave
            }
        }
    }

    fun onSaveCancelled() {
        if (_exportUiState.value is ExportUiState.ReadyToSave) {
            _uiState.value = _uiState.value.copy(
                error = UiError("保存先の選択をキャンセルしました。PDFはもう一度保存できます。")
            )
        }
    }

    fun discardSession() {
        if (_uiState.value.busy) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(busy = true, currentOperation = "作業データを破棄しています", error = null)
            sessionStore.delete()
            workingFileStore.deleteSessionFiles()
            tempPdfFile?.delete()
            tempPdfFile = null
            _uiState.value = ScanSessionUiState(initialized = true)
            _exportUiState.value = ExportUiState.Idle
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    suspend fun loadThumbnail(page: com.paperscan.model.PageItem) = sourceImageLoader.loadThumbnail(page)

    private fun updatePage(pageId: String, refreshEditor: Boolean = true, transform: (PageItem) -> PageItem) {
        val current = _uiState.value
        val session = current.session ?: return
        val oldPage = session.pages.firstOrNull { it.id == pageId } ?: return
        viewModelScope.launch {
            val updated = session.copy(
                pages = session.pages.map { page -> if (page.id == pageId) transform(page) else page },
                updatedAtEpochMillis = now()
            )
            _uiState.value = current.copy(busy = true, currentOperation = "ページ補正を保存しています", error = null)
            if (persist(updated)) {
                _uiState.value = ScanSessionUiState(initialized = true, session = updated, recoveryAvailable = true)
                if (refreshEditor && _pageEditUiState.value.pageId == pageId) openEditor(pageId)
            }
        }
    }

    private fun rotateCornersClockwise(corners: CornerPoints): CornerPoints? =
        CornerPoints.fromUnordered(corners.points.map { point -> NormalizedPoint(1f - point.y, point.x) })

    private fun releaseEditorBitmaps() {
        _pageEditUiState.value.original?.takeIf { !it.isRecycled }?.recycle()
        _pageEditUiState.value.corrected?.takeIf { !it.isRecycled }?.recycle()
    }

    override fun onCleared() {
        releaseEditorBitmaps()
        super.onCleared()
    }

    private suspend fun persist(session: ScanSession): Boolean {
        val saved = sessionStore.saveAtomic(session)
        if (saved.isFailure) {
            _uiState.value = _uiState.value.copy(
                busy = false,
                currentOperation = null,
                error = UiError("作業データを保存できませんでした。変更は反映されていません。")
            )
            return false
        }
        return true
    }

    private fun importMessage(result: com.paperscan.importer.ImportResult): UiError? = when {
        result.failures > 0 -> UiError("一部の画像を追加できませんでした。追加済みの画像は保持されています。")
        result.duplicateCount > 0 -> UiError("重複して選択された画像は追加しませんでした。")
        else -> null
    }

    private fun newSession(): ScanSession {
        val timestamp = now()
        return ScanSession(
            id = UUID.randomUUID().toString(),
            createdAtEpochMillis = timestamp,
            updatedAtEpochMillis = timestamp,
            pages = emptyList(),
            selectedPageId = null
        )
    }

    private fun now(): Long = Instant.now().toEpochMilli()

    class Factory(private val appContext: Context, private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ScanSessionViewModel::class.java))
            return ScanSessionViewModel(
                appContext.applicationContext,
                container.workingFileStore,
                container.sessionStore,
                container.photoImportService,
                container.sourceImageLoader,
                container.documentProcessor,
                container.pdfExporter,
                container.cameraCaptureService
            ) as T
        }
    }
}
