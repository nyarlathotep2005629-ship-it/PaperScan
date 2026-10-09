package com.paperscan.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen(
    recoveryAvailable: Boolean,
    onCameraClick: () -> Unit,
    onImportClick: () -> Unit,
    onResumeClick: () -> Unit,
    onDiscardClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("PaperScan", style = MaterialTheme.typography.headlineMedium)
        Button(
            modifier = Modifier.fillMaxWidth().semantics { testTag = "home_camera" },
            onClick = onCameraClick
        ) { Text("撮影する") }
        Button(
            modifier = Modifier.fillMaxWidth().semantics { testTag = "home_import" },
            onClick = onImportClick
        ) { Text("画像から追加") }
        if (recoveryAvailable) {
            OutlinedButton(onClick = onResumeClick, modifier = Modifier.fillMaxWidth()) {
                Text("続きから")
            }
            OutlinedButton(onClick = onDiscardClick, modifier = Modifier.fillMaxWidth()) {
                Text("作業を破棄")
            }
        }
    }
}
