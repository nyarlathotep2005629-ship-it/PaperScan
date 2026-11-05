package com.paperscan.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class SessionOperationsTest {
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
}
