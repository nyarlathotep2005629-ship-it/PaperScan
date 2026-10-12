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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import com.paperscan.model.PageItem
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
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("ページ一覧", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        val pages = session?.pages.orEmpty()
        if (pages.isEmpty()) {
            Text("まだページがありません。画像から追加してください。")
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                itemsIndexed(pages, key = { _, page -> page.id }) { index, page ->
                    PageRow(
                        pageNumber = index + 1,
                        page = page,
                        busy = busy,
                        viewModel = viewModel,
                        canMovePrevious = index > 0,
                        canMoveNext = index < pages.lastIndex,
                        onMovePrevious = { viewModel.movePage(index, index - 1) },
                        onMoveNext = { viewModel.movePage(index, index + 1) },
                        onDelete = { viewModel.deletePage(page.id) },
                        onEdit = { onEditPage(page.id) }
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
    viewModel: ScanSessionViewModel,
    canMovePrevious: Boolean,
    canMoveNext: Boolean,
    onMovePrevious: () -> Unit,
    onMoveNext: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Thumbnail(page, viewModel)
            Column(modifier = Modifier.weight(1f)) {
                Text("ページ $pageNumber")
                Text(page.status.name, style = MaterialTheme.typography.bodySmall)
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedButton(onClick = onMovePrevious, enabled = !busy && canMovePrevious) { Text("前へ") }
                OutlinedButton(onClick = onMoveNext, enabled = !busy && canMoveNext) { Text("後ろへ") }
                OutlinedButton(onClick = onEdit, enabled = !busy) { Text("補正") }
                OutlinedButton(onClick = onDelete, enabled = !busy) { Text("削除") }
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
