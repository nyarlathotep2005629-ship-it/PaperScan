package com.paperscan.ui

import android.graphics.Bitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.paperscan.model.CornerEditDraft
import com.paperscan.model.CornerPoints
import com.paperscan.model.PageItem
import com.paperscan.model.PageStatus
import com.paperscan.model.ScanMode
import com.paperscan.model.SourceType
import com.paperscan.viewmodel.PageEditUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import kotlin.math.min

class PageEditScreenInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun dragHandle_thenSave_commitsDisplayedDraft() {
        val bitmap = Bitmap.createBitmap(600, 600, Bitmap.Config.ARGB_8888)
        val draft = mutableStateOf(CornerEditDraft.fromSaved(CornerPoints.fullImage()))
        var saved: CornerPoints? = null
        val page = PageItem(
            id = "manual-corner",
            sourceType = SourceType.IMPORT,
            sourceUri = "content://fixture/manual-corner",
            workingRelativePath = null,
            thumbnailRelativePath = null,
            sourceExifRotationDegrees = 0,
            sourceExifFlipped = false,
            rotationQuarterTurns = 0,
            scanMode = ScanMode.AUTO,
            status = PageStatus.READY,
            corners = CornerPoints.fullImage()
        )
        composeRule.setContent {
            MaterialTheme {
                PageEditScreen(
                    page = page,
                    state = PageEditUiState(
                        pageId = page.id,
                        original = bitmap,
                        corrected = bitmap,
                        cornerDraft = draft.value
                    ),
                    busy = false,
                    onCornerChanged = { index, point -> draft.value = draft.value.moveCorner(index, point) },
                    onSaveCorners = { saved = draft.value.cornersToCommit() },
                    onScanModeChanged = {},
                    onRotate = {},
                    onRedetect = {},
                    onRecognize = {},
                    onBack = {}
                )
            }
        }

        val canvas = composeRule.onNodeWithTag("page_edit_canvas")
        val canvasBounds = canvas.fetchSemanticsNode().boundsInRoot
        canvas.performTouchInput {
            val side = min(canvasBounds.width, canvasBounds.height)
            val left = (canvasBounds.width - side) / 2f
            val top = (canvasBounds.height - side) / 2f
            down(Offset(left + 2f, top + 2f))
            moveTo(Offset(left + side * 0.20f, top + side * 0.18f), 400L)
            up()
        }
        composeRule.onNodeWithTag("page_edit_save").performClick()

        assertNotNull(saved)
        val committed = requireNotNull(saved)
        assertEquals(0.20f, committed.topLeft.x, 0.04f)
        assertEquals(0.18f, committed.topLeft.y, 0.04f)
    }
}
