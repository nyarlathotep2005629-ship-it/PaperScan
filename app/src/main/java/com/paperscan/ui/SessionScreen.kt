package com.paperscan.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import com.paperscan.model.PageItem
import com.paperscan.model.ScanMode
import com.paperscan.model.ScanSession
import com.paperscan.viewmodel.ScanSessionViewModel

@Composable
fun SessionScreen(
    session: ScanSession?,
    busy: Boolean,
    viewModel: ScanSessionViewModel,
    onCameraClick: () -> Unit,
    onImportClick: () -> Unit,
    onCreatePdfClick: () -> Unit,
    onEditPage: (String) -> Unit,
    onHomeClick: () -> Unit
) {
    val pages = session?.pages.orEmpty()
    val pageIds = pages.map(PageItem::id).toSet()
    var selectionMode by remember(session?.id) { mutableStateOf(false) }
    var selectedPageIds by remember(session?.id) { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(pageIds) { selectedPageIds = selectedPageIds.intersect(pageIds) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("ページ一覧", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            OutlinedButton(
                onClick = {
                    selectionMode = !selectionMode
                    selectedPageIds = emptySet()
                },
                enabled = !busy
            ) { Text(if (selectionMode) "選択終了" else "複数選択") }
        }
        Spacer(Modifier.height(8.dp))
        if (selectionMode && pages.isNotEmpty()) {
            BatchSelectionControls(
                selectedCount = selectedPageIds.size,
                pageCount = pages.size,
                busy = busy,
                onSelectAll = { selectedPageIds = pageIds },
                onClear = { selectedPageIds = emptySet() },
                onApply = { mode -> viewModel.applyScanModeToPages(selectedPageIds, mode) }
            )
            Spacer(Modifier.height(8.dp))
        }
        if (pages.isEmpty()) {
            Text("まだページがありません。画像から追加してください。")
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                itemsIndexed(pages, key = { _, page -> page.id }) { index, page ->
                    PageRow(
                        pageNumber = index + 1,
                        page = page,
                        busy = busy,
                        selectionMode = selectionMode,
                        selected = page.id in selectedPageIds,
                        viewModel = viewModel,
                        canMovePrevious = index > 0,
                        canMoveNext = index < pages.lastIndex,
                        onMovePrevious = { viewModel.movePage(index, index - 1) },
                        onMoveNext = { viewModel.movePage(index, index + 1) },
                        onDelete = { viewModel.deletePage(page.id) },
                        onEdit = { onEditPage(page.id) },
                        onToggleSelection = {
                            selectedPageIds = if (page.id in selectedPageIds) {
                                selectedPageIds - page.id
                            } else {
                                selectedPageIds + page.id
                            }
                        }
                    )
                }
            }
        }
        SessionActionButtons(
            pageCount = pages.size,
            busy = busy,
            onCameraClick = onCameraClick,
            onImportClick = onImportClick,
            onCreatePdfClick = onCreatePdfClick,
            onHomeClick = onHomeClick
        )
    }
}

@Composable
internal fun BatchSelectionControls(
    selectedCount: Int,
    pageCount: Int,
    busy: Boolean,
    onSelectAll: () -> Unit,
    onClear: () -> Unit,
    onApply: (ScanMode) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("選択中: $selectedCount / $pageCount ページ")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onSelectAll, enabled = !busy && selectedCount < pageCount, modifier = Modifier.weight(1f)) {
                Text("全選択")
            }
            OutlinedButton(onClick = onClear, enabled = !busy && selectedCount > 0, modifier = Modifier.weight(1f)) {
                Text("選択解除")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ScanMode.entries.forEach { mode ->
                OutlinedButton(
                    onClick = { onApply(mode) },
                    enabled = !busy && selectedCount > 0,
                    modifier = Modifier.weight(1f)
                ) { Text("${mode.name}を適用") }
            }
        }
        Text("一括適用でも手動調整した四隅は保持します。")
    }
}

@Composable
fun SessionActionButtons(
    pageCount: Int,
    busy: Boolean,
    onCameraClick: () -> Unit,
    onImportClick: () -> Unit,
    onCreatePdfClick: () -> Unit,
    onHomeClick: () -> Unit
) {
    Spacer(Modifier.height(8.dp))
    OutlinedButton(
        onClick = onCameraClick,
        enabled = !busy,
        modifier = Modifier.fillMaxWidth()
    ) { Text("撮影して追加") }
    Spacer(Modifier.height(8.dp))
    OutlinedButton(
        onClick = onImportClick,
        enabled = !busy,
        modifier = Modifier.fillMaxWidth()
    ) { Text("画像から追加") }
    Spacer(Modifier.height(8.dp))
    Button(
        onClick = onCreatePdfClick,
        enabled = !busy && pageCount > 0,
        modifier = Modifier.fillMaxWidth().semantics { testTag = "session_create_pdf" }
    ) { Text("PDFを作成") }
    OutlinedButton(onClick = onHomeClick, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
        Text("ホームへ戻る")
    }
}

@Composable
private fun PageRow(
    pageNumber: Int,
    page: PageItem,
    busy: Boolean,
    selectionMode: Boolean,
    selected: Boolean,
    viewModel: ScanSessionViewModel,
    canMovePrevious: Boolean,
    canMoveNext: Boolean,
    onMovePrevious: () -> Unit,
    onMoveNext: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onToggleSelection: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (selectionMode) {
                Checkbox(checked = selected, onCheckedChange = { onToggleSelection() }, enabled = !busy)
            }
            Thumbnail(page, viewModel)
            Column(modifier = Modifier.weight(1f)) {
                Text("ページ $pageNumber")
                Text(page.status.name, style = MaterialTheme.typography.bodySmall)
            }
            if (!selectionMode) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedButton(onClick = onMovePrevious, enabled = !busy && canMovePrevious) { Text("前へ") }
                    OutlinedButton(onClick = onMoveNext, enabled = !busy && canMoveNext) { Text("後ろへ") }
                    OutlinedButton(onClick = onEdit, enabled = !busy) { Text("補正") }
                    OutlinedButton(onClick = onDelete, enabled = !busy) { Text("削除") }
                }
            }
        }
    }
}

@Composable
private fun Thumbnail(page: PageItem, viewModel: ScanSessionViewModel) {
    val bitmap by produceState<Bitmap?>(null, page.id, page.thumbnailRelativePath) {
        value = viewModel.loadThumbnail(page)
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = "ページのサムネイル",
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(96.dp)
        )
    } else {
        Text("読込不可", modifier = Modifier.size(96.dp))
    }
}
