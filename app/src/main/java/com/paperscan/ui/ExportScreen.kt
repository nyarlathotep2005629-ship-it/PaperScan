package com.paperscan.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import com.paperscan.viewmodel.ExportUiState

@Composable
fun ExportScreen(
    pageCount: Int,
    state: ExportUiState,
    onGenerateClick: () -> Unit,
    onSaveClick: () -> Unit,
    onBackClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("PDFを作成", style = MaterialTheme.typography.headlineSmall)
        Text("$pageCount ページをPDFにします。")
        when (state) {
            ExportUiState.Idle -> Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = onGenerateClick
            ) { Text("PDFを作成") }
            is ExportUiState.Processing -> {
                CircularProgressIndicator()
                Text(
                    "PDFを作成しています\n${state.current} / ${state.total} ページ",
                    modifier = Modifier.semantics { testTag = "export_progress" }
                )
            }
            ExportUiState.ReadyToSave -> Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = onSaveClick
            ) { Text("保存先を選択") }
            ExportUiState.Saving -> {
                CircularProgressIndicator()
                Text("PDFを保存しています")
            }
            ExportUiState.Completed -> Text("保存が完了しました。")
            is ExportUiState.Failed -> {
                Text(state.message)
                Button(onClick = onGenerateClick, modifier = Modifier.fillMaxWidth()) { Text("もう一度作成") }
            }
        }
        OutlinedButton(
            onClick = onBackClick,
            enabled = state !is ExportUiState.Processing && state !is ExportUiState.Saving,
            modifier = Modifier.fillMaxWidth()
        ) { Text("ページ一覧へ戻る") }
    }
}
