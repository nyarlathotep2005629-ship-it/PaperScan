package com.paperscan.ui

import android.graphics.Bitmap
import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.paperscan.model.CornerPoints
import com.paperscan.model.DisplayPoint
import com.paperscan.model.DisplayedImageBounds
import com.paperscan.model.NormalizedPoint
import com.paperscan.model.PageItem
import com.paperscan.model.OcrStatus
import com.paperscan.model.ScanMode
import com.paperscan.model.ocrRevision
import com.paperscan.viewmodel.PageEditUiState

@Composable
fun PageEditScreen(
    page: PageItem,
    state: PageEditUiState,
    busy: Boolean,
    onCornerChanged: (Int, NormalizedPoint) -> Unit,
    onSaveCorners: () -> Unit,
    onScanModeChanged: (ScanMode) -> Unit,
    onRotate: () -> Unit,
    onRedetect: () -> Unit,
    onRecognize: () -> Unit,
    onBack: () -> Unit
) {
    var showCorrected by remember(page.id) { mutableStateOf(false) }
    val corners = state.cornerDraft?.editableCorners ?: page.corners ?: CornerPoints.fullImage()
    val preview = if (showCorrected) state.corrected ?: state.original else state.original
    val context = LocalContext.current
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    val currentOcr = page.ocrResult?.takeIf { it.revision == page.ocrRevision() && it.status == OcrStatus.COMPLETE }
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("ページ補正", style = MaterialTheme.typography.headlineSmall)
        Text(
            if (showCorrected) "補正後プレビュー" else "元画像: 四隅をドラッグして紙面を指定",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(8.dp))
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            if (preview != null) {
                if (showCorrected) {
                    PreviewCanvas(preview, null) { _, _ -> }
                } else {
                    PreviewCanvas(preview, corners) { index, point ->
                        onCornerChanged(index, point)
                    }
                }
            } else {
                Text(if (state.loading) "プレビューを作成しています…" else "プレビューを表示できません。")
            }
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { showCorrected = false }, modifier = Modifier.weight(1f)) { Text("元画像") }
            OutlinedButton(onClick = { showCorrected = true }, modifier = Modifier.weight(1f)) { Text("補正後") }
        }
        Text("画質モード", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ScanMode.entries.forEach { mode ->
                val selected = page.scanMode == mode
                if (selected) {
                    Button(
                        onClick = { onScanModeChanged(mode) },
                        enabled = !busy && !state.loading,
                        modifier = Modifier.weight(1f).semantics { testTag = "scan_mode_${mode.name}" }
                    ) { Text(mode.name) }
                } else {
                    OutlinedButton(
                        onClick = { onScanModeChanged(mode) },
                        enabled = !busy && !state.loading,
                        modifier = Modifier.weight(1f).semantics { testTag = "scan_mode_${mode.name}" }
                    ) { Text(mode.name) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onRotate, enabled = !busy && !state.loading, modifier = Modifier.weight(1f)) { Text("90°回転") }
            OutlinedButton(
                onClick = {
                    showCorrected = false
                    onRedetect()
                },
                enabled = !busy && !state.loading,
                modifier = Modifier.weight(1f)
            ) { Text("再検出") }
        }
        Button(
            onClick = onSaveCorners,
            enabled = !busy && !state.loading && !showCorrected && state.cornerDraft != null,
            modifier = Modifier.fillMaxWidth().semantics { testTag = "page_edit_save" }
        ) { Text("四隅を保存") }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = onRecognize,
                enabled = !busy && !state.loading && !state.ocrRunning,
                modifier = Modifier.weight(1f)
            ) { Text(if (state.ocrRunning) "OCR中…" else "OCRを再認識") }
            OutlinedButton(
                onClick = { currentOcr?.text?.takeIf(String::isNotBlank)?.let {
                    clipboard.setPrimaryClip(ClipData.newPlainText("PaperScan OCR", it))
                } },
                enabled = currentOcr != null,
                modifier = Modifier.weight(1f)
            ) { Text("認識文字をコピー") }
        }
        currentOcr?.let { Text("OCR結果: ${it.text.take(160)}") }
        state.ocrError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        OutlinedButton(onClick = onBack, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("ページ一覧へ戻る") }
    }
}

@Composable
private fun PreviewCanvas(
    bitmap: Bitmap,
    corners: CornerPoints?,
    onCornerChanged: (Int, NormalizedPoint) -> Unit
) {
    var activeCorner by remember(bitmap) { mutableIntStateOf(-1) }
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    val latestCorners by rememberUpdatedState(corners)
    val latestOnCornerChanged by rememberUpdatedState(onCornerChanged)
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .semantics { testTag = "page_edit_canvas" }
            .pointerInput(bitmap) {
                detectDragGestures(
                    onDragStart = { position ->
                        val value = latestCorners ?: return@detectDragGestures
                        val bounds = DisplayedImageBounds.fit(
                            size.width.toFloat(), size.height.toFloat(), bitmap.width, bitmap.height
                        )
                        activeCorner = value.points.indices.minBy { index ->
                            (bounds.toOffset(value.points[index]) - position).getDistance()
                        }
                    },
                    onDragEnd = { activeCorner = -1 },
                    onDragCancel = { activeCorner = -1 }
                ) { change, _ ->
                    if (activeCorner >= 0 && latestCorners != null) {
                        val bounds = DisplayedImageBounds.fit(
                            size.width.toFloat(), size.height.toFloat(), bitmap.width, bitmap.height
                        )
                        latestOnCornerChanged(
                            activeCorner,
                            bounds.toNormalized(DisplayPoint(change.position.x, change.position.y))
                        )
                        change.consume()
                    }
                }
            }
    ) {
        val frame = DisplayedImageBounds.fit(size.width, size.height, bitmap.width, bitmap.height)
        drawImage(
            image = image,
            dstOffset = IntOffset(frame.left.toInt(), frame.top.toInt()),
            dstSize = IntSize(frame.width.toInt(), frame.height.toInt())
        )
        corners?.let { value ->
            val points = value.points.map(frame::toOffset)
            points.indices.forEach { index ->
                drawLine(Color(0xFF1565C0), points[index], points[(index + 1) % points.size], strokeWidth = 4f)
                drawCircle(Color(0xFFFFC107), 16f, points[index])
                drawCircle(Color(0xFF5D4037), 16f, points[index], style = androidx.compose.ui.graphics.drawscope.Stroke(3f))
            }
        }
    }
}

private fun DisplayedImageBounds.toOffset(point: NormalizedPoint): Offset =
    toDisplay(point).let { Offset(it.x, it.y) }
