package com.paperscan.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import com.paperscan.pdf.PdfColorMode
import com.paperscan.pdf.PdfExportOption
import com.paperscan.pdf.PdfExportSettings
import com.paperscan.pdf.PdfOrientation
import com.paperscan.pdf.PdfPaperSize
import com.paperscan.pdf.PdfQuality
import com.paperscan.viewmodel.ExportUiState

@Composable
fun ExportScreen(
    pageCount: Int,
    state: ExportUiState,
    searchable: Boolean,
    settings: PdfExportSettings,
    onSearchableChange: (Boolean) -> Unit,
    onSettingsChange: (PdfExportSettings) -> Unit,
    onGenerate: () -> Unit,
    onChangeSettings: () -> Unit,
    onSaveClick: () -> Unit,
    onBackClick: () -> Unit
) {
    val canEdit = state == ExportUiState.Idle || state is ExportUiState.Failed
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("PDFを作成", style = MaterialTheme.typography.headlineSmall)
        Text("$pageCount ページをセッションの順番で出力します。")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = searchable, onCheckedChange = onSearchableChange, enabled = canEdit)
            Column {
                Text("検索可能PDF（OCR）")
                Text("初期設定: ON。認識できないページは画像として出力します。", style = MaterialTheme.typography.bodySmall)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ExportOptionPicker(
                label = "画質",
                selected = settings.quality,
                options = PdfQuality.entries,
                enabled = canEdit,
                modifier = Modifier.weight(1f),
                onSelected = { onSettingsChange(settings.copy(quality = it)) }
            )
            ExportOptionPicker(
                label = "用紙",
                selected = settings.paperSize,
                options = PdfPaperSize.entries,
                enabled = canEdit,
                modifier = Modifier.weight(1f),
                onSelected = { onSettingsChange(settings.copy(paperSize = it)) }
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ExportOptionPicker(
                label = "向き",
                selected = settings.orientation,
                options = PdfOrientation.entries,
                enabled = canEdit,
                modifier = Modifier.weight(1f),
                onSelected = { onSettingsChange(settings.copy(orientation = it)) }
            )
            ExportOptionPicker(
                label = "色",
                selected = settings.colorMode,
                options = PdfColorMode.entries,
                enabled = canEdit,
                modifier = Modifier.weight(1f),
                onSelected = { onSettingsChange(settings.copy(colorMode = it)) }
            )
        }
        when (state) {
            ExportUiState.Idle -> Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = onGenerate,
                enabled = pageCount > 0
            ) { Text(if (searchable) "検索可能PDFを作成" else "画像PDFを作成") }
            is ExportUiState.Processing -> {
                CircularProgressIndicator()
                Text(
                    "PDFを作成しています\n${state.current} / ${state.total} ページ",
                    modifier = Modifier.semantics { testTag = "export_progress" }
                )
            }
            is ExportUiState.ReadyToSave -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (state.searchable) "検索可能PDFができました。" else "画像PDFができました。")
                Button(modifier = Modifier.fillMaxWidth(), onClick = onSaveClick) { Text("保存先を選択") }
                OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onChangeSettings) {
                    Text("出力設定を変更して再作成")
                }
            }
            ExportUiState.Saving -> {
                CircularProgressIndicator()
                Text("PDFを保存しています")
            }
            ExportUiState.Completed -> Text("保存が完了しました。")
            is ExportUiState.Failed -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(state.message, color = MaterialTheme.colorScheme.error)
                Button(modifier = Modifier.fillMaxWidth(), onClick = onGenerate) {
                    Text("設定して再作成")
                }
            }
        }
        OutlinedButton(
            onClick = onBackClick,
            enabled = state !is ExportUiState.Processing && state !is ExportUiState.Saving,
            modifier = Modifier.fillMaxWidth()
        ) { Text("ページ一覧へ戻る") }
    }
}

@Composable
private fun <T> ExportOptionPicker(
    label: String,
    selected: T,
    options: List<T>,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onSelected: (T) -> Unit
) where T : Enum<T>, T : PdfExportOption {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { expanded = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
            Text("$label: ${selected.label}")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}
