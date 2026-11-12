package com.paperscan.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.paperscan.pdf.PdfExportSettings
import com.paperscan.pdf.PdfQuality
import com.paperscan.viewmodel.ExportUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ExportScreenInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun exportScreen_changesQualityAndSearchableDefaultBeforeGenerating() {
        var searchable by mutableStateOf(true)
        var settings by mutableStateOf(PdfExportSettings())
        var generated = false
        composeRule.setContent {
            MaterialTheme {
                ExportScreen(
                    pageCount = 2,
                    state = ExportUiState.Idle,
                    searchable = searchable,
                    settings = settings,
                    onSearchableChange = { searchable = it },
                    onSettingsChange = { settings = it },
                    onGenerate = { generated = true },
                    onChangeSettings = {},
                    onSaveClick = {},
                    onBackClick = {}
                )
            }
        }

        composeRule.onNodeWithText("画質: 標準").performClick()
        composeRule.onNodeWithText("高画質").performClick()
        composeRule.onNode(isToggleable()).performClick()
        composeRule.onNodeWithText("画像PDFを作成").performScrollTo().performClick()

        assertEquals(PdfQuality.HIGH, settings.quality)
        assertFalse(searchable)
        assertTrue(generated)
    }
}
