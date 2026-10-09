package com.paperscan.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HomeScreenInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun cameraButton_invokesCameraNavigation() {
        var requested = false
        composeRule.setContent {
            HomeScreen(
                recoveryAvailable = false,
                onCameraClick = { requested = true },
                onImportClick = {},
                onResumeClick = {},
                onDiscardClick = {}
            )
        }

        composeRule.onNodeWithText("撮影する").performClick()
        assertTrue(requested)
    }
}
