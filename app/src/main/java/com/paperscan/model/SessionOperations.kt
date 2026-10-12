package com.paperscan.model

object SessionOperations {
    fun appendPage(session: ScanSession, page: PageItem, now: Long): ScanSession = session.copy(
        updatedAtEpochMillis = now,
        pages = session.pages + page,
        selectedPageId = session.selectedPageId ?: page.id
    )

    fun movePage(session: ScanSession, fromIndex: Int, toIndex: Int, now: Long): ScanSession? {
        if (fromIndex !in session.pages.indices || toIndex !in session.pages.indices || fromIndex == toIndex) {
            return null
        }
        val updatedPages = session.pages.toMutableList().apply {
            add(toIndex, removeAt(fromIndex))
        }
        return session.copy(updatedAtEpochMillis = now, pages = updatedPages)
    }

    fun deletePage(session: ScanSession, pageId: String, now: Long): ScanSession? {
        val index = session.pages.indexOfFirst { it.id == pageId }
        if (index < 0) return null
        val updatedPages = session.pages.toMutableList().apply { removeAt(index) }
        val nextSelected = when {
            session.selectedPageId != pageId -> session.selectedPageId
            updatedPages.isEmpty() -> null
            index < updatedPages.size -> updatedPages[index].id
            else -> updatedPages.last().id
        }
        return session.copy(
            updatedAtEpochMillis = now,
            pages = updatedPages,
            selectedPageId = nextSelected
        )
    }
}
