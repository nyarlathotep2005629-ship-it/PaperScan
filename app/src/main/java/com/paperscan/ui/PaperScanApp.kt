package com.paperscan.ui

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.paperscan.pdf.PdfFileName
import com.paperscan.pdf.PdfExportSettings
import com.paperscan.viewmodel.ExportUiState
import com.paperscan.viewmodel.ScanSessionViewModel

private enum class AppScreen { HOME, CAMERA, SESSION, EDIT, EXPORT, COMPLETE }

@Composable
fun PaperScanApp(viewModel: ScanSessionViewModel) {
    val context = LocalContext.current
    val preferences = remember {
        context.getSharedPreferences("paperscan_preferences", Context.MODE_PRIVATE)
    }
    var themeMode by remember {
        mutableStateOf(
            preferences.getString(THEME_MODE_KEY, ThemeMode.SYSTEM.name)
                ?.let { value -> ThemeMode.entries.firstOrNull { it.name == value } }
                ?: ThemeMode.SYSTEM
        )
    }
    var showThemeDialog by remember { mutableStateOf(false) }
    val uiState by viewModel.uiState.collectAsState()
    val exportState by viewModel.exportUiState.collectAsState()
    val editState by viewModel.pageEditUiState.collectAsState()
    var screen by remember { mutableStateOf(AppScreen.HOME) }
    var searchablePdf by remember { mutableStateOf(true) }
    var pdfSettings by remember { mutableStateOf(PdfExportSettings()) }
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

    val useDarkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(colorScheme = if (useDarkTheme) darkColorScheme() else lightColorScheme()) {
        when (screen) {
            AppScreen.HOME -> HomeScreen(
                recoveryAvailable = uiState.recoveryAvailable,
                themeLabel = themeMode.label,
                onThemeClick = { showThemeDialog = true },
                onCameraClick = { screen = AppScreen.CAMERA },
                onImportClick = {
                    photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onResumeClick = { screen = AppScreen.SESSION },
                onDiscardClick = viewModel::discardSession
            )
            AppScreen.CAMERA -> CameraScreen(
                pageCount = uiState.session?.pages?.size ?: 0,
                latestPage = uiState.session?.pages?.lastOrNull(),
                busy = uiState.busy,
                viewModel = viewModel,
                onCapture = { capture, onAccepted, onFailure ->
                    viewModel.captureImage(capture, onCaptureAccepted = onAccepted, onCaptureFailed = onFailure)
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
                        onCornerChanged = { index, point -> viewModel.updateEditorCorner(page.id, index, point) },
                        onSaveCorners = { viewModel.saveCorners(page.id) },
                        onScanModeChanged = { viewModel.updateScanMode(page.id, it) },
                        onOcrLanguageChanged = { viewModel.updateOcrLanguage(page.id, it) },
                        onRotate = { viewModel.rotatePageClockwise(page.id) },
                        onRedetect = { viewModel.redetectPage(page.id) },
                        onRecognize = { viewModel.recognizePage(page.id) },
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
                searchable = searchablePdf,
                settings = pdfSettings,
                onSearchableChange = { searchablePdf = it },
                onSettingsChange = { pdfSettings = it },
                onGenerate = { viewModel.generatePdf(searchable = searchablePdf, settings = pdfSettings) },
                onChangeSettings = viewModel::changePdfSettings,
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
        if (showThemeDialog) {
            AlertDialog(
                onDismissRequest = { showThemeDialog = false },
                title = { Text("表示テーマ") },
                text = {
                    Column {
                        ThemeMode.entries.forEach { option ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 2.dp)
                            ) {
                                RadioButton(
                                    selected = themeMode == option,
                                    onClick = {
                                        themeMode = option
                                        preferences.edit().putString(THEME_MODE_KEY, option.name).apply()
                                        showThemeDialog = false
                                    }
                                )
                                Text(option.label)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showThemeDialog = false }) { Text("閉じる") }
                }
            )
        }
    }
}

private enum class ThemeMode(val label: String) {
    SYSTEM("システム"), LIGHT("ライト"), DARK("ダーク")
}

private const val THEME_MODE_KEY = "theme_mode"
