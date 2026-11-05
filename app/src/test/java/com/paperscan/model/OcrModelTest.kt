package com.paperscan.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrModelTest {
    @Test
    fun revision_TC_OCR_001_isStableForIdenticalPageInputs() {
        val first = page().ocrRevision()
        val second = page().ocrRevision()

        assertEquals(first, second)
        assertEquals(64, first.length)
        assertTrue(first.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun revision_TC_OCR_002_changesWhenUserRotationChanges() {
        assertNotEquals(page().ocrRevision(), page().copy(rotationQuarterTurns = 1).ocrRevision())
    }

    @Test
    fun revision_TC_OCR_003_changesWhenExifOrientationChanges() {
        assertNotEquals(page().ocrRevision(), page().copy(sourceExifRotationDegrees = 90).ocrRevision())
    }

    @Test
    fun revision_TC_OCR_004_changesWhenExifMirrorChanges() {
        assertNotEquals(page().ocrRevision(), page().copy(sourceExifFlipped = true).ocrRevision())
    }

    @Test
    fun revision_TC_OCR_005_changesWhenProcessingModeChanges() {
        assertNotEquals(page().ocrRevision(), page().copy(scanMode = ScanMode.TEXT).ocrRevision())
    }

    @Test
    fun revision_TC_OCR_006_changesWhenManualCornersChange() {
        val changed = page().copy(corners = CornerPoints(
            NormalizedPoint(0.12f, 0.1f), NormalizedPoint(0.9f, 0.1f),
            NormalizedPoint(0.9f, 0.9f), NormalizedPoint(0.1f, 0.9f)
        ))

        assertNotEquals(page().ocrRevision(), changed.ocrRevision())
    }

    @Test
    fun revision_TC_OCR_007_changesWhenSourcePathChanges() {
        assertNotEquals(
            page().ocrRevision(),
            page().copy(workingRelativePath = "imports/changed.jpg").ocrRevision()
        )
    }

    @Test
    fun revision_TC_OCR_008_changesWhenPageIdentityChanges() {
        assertNotEquals(page().ocrRevision(), page().copy(id = "page-2").ocrRevision())
    }

    @Test
    fun revision_TC_OCR_009_changesWhenManualLanguageSelectionChanges() {
        assertNotEquals(
            page().ocrRevision(),
            page().copy(ocrLanguage = OcrLanguage.JAPANESE).ocrRevision()
        )
    }

    @Test
    fun textResult_TC_OCR_010_joinsLinesInStoredReadingOrder() {
        val result = PageOcrResult(
            revision = "revision",
            status = OcrStatus.COMPLETE,
            lines = listOf(
                OcrTextLine("First", NormalizedTextBounds(0.1f, 0.1f, 0.5f, 0.2f)),
                OcrTextLine("Second", NormalizedTextBounds(0.1f, 0.3f, 0.5f, 0.4f))
            ),
            updatedAtEpochMillis = 1
        )

        assertEquals("First\nSecond", result.text)
    }

    @Test
    fun textBounds_TC_OCR_011_rejectsReversedHorizontalRange() {
        expectIllegalArgument { NormalizedTextBounds(0.7f, 0.1f, 0.3f, 0.2f) }
    }

    @Test
    fun textBounds_TC_OCR_012_rejectsOutOfRangeCoordinates() {
        expectIllegalArgument { NormalizedTextBounds(-0.01f, 0.1f, 0.3f, 0.2f) }
    }

    @Test
    fun textBounds_TC_OCR_013_rejectsNonFiniteCoordinates() {
        expectIllegalArgument { NormalizedTextBounds(0f, 0f, Float.POSITIVE_INFINITY, 1f) }
    }

    @Test
    fun textLine_TC_OCR_014_rejectsBlankRecognizedText() {
        expectIllegalArgument {
            OcrTextLine("  ", NormalizedTextBounds(0f, 0f, 1f, 1f))
        }
    }

    private fun page() = PageItem(
        id = "page-1",
        sourceType = SourceType.IMPORT,
        sourceUri = "content://fixture/1",
        workingRelativePath = "imports/page-1.jpg",
        thumbnailRelativePath = null,
        sourceExifRotationDegrees = 0,
        sourceExifFlipped = false,
        rotationQuarterTurns = 0,
        scanMode = ScanMode.AUTO,
        status = PageStatus.READY
    )

    private fun expectIllegalArgument(block: () -> Unit) {
        try {
            block()
            throw AssertionError("invalid OCR data must be rejected")
        } catch (_: IllegalArgumentException) { }
    }
}
