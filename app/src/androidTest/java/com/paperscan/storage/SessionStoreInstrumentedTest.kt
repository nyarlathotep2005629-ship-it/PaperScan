package com.paperscan.storage

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.paperscan.model.PageItem
import com.paperscan.model.PageStatus
import com.paperscan.model.ScanMode
import com.paperscan.model.ScanSession
import com.paperscan.model.SourceType
import com.paperscan.model.CornerPoints
import com.paperscan.model.DetectionConfidence
import com.paperscan.model.NormalizedPoint
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SessionStoreInstrumentedTest {
    @Test
    fun saveAndLoad_roundTrip_preservesExifAndPageOrder() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val files = WorkingFileStore(context)
        files.deleteSessionFiles()
        val store = SessionStore(files)
        val session = ScanSession(
            id = "test-session",
            createdAtEpochMillis = 1,
            updatedAtEpochMillis = 2,
            pages = listOf(
                page("first", 90, true, ScanMode.TEXT),
                cameraPage("second", 0, false, ScanMode.COLOR)
            ),
            selectedPageId = "first"
        )

        assertTrue(store.saveAtomic(session).isSuccess)
        val loaded = store.load() as SessionStore.LoadResult.Loaded

        assertEquals(listOf("first", "second"), loaded.session.pages.map(PageItem::id))
        assertEquals(90, loaded.session.pages.first().sourceExifRotationDegrees)
        assertTrue(loaded.session.pages.first().sourceExifFlipped)
        assertEquals(DetectionConfidence.HIGH, loaded.session.pages.first().detectionConfidence)
        assertEquals(NormalizedPoint(0.1f, 0.2f), loaded.session.pages.first().corners?.topLeft)
        assertEquals(ScanMode.TEXT, loaded.session.pages.first().scanMode)
        assertEquals(ScanMode.COLOR, loaded.session.pages.last().scanMode)
        assertEquals(SourceType.CAMERA, loaded.session.pages.last().sourceType)
        assertEquals("captures/second.jpg", loaded.session.pages.last().workingRelativePath)
        assertEquals(null, loaded.session.pages.last().sourceUri)
        files.deleteSessionFiles()
    }

    private fun page(id: String, rotation: Int, flipped: Boolean, mode: ScanMode) = PageItem(
        id = id,
        sourceType = SourceType.IMPORT,
        sourceUri = "content://fixture/$id",
        workingRelativePath = null,
        thumbnailRelativePath = "thumbnails/$id.jpg",
        sourceExifRotationDegrees = rotation,
        sourceExifFlipped = flipped,
        rotationQuarterTurns = 0,
        scanMode = mode,
        status = PageStatus.READY,
        corners = CornerPoints(
            NormalizedPoint(0.1f, 0.2f), NormalizedPoint(0.9f, 0.2f),
            NormalizedPoint(0.9f, 0.8f), NormalizedPoint(0.1f, 0.8f)
        ),
        detectionConfidence = DetectionConfidence.HIGH
    )

    private fun cameraPage(id: String, rotation: Int, flipped: Boolean, mode: ScanMode) = PageItem(
        id = id,
        sourceType = SourceType.CAMERA,
        sourceUri = null,
        workingRelativePath = "captures/$id.jpg",
        thumbnailRelativePath = "thumbnails/$id.jpg",
        sourceExifRotationDegrees = rotation,
        sourceExifFlipped = flipped,
        rotationQuarterTurns = 0,
        scanMode = mode,
        status = PageStatus.NEEDS_REVIEW,
        corners = CornerPoints(
            NormalizedPoint(0.1f, 0.2f), NormalizedPoint(0.9f, 0.2f),
            NormalizedPoint(0.9f, 0.8f), NormalizedPoint(0.1f, 0.8f)
        ),
        detectionConfidence = DetectionConfidence.MEDIUM
    )
}
