package com.paperscan.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class SessionOperationsTest {
    @Test
    fun applyScanMode_TC_SESSION_008_multiPageSelectionChangesOnlyRequestedPagesInPlace() {
        val original = session("A", "B", "C")
        val updated = requireNotNull(SessionOperations.applyScanMode(original, setOf("A", "C"), ScanMode.COLOR, now = 20))

        assertEquals(listOf("A", "B", "C"), updated.pages.map(PageItem::id))
        assertEquals(listOf(ScanMode.COLOR, ScanMode.AUTO, ScanMode.COLOR), updated.pages.map(PageItem::scanMode))
        assertEquals(original.pages[1], updated.pages[1])
    }

    @Test
    fun applyScanMode_TC_SESSION_009_unknownOnlySelectionDoesNotChangeSession() {
        val original = session("A", "B")

        assertNull(SessionOperations.applyScanMode(original, setOf("unknown"), ScanMode.TEXT, now = 21))
        assertEquals(listOf(ScanMode.AUTO, ScanMode.AUTO), original.pages.map(PageItem::scanMode))
    }

    @Test
    fun applyScanMode_TC_SESSION_010_emptySelectionDoesNotChangeSession() {
        val original = session("A")

        assertNull(SessionOperations.applyScanMode(original, emptySet(), ScanMode.TEXT, now = 22))
        assertEquals(0, original.updatedAtEpochMillis)
    }

    @Test
    fun movePage_TC_SESSION_011_firstPageCanMoveToLastPosition() {
        val original = session("A", "B", "C")

        val updated = requireNotNull(SessionOperations.movePage(original, 0, 2, now = 23))

        assertEquals(listOf("B", "C", "A"), updated.pages.map(PageItem::id))
        assertEquals(23, updated.updatedAtEpochMillis)
    }

    @Test
    fun movePage_TC_SESSION_012_negativeSourceIndexIsRejected() {
        assertNull(SessionOperations.movePage(session("A", "B"), -1, 0, now = 24))
    }

    @Test
    fun deletePage_TC_SESSION_013_deletingSelectedFirstPageSelectsFollowingPage() {
        val original = session("A", "B", "C", selected = "A")

        val updated = requireNotNull(SessionOperations.deletePage(original, "A", now = 25))

        assertEquals(listOf("B", "C"), updated.pages.map(PageItem::id))
        assertEquals("B", updated.selectedPageId)
    }

    @Test
    fun appendPage_TC_SESSION_014_existingSelectionIsRetainedWhenAppending() {
        val original = session("A", "B", selected = "B")
        val updated = SessionOperations.appendPage(original, originalPage("C"), now = 26)

        assertEquals(listOf("A", "B", "C"), updated.pages.map(PageItem::id))
        assertEquals("B", updated.selectedPageId)
        assertEquals(listOf("A", "B"), original.pages.map(PageItem::id))
    }

    @Test
    fun appendPage_TC_SESSION_001_emptySessionSelectsFirstPageAndUpdatesTimestamp() {
        val original = session(selected = null)
        val page = originalPage("new")

        val updated = SessionOperations.appendPage(original, page, now = 42)

        assertEquals(listOf("new"), updated.pages.map(PageItem::id))
        assertEquals("new", updated.selectedPageId)
        assertEquals(42, updated.updatedAtEpochMillis)
        assertEquals(emptyList<String>(), original.pages.map(PageItem::id))
    }

    @Test
    fun deletePage_TC_SESSION_002_deletingUnselectedPagePreservesSelection() {
        val original = session("A", "B", "C", selected = "B")

        val updated = requireNotNull(SessionOperations.deletePage(original, "A", now = 8))

        assertEquals(listOf("B", "C"), updated.pages.map(PageItem::id))
        assertEquals("B", updated.selectedPageId)
    }

    @Test
    fun deletePage_TC_SESSION_003_deletingSelectedLastPageSelectsPreviousPage() {
        val original = session("A", "B", "C", selected = "C")

        val updated = requireNotNull(SessionOperations.deletePage(original, "C", now = 9))

        assertEquals(listOf("A", "B"), updated.pages.map(PageItem::id))
        assertEquals("B", updated.selectedPageId)
    }

    @Test
    fun deletePage_TC_SESSION_004_unknownPageIsNoOpAndLeavesOriginalUntouched() {
        val original = session("A", "B", selected = "B")

        assertNull(SessionOperations.deletePage(original, "missing", now = 10))
        assertEquals(listOf("A", "B"), original.pages.map(PageItem::id))
        assertEquals(0, original.updatedAtEpochMillis)
    }

    @Test
    fun movePage_TC_SESSION_005_outOfRangeDestinationIsNoOp() {
        val original = session("A", "B")

        assertNull(SessionOperations.movePage(original, 0, 2, now = 11))
        assertEquals(listOf("A", "B"), original.pages.map(PageItem::id))
    }

    @Test
    fun movePage_TC_SESSION_006_sameIndexDoesNotCreateSessionUpdate() {
        val original = session("A", "B")

        assertNull(SessionOperations.movePage(original, 1, 1, now = 12))
        assertEquals(0, original.updatedAtEpochMillis)
    }

    @Test
    fun movePage_TC_SESSION_007_reorderingPreservesSelectedPageIdentity() {
        val original = session("A", "B", "C", selected = "B")

        val updated = requireNotNull(SessionOperations.movePage(original, 1, 0, now = 13))

        assertEquals(listOf("B", "A", "C"), updated.pages.map(PageItem::id))
        assertEquals("B", updated.selectedPageId)
    }

    @Test
    fun appendPage_cameraPage_keepsExistingPagesAndAppendsAtEnd() {
        val session = session("A", "B", selected = "A")
        val cameraPage = PageItem(
            id = "C",
            sourceType = SourceType.CAMERA,
            sourceUri = null,
            workingRelativePath = "captures/C.jpg",
            thumbnailRelativePath = "thumbnails/C.jpg",
            sourceExifRotationDegrees = 90,
            sourceExifFlipped = false,
            rotationQuarterTurns = 0,
            scanMode = ScanMode.AUTO,
            status = PageStatus.NEEDS_REVIEW
        )

        val actual = SessionOperations.appendPage(session, cameraPage, 10)

        assertEquals(listOf("A", "B", "C"), actual.pages.map(PageItem::id))
        assertEquals(SourceType.CAMERA, actual.pages.last().sourceType)
        assertEquals("captures/C.jpg", actual.pages.last().workingRelativePath)
        assertEquals("A", actual.selectedPageId)
    }

    @Test
    fun movePage_lastPagePrevious_preservesExpectedOrder() {
        val session = session("A", "B", "C")

        val actual = requireNotNull(SessionOperations.movePage(session, 2, 1, 10))

        assertEquals(listOf("A", "C", "B"), actual.pages.map(PageItem::id))
    }

    @Test
    fun movePage_firstPagePrevious_returnsNullWithoutChangingOrder() {
        val session = session("A", "B")

        val actual = SessionOperations.movePage(session, 0, -1, 10)

        assertNull(actual)
        assertEquals(listOf("A", "B"), session.pages.map(PageItem::id))
    }

    @Test
    fun deletePage_selectedMiddlePage_selectsFollowingPage() {
        val session = session("A", "B", "C", selected = "B")

        val actual = requireNotNull(SessionOperations.deletePage(session, "B", 10))

        assertEquals(listOf("A", "C"), actual.pages.map(PageItem::id))
        assertEquals("C", actual.selectedPageId)
    }

    @Test
    fun deletePage_lastPage_clearsSelectedPage() {
        val session = session("A", selected = "A")

        val actual = requireNotNull(SessionOperations.deletePage(session, "A", 10))

        assertEquals(emptyList<String>(), actual.pages.map(PageItem::id))
        assertNull(actual.selectedPageId)
    }

    @Test
    fun applyScanMode_selectedPagesKeepsCornersAndInvalidatesOnlyChangedOcr() {
        val first = session("A", "B").pages[0].copy(
            scanMode = ScanMode.AUTO,
            corners = CornerPoints.fromUnordered(
                listOf(
                    NormalizedPoint(0.1f, 0.1f),
                    NormalizedPoint(0.9f, 0.1f),
                    NormalizedPoint(0.9f, 0.9f),
                    NormalizedPoint(0.1f, 0.9f)
                )
            ),
            ocrResult = PageOcrResult("rev-a", OcrStatus.COMPLETE, emptyList(), updatedAtEpochMillis = 1)
        )
        val second = session("A", "B").pages[1]
        val original = session("A", "B").copy(pages = listOf(first, second))

        val actual = requireNotNull(SessionOperations.applyScanMode(original, setOf("A", "unknown"), ScanMode.TEXT, 10))

        assertEquals(listOf("A", "B"), actual.pages.map(PageItem::id))
        assertEquals(ScanMode.TEXT, actual.pages[0].scanMode)
        assertNotNull(actual.pages[0].corners)
        assertNull(actual.pages[0].ocrResult)
        assertEquals(second, actual.pages[1])
    }

    @Test
    fun applyScanMode_returnsNullWhenNothingChanges() {
        val original = session("A")

        assertNull(SessionOperations.applyScanMode(original, setOf("A"), ScanMode.AUTO, 10))
        assertEquals(0, original.updatedAtEpochMillis)
    }

    private fun session(vararg ids: String, selected: String? = ids.firstOrNull()) = ScanSession(
        id = "session",
        createdAtEpochMillis = 0,
        updatedAtEpochMillis = 0,
        pages = ids.map { id ->
            PageItem(
                id = id,
                sourceType = SourceType.IMPORT,
                sourceUri = "content://fixture/$id",
                workingRelativePath = null,
                thumbnailRelativePath = null,
                sourceExifRotationDegrees = 0,
                sourceExifFlipped = false,
                rotationQuarterTurns = 0,
                scanMode = ScanMode.AUTO,
                status = PageStatus.READY
            )
        },
        selectedPageId = selected
    )

    private fun originalPage(id: String) = PageItem(
        id = id,
        sourceType = SourceType.CAMERA,
        sourceUri = null,
        workingRelativePath = "captures/$id.jpg",
        thumbnailRelativePath = null,
        sourceExifRotationDegrees = 0,
        sourceExifFlipped = false,
        rotationQuarterTurns = 0,
        scanMode = ScanMode.AUTO,
        status = PageStatus.NEEDS_REVIEW
    )
}
