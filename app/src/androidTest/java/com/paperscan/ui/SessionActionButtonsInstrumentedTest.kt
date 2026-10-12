package com.paperscan.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SessionActionButtonsInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun captureButton_requestsCameraWithoutChangingExistingPageCount() {
        var cameraRequested = false
        val existingPageCount = 3

        composeRule.setContent {
            MaterialTheme {
                Column {
                    SessionActionButtons(
                        pageCount = existingPageCount,
                        busy = false,
                        onCameraClick = { cameraRequested = true },
                        onImportClick = {},
                        onCreatePdfClick = {},
                        onHomeClick = {}
                    )
                }
            }
        }

        composeRule.onNodeWithText("撮影して追加").performClick()

        assertTrue(cameraRequested)
        assertEquals(3, existingPageCount)
    }
}
