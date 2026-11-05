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
import com.paperscan.model.CornerEditDraft
import com.paperscan.model.DetectionConfidence
import com.paperscan.model.NormalizedPoint
import com.paperscan.model.PageItem
import com.paperscan.model.PageStatus
import com.paperscan.model.PageOcrResult
import com.paperscan.model.OcrStatus
import com.paperscan.model.ocrRevision
import com.paperscan.model.ScanMode
import com.paperscan.model.SourceType
import com.paperscan.pdf.ExportResult
import com.paperscan.pdf.PdfExporter
import com.paperscan.pdf.PdfExportSettings
import com.paperscan.ocr.MlKitOcrService
import com.paperscan.processing.DocumentProcessor
import com.paperscan.storage.SessionStore
import com.paperscan.storage.WorkingFileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    val cornerDraft: CornerEditDraft? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val ocrRunning: Boolean = false,
    val ocrError: String? = null
)

sealed interface ExportUiState {
    data object Idle : ExportUiState
    data class Processing(val current: Int, val total: Int) : ExportUiState
    data class ReadyToSave(val searchable: Boolean) : ExportUiState
    data object Saving : ExportUiState
    data object Completed : ExportUiState
    data class Failed(val message: String, val searchable: Boolean = false) : ExportUiState
}

class ScanSessionViewModel(
    private val appContext: Context,
    private val workingFileStore: WorkingFileStore,
    private val sessionStore: SessionStore,
    private val photoImportService: PhotoImportService,
    private val sourceImageLoader: SourceImageLoader,
    private val documentProcessor: DocumentProcessor,
    private val pdfExporter: PdfExporter,
    private val cameraCaptureService: CameraCaptureService,
    private val ocrService: MlKitOcrService
) : ViewModel() {
    private val _uiState = MutableStateFlow(ScanSessionUiState())
    val uiState: StateFlow<ScanSessionUiState> = _uiState.asStateFlow()

    private val _exportUiState = MutableStateFlow<ExportUiState>(ExportUiState.Idle)
    val exportUiState: StateFlow<ExportUiState> = _exportUiState.asStateFlow()

    private val _pageEditUiState = MutableStateFlow(PageEditUiState())
    val pageEditUiState: StateFlow<PageEditUiState> = _pageEditUiState.asStateFlow()

    private var tempPdfFile: File? = null
    private var tempPdfSearchable = false
    private var editorLoadGeneration = 0L
    private val ocrJobs = mutableMapOf<String, Job>()

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
            val updated = base.copy(
                pages = base.pages + result.pages,
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
                page
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
        ocrJobs.remove(pageId)?.cancel()
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

    fun applyScanModeToPages(pageIds: Set<String>, mode: ScanMode) {
        val current = _uiState.value
        val session = current.session ?: return
        if (current.busy || pageIds.isEmpty()) return
        val changedIds = session.pages.filter { it.id in pageIds && it.scanMode != mode }.map(PageItem::id)
        if (changedIds.isEmpty()) return
        changedIds.forEach { id -> ocrJobs.remove(id)?.cancel() }
        viewModelScope.launch {
            val latestState = _uiState.value
            val latestSession = latestState.session ?: return@launch
            if (latestState.busy) return@launch
            val updated = SessionOperations.applyScanMode(latestSession, changedIds.toSet(), mode, now()) ?: return@launch
            _uiState.value = latestState.copy(busy = true, currentOperation = "選択ページを補正しています", error = null)
            if (persist(updated)) {
                _uiState.value = ScanSessionUiState(
                    initialized = true,
                    session = updated,
                    recoveryAvailable = updated.pages.isNotEmpty()
                )
                changedIds.forEach(::recognizePageInternal)
            }
        }
    }

    fun openEditor(pageId: String) {
        openEditor(pageId, draftOverride = null)
    }

    private fun openEditor(pageId: String, draftOverride: CornerEditDraft?) {
        val page = _uiState.value.session?.pages?.firstOrNull { it.id == pageId } ?: return
        val generation = ++editorLoadGeneration
        viewModelScope.launch {
            _pageEditUiState.value = _pageEditUiState.value.copy(
                pageId = pageId,
                cornerDraft = draftOverride ?: _pageEditUiState.value.cornerDraft.takeIf {
                    _pageEditUiState.value.pageId == pageId
                },
                loading = true,
                error = null
            )
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
                val detection = when {
                    draftOverride != null || page.corners != null -> null
                    else -> runCatching { documentProcessor.detect(page) }.getOrNull()
                }
                val draft = draftOverride
                    ?: page.corners?.let(CornerEditDraft::fromSaved)
                    ?: detection?.let { CornerEditDraft.fromDetection(it.corners, it.confidence) }
                    ?: CornerEditDraft.manualFallback()
                if (generation != editorLoadGeneration || _pageEditUiState.value.pageId != pageId) {
                    original.recycle()
                    corrected.recycle()
                    return@onSuccess
                }
                releaseEditorBitmaps()
                _pageEditUiState.value = PageEditUiState(
                    pageId = pageId,
                    original = original,
                    corrected = corrected,
                    cornerDraft = draft,
                    error = if (page.corners == null && draftOverride == null && detection?.corners == null) {
                        "紙面を自動検出できませんでした。四隅を手動で調整してください。"
                    } else {
                        null
                    }
                )
            }.onFailure {
                if (generation == editorLoadGeneration) {
                    _pageEditUiState.value = PageEditUiState(
                        pageId = pageId,
                        cornerDraft = draftOverride ?: page.corners?.let(CornerEditDraft::fromSaved),
                        error = "画像補正プレビューを作成できませんでした。"
                    )
                }
            }
        }
    }

    fun closeEditor() {
        editorLoadGeneration++
        releaseEditorBitmaps()
        _pageEditUiState.value = PageEditUiState()
    }

    fun updateEditorCorner(pageId: String, index: Int, point: NormalizedPoint) {
        if (_uiState.value.busy) return
        val state = _pageEditUiState.value
        val draft = state.cornerDraft ?: return
        if (state.pageId != pageId) return
        _pageEditUiState.value = state.copy(cornerDraft = draft.moveCorner(index, point), error = null)
    }

    fun saveCorners(pageId: String) {
        val draft = _pageEditUiState.value.cornerDraft
        val corners = draft?.cornersToCommit()
        if (corners == null) {
            _pageEditUiState.value = _pageEditUiState.value.copy(
                error = "四隅が交差しているか、凹形、範囲外または小さすぎるため保存できません。"
            )
            return
        }
        updatePage(pageId, recognizeAfter = true) { page ->
            page.copy(corners = corners, detectionConfidence = DetectionConfidence.MEDIUM, status = PageStatus.READY)
        }
    }

    fun rotatePageClockwise(pageId: String) {
        val rotatedDraft = _pageEditUiState.value.cornerDraft?.rotateClockwise()
        updatePage(pageId, preserveEditorDraft = true, draftOverride = rotatedDraft, recognizeAfter = true) { page ->
            page.copy(
                rotationQuarterTurns = (page.rotationQuarterTurns + 1) % 4,
                corners = page.corners?.rotateClockwise()
            )
        }
    }

    fun updateScanMode(pageId: String, mode: ScanMode) {
        updatePage(
            pageId,
            refreshEditor = true,
            preserveEditorDraft = true,
            draftOverride = _pageEditUiState.value.cornerDraft,
            recognizeAfter = true
        ) { page -> page.copy(scanMode = mode) }
    }

    fun recognizePage(pageId: String) {
        if (_uiState.value.busy) return
        ocrJobs.remove(pageId)?.cancel()
        recognizePageInternal(pageId, force = true)
    }

    fun redetectPage(pageId: String) {
        val page = _uiState.value.session?.pages?.firstOrNull { it.id == pageId } ?: return
        if (_uiState.value.busy) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(busy = true, currentOperation = "紙面を検出しています", error = null)
            val result = runCatching { documentProcessor.detect(page) }
            result.onSuccess { detection ->
                val currentEditor = _pageEditUiState.value
                val updatedDraft = currentEditor.cornerDraft?.applyDetection(
                    detection.corners,
                    detection.confidence
                )
                if (currentEditor.pageId == pageId && updatedDraft != null) {
                    _pageEditUiState.value = currentEditor.copy(cornerDraft = updatedDraft, error = null)
                } else if (currentEditor.pageId == pageId) {
                    _pageEditUiState.value = currentEditor.copy(
                        error = "紙面を自動検出できませんでした。現在の四隅は維持されています。"
                    )
                }
                _uiState.value = _uiState.value.copy(busy = false, currentOperation = null)
            }.onFailure {
                _uiState.value = _uiState.value.copy(
                    busy = false,
                    currentOperation = null,
                    error = UiError("紙面を自動検出できませんでした。現在の四隅は維持されています。")
                )
            }
        }
    }

    fun generatePdf(searchable: Boolean = false, settings: PdfExportSettings = PdfExportSettings()) {
        val session = _uiState.value.session ?: return
        if (_uiState.value.busy || session.pages.isEmpty()) return
        viewModelScope.launch {
            tempPdfSearchable = searchable
            _uiState.value = _uiState.value.copy(busy = true, currentOperation = "PDFを作成しています", error = null)
            ocrJobs.values.forEach(Job::cancel)
            ocrJobs.clear()
            _exportUiState.value = ExportUiState.Processing(0, session.pages.size)
            var exportSession = session
            if (searchable) {
                session.pages.forEachIndexed { index, page ->
                    val stored = page.ocrResult?.takeIf { it.revision == page.ocrRevision() }
                    val ocrResult = stored ?: runCatching { recognizePageContent(page) }.getOrElse {
                        PageOcrResult(
                            revision = page.ocrRevision(),
                            status = OcrStatus.FAILED,
                            lines = emptyList(),
                            updatedAtEpochMillis = now()
                        )
                    }
                    exportSession = exportSession.copy(
                        pages = exportSession.pages.map { if (it.id == page.id) it.copy(ocrResult = ocrResult) else it },
                        updatedAtEpochMillis = now()
                    )
                    if (persist(exportSession)) {
                        _uiState.value = _uiState.value.copy(session = exportSession, recoveryAvailable = true)
                    }
                    _exportUiState.value = ExportUiState.Processing(index + 1, session.pages.size)
                }
            }
            when (val result = pdfExporter.export(
                exportSession,
                includeSearchableText = searchable,
                settings = settings
            ) { current, total ->
                _exportUiState.value = ExportUiState.Processing(current, total)
            }) {
                is ExportResult.Success -> {
                    tempPdfFile = result.tempFile
                    _uiState.value = _uiState.value.copy(busy = false, currentOperation = null)
                    _exportUiState.value = ExportUiState.ReadyToSave(searchable)
                }
                is ExportResult.Failure -> {
                    _uiState.value = _uiState.value.copy(
                        busy = false,
                        currentOperation = null,
                        error = UiError("PDFを作成できませんでした。作業データは保持されています。")
                    )
                    _exportUiState.value = ExportUiState.Failed("PDFの作成に失敗しました。", searchable)
                }
            }
        }
    }

    fun savePdfTo(destination: Uri) {
        val session = _uiState.value.session ?: return
        val source = tempPdfFile?.takeIf(File::exists) ?: run {
            _exportUiState.value = ExportUiState.Failed("保存するPDFがありません。もう一度作成してください。", tempPdfSearchable)
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
                _exportUiState.value = ExportUiState.ReadyToSave(tempPdfSearchable)
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

    fun changePdfSettings() {
        if (_exportUiState.value !is ExportUiState.ReadyToSave) return
        tempPdfFile?.delete()
        tempPdfFile = null
        _exportUiState.value = ExportUiState.Idle
    }

    fun discardSession() {
        if (_uiState.value.busy) return
        ocrJobs.values.forEach(Job::cancel)
        ocrJobs.clear()
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

    private fun updatePage(
        pageId: String,
        refreshEditor: Boolean = true,
        preserveEditorDraft: Boolean = false,
        draftOverride: CornerEditDraft? = null,
        recognizeAfter: Boolean = false,
        transform: (PageItem) -> PageItem
    ) {
        val current = _uiState.value
        val session = current.session ?: return
        val oldPage = session.pages.firstOrNull { it.id == pageId } ?: return
        ocrJobs.remove(pageId)?.cancel()
        viewModelScope.launch {
            val latestState = _uiState.value
            val latestSession = latestState.session ?: return@launch
            val updated = latestSession.copy(
                pages = latestSession.pages.map { page ->
                    if (page.id != pageId) page else transform(page).let { transformed ->
                        if (transformed.ocrRevision() != page.ocrRevision()) transformed.copy(ocrResult = null) else transformed
                    }
                },
                updatedAtEpochMillis = now()
            )
            _uiState.value = latestState.copy(busy = true, currentOperation = "ページ補正を保存しています", error = null)
            if (persist(updated)) {
                _uiState.value = ScanSessionUiState(initialized = true, session = updated, recoveryAvailable = true)
                if (refreshEditor && _pageEditUiState.value.pageId == pageId) {
                    openEditor(pageId, if (preserveEditorDraft) draftOverride else null)
                }
                if (recognizeAfter) recognizePageInternal(pageId)
            }
        }
    }

    private fun releaseEditorBitmaps() {
        _pageEditUiState.value.original?.takeIf { !it.isRecycled }?.recycle()
        _pageEditUiState.value.corrected?.takeIf { !it.isRecycled }?.recycle()
    }

    override fun onCleared() {
        ocrJobs.values.forEach(Job::cancel)
        ocrJobs.clear()
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

    private fun recognizePageInternal(pageId: String, force: Boolean = false) {
        if (ocrJobs[pageId]?.isActive == true) return
        val page = _uiState.value.session?.pages?.firstOrNull { it.id == pageId } ?: return
        val revision = page.ocrRevision()
        if (!force && page.ocrResult?.let { it.revision == revision && it.status == OcrStatus.COMPLETE } == true) return
        lateinit var job: Job
        job = viewModelScope.launch {
            if (_pageEditUiState.value.pageId == pageId) {
                _pageEditUiState.value = _pageEditUiState.value.copy(ocrRunning = true, ocrError = null)
            }
            try {
                val result = recognizePageContent(page)
                persistOcrResult(pageId, revision, result)
                if (_pageEditUiState.value.pageId == pageId) {
                    _pageEditUiState.value = _pageEditUiState.value.copy(
                        ocrRunning = false,
                        ocrError = if (result.status == OcrStatus.FAILED) "文字を認識できませんでした。再試行できます。" else null
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                if (_pageEditUiState.value.pageId == pageId) {
                    _pageEditUiState.value = _pageEditUiState.value.copy(
                        ocrRunning = false,
                        ocrError = "OCRに失敗しました。画像と編集内容は保持されています。"
                    )
                }
            } finally {
                if (ocrJobs[pageId] === job) ocrJobs.remove(pageId)
            }
        }
        ocrJobs[pageId] = job
    }

    private suspend fun persistOcrResult(pageId: String, revision: String, result: PageOcrResult): Boolean {
        while (_uiState.value.busy) delay(25)
        val latestState = _uiState.value
        val latestSession = latestState.session ?: return false
        val latestPage = latestSession.pages.firstOrNull { it.id == pageId } ?: return false
        if (latestPage.ocrRevision() != revision) return false
        val updated = latestSession.copy(
            pages = latestSession.pages.map { if (it.id == pageId) it.copy(ocrResult = result) else it },
            updatedAtEpochMillis = now()
        )
        _uiState.value = latestState.copy(busy = true, currentOperation = "OCR結果を保存しています")
        if (!persist(updated)) return false
        _uiState.value = _uiState.value.copy(
            busy = false,
            currentOperation = null,
            session = updated,
            recoveryAvailable = true
        )
        return true
    }

    private suspend fun recognizePageContent(page: PageItem): PageOcrResult {
        val bitmap = withContext(Dispatchers.IO) { documentProcessor.processForExport(page) }
        return try {
            ocrService.recognize(bitmap, page.ocrRevision(), now())
        } finally {
            bitmap.recycle()
        }
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
                container.cameraCaptureService,
                container.ocrService
            ) as T
        }
    }
}
