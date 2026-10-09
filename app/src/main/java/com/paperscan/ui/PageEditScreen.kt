package com.paperscan.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.paperscan.model.CornerPoints
import com.paperscan.model.NormalizedPoint
import com.paperscan.model.PageItem
import com.paperscan.model.ScanMode
import com.paperscan.viewmodel.PageEditUiState
import kotlin.math.min

@Composable
fun PageEditScreen(
    page: PageItem,
    state: PageEditUiState,
    busy: Boolean,
    onSaveCorners: (CornerPoints) -> Unit,
    onScanModeChanged: (ScanMode) -> Unit,
    onRotate: () -> Unit,
    onRedetect: () -> Unit,
    onBack: () -> Unit
) {
    var corners by remember(page.id, page.corners) { mutableStateOf(page.corners ?: CornerPoints.fullImage()) }
    var showCorrected by remember(page.id) { mutableStateOf(false) }
    val preview = if (showCorrected) state.corrected ?: state.original else state.original
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
                        corners = corners.replace(index, point)
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
                        enabled = !busy,
                        modifier = Modifier.weight(1f).semantics { testTag = "scan_mode_${mode.name}" }
                    ) { Text(mode.name) }
                } else {
                    OutlinedButton(
                        onClick = { onScanModeChanged(mode) },
                        enabled = !busy,
                        modifier = Modifier.weight(1f).semantics { testTag = "scan_mode_${mode.name}" }
                    ) { Text(mode.name) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onRotate, enabled = !busy, modifier = Modifier.weight(1f)) { Text("90°回転") }
            OutlinedButton(onClick = onRedetect, enabled = !busy, modifier = Modifier.weight(1f)) { Text("再検出") }
        }
        Button(
            onClick = { onSaveCorners(corners) },
            enabled = !busy && !showCorrected,
            modifier = Modifier.fillMaxWidth().semantics { testTag = "page_edit_save" }
        ) { Text("四隅を保存") }
        OutlinedButton(onClick = onBack, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("ページ一覧へ戻る") }
    }
}

@Composable
private fun PreviewCanvas(
    bitmap: Bitmap,
    corners: CornerPoints?,
    onCornerChanged: (Int, NormalizedPoint) -> Unit
) {
    var activeCorner by remember(bitmap, corners) { mutableIntStateOf(-1) }
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .semantics { testTag = "page_edit_canvas" }
            .pointerInput(bitmap, corners) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val position = event.changes.firstOrNull()?.position ?: continue
                        val frame = fittedRect(size.width.toFloat(), size.height.toFloat(), bitmap.width, bitmap.height)
                        when {
                            event.changes.any { it.pressed } && activeCorner < 0 && corners != null -> {
                                activeCorner = corners.points.indices.minBy { index ->
                                    val marker = toDisplay(corners.points[index], frame)
                                    (marker - position).getDistance()
                                }
                            }
                            event.changes.any { it.pressed } && activeCorner >= 0 && corners != null -> {
                                onCornerChanged(activeCorner, toNormalized(position, frame))
                            }
                            event.changes.none { it.pressed } -> activeCorner = -1
                        }
                    }
                }
            }
    ) {
        val frame = fittedRect(size.width, size.height, bitmap.width, bitmap.height)
        drawImage(
            image = image,
            dstOffset = IntOffset(frame.left.toInt(), frame.top.toInt()),
            dstSize = IntSize(frame.width.toInt(), frame.height.toInt())
        )
        corners?.let { value ->
            val points = value.points.map { toDisplay(it, frame) }
            points.indices.forEach { index ->
                drawLine(Color(0xFF1565C0), points[index], points[(index + 1) % points.size], strokeWidth = 4f)
                drawCircle(Color(0xFFFFC107), 16f, points[index])
                drawCircle(Color(0xFF5D4037), 16f, points[index], style = androidx.compose.ui.graphics.drawscope.Stroke(3f))
            }
        }
    }
}

private fun fittedRect(containerWidth: Float, containerHeight: Float, imageWidth: Int, imageHeight: Int): Rect {
    val scale = min(containerWidth / imageWidth, containerHeight / imageHeight)
    val width = imageWidth * scale
    val height = imageHeight * scale
    return Rect((containerWidth - width) / 2f, (containerHeight - height) / 2f, (containerWidth + width) / 2f, (containerHeight + height) / 2f)
}

private fun toDisplay(point: NormalizedPoint, frame: Rect) = Offset(
    frame.left + point.x * frame.width,
    frame.top + point.y * frame.height
)

private fun toNormalized(point: Offset, frame: Rect) = NormalizedPoint(
    ((point.x - frame.left) / frame.width).coerceIn(0f, 1f),
    ((point.y - frame.top) / frame.height).coerceIn(0f, 1f)
)

private fun CornerPoints.replace(index: Int, point: NormalizedPoint): CornerPoints = when (index) {
    0 -> copy(topLeft = point)
    1 -> copy(topRight = point)
    2 -> copy(bottomRight = point)
    else -> copy(bottomLeft = point)
}
