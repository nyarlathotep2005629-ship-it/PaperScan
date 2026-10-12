package com.paperscan.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import com.paperscan.pdf.PdfFileName
import com.paperscan.viewmodel.ExportUiState
import com.paperscan.viewmodel.ScanSessionViewModel

private enum class AppScreen { HOME, CAMERA, SESSION, EDIT, EXPORT, COMPLETE }

@Composable
fun PaperScanApp(viewModel: ScanSessionViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val exportState by viewModel.exportUiState.collectAsState()
    val editState by viewModel.pageEditUiState.collectAsState()
    var screen by remember { mutableStateOf(AppScreen.HOME) }
    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(30)
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importImages(uris)
            screen = AppScreen.SESSION
        }
    }
    val createDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri == null) viewModel.onSaveCancelled() else viewModel.savePdfTo(uri)
    }

    LaunchedEffect(Unit) { viewModel.initialize() }
    LaunchedEffect(exportState) {
        if (exportState is ExportUiState.Completed) screen = AppScreen.COMPLETE
    }

    MaterialTheme {
        when (screen) {
            AppScreen.HOME -> HomeScreen(
                recoveryAvailable = uiState.recoveryAvailable,
                onCameraClick = { screen = AppScreen.CAMERA },
                onImportClick = {
                    photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onResumeClick = { screen = AppScreen.SESSION },
                onDiscardClick = viewModel::discardSession
            )
            AppScreen.CAMERA -> CameraScreen(
                pageCount = uiState.session?.pages?.size ?: 0,
                busy = uiState.busy,
                onCapture = { capture, onFailure ->
                    viewModel.captureImage(capture, onCaptureAccepted = { screen = AppScreen.SESSION }, onCaptureFailed = onFailure)
                },
                onBack = { screen = if (uiState.session?.pages?.isNotEmpty() == true) AppScreen.SESSION else AppScreen.HOME }
            )
            AppScreen.SESSION -> SessionScreen(
                session = uiState.session,
                busy = uiState.busy,
                viewModel = viewModel,
                onCameraClick = { screen = AppScreen.CAMERA },
                onImportClick = {
                    photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onCreatePdfClick = { screen = AppScreen.EXPORT },
                onEditPage = { pageId ->
                    viewModel.openEditor(pageId)
                    screen = AppScreen.EDIT
                },
                onHomeClick = { screen = AppScreen.HOME }
            )
            AppScreen.EDIT -> {
                val page = uiState.session?.pages?.firstOrNull { it.id == editState.pageId }
                if (page == null) {
                    LaunchedEffect(Unit) { screen = AppScreen.SESSION }
                } else {
                    PageEditScreen(
                        page = page,
                        state = editState,
                        busy = uiState.busy,
                        onSaveCorners = { viewModel.saveCorners(page.id, it) },
                        onScanModeChanged = { viewModel.updateScanMode(page.id, it) },
                        onRotate = { viewModel.rotatePageClockwise(page.id) },
                        onRedetect = { viewModel.redetectPage(page.id) },
                        onBack = {
                            viewModel.closeEditor()
                            screen = AppScreen.SESSION
                        }
                    )
                }
            }
            AppScreen.EXPORT -> ExportScreen(
                pageCount = uiState.session?.pages?.size ?: 0,
                state = exportState,
                onGenerateClick = viewModel::generatePdf,
                onSaveClick = { createDocument.launch(PdfFileName.suggested()) },
                onBackClick = { screen = AppScreen.SESSION }
            )
            AppScreen.COMPLETE -> CompleteScreen(onHomeClick = { screen = AppScreen.HOME })
        }
        uiState.error?.let { error ->
            AlertDialog(
                onDismissRequest = viewModel::clearError,
                title = { Text("PaperScan") },
                text = { Text(error.message) },
                confirmButton = {
                    TextButton(onClick = viewModel::clearError) { Text("閉じる") }
                }
            )
        }
    }
}
