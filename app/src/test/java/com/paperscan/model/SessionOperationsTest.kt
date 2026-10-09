package com.paperscan.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionOperationsTest {
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
