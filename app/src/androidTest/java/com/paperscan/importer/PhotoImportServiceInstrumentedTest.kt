package com.paperscan.importer

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.paperscan.image.SourceImageLoader
import com.paperscan.storage.WorkingFileStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PhotoImportServiceInstrumentedTest {
    @Test
    fun importUris_countsInputAndExistingDuplicates_andDoesNotModifyOriginalBytes() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val files = WorkingFileStore(context)
        files.deleteSessionFiles()
        files.cleanupStaleCache()

        val source = File(context.noBackupFilesDir, "paperscan-import-source.png")
        InstrumentationRegistry.getInstrumentation().context.assets
            .open("fixtures/overhead_a4.png")
            .use { input -> source.outputStream().use(input::copyTo) }
        val before = source.readBytes()

        val service = PhotoImportService(context, files, SourceImageLoader(context, files))
        val uri = Uri.fromFile(source)
        val inputDuplicateResult = service.importUris(listOf(uri, uri), emptySet())

        assertEquals(1, inputDuplicateResult.pages.size)
        assertEquals(1, inputDuplicateResult.duplicateCount)
        assertEquals(0, inputDuplicateResult.failures)
        assertEquals(uri.toString(), inputDuplicateResult.pages.single().sourceUri)
        assertTrue(inputDuplicateResult.pages.single().workingRelativePath != null)
        assertTrue(source.readBytes().contentEquals(before))

        val existingDuplicateResult = service.importUris(
            uris = listOf(uri),
            existingUris = setOf(uri.toString())
        )
        assertTrue(existingDuplicateResult.pages.isEmpty())
        assertEquals(1, existingDuplicateResult.duplicateCount)
        assertEquals(0, existingDuplicateResult.failures)
        assertTrue(source.readBytes().contentEquals(before))

        files.deleteSessionFiles()
        files.cleanupStaleCache()
        source.delete()
    }
}
