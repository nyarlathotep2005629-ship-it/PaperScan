package com.paperscan.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.paperscan.model.ScanMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class BatchSelectionControlsInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun controls_selectAllAndApplySelectedScanMode() {
        var selectAllCalled = false
        var appliedMode: ScanMode? = null
        composeRule.setContent {
            MaterialTheme {
                BatchSelectionControls(
                    selectedCount = 1,
                    pageCount = 3,
                    busy = false,
                    onSelectAll = { selectAllCalled = true },
                    onClear = {},
                    onApply = { appliedMode = it }
                )
            }
        }

        composeRule.onNodeWithText("全選択").performClick()
        composeRule.onNodeWithText("TEXTを適用").performClick()

        assertTrue(selectAllCalled)
        assertEquals(ScanMode.TEXT, appliedMode)
    }
}
