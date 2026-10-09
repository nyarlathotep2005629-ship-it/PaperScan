package com.paperscan.storage

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkingFileStoreInstrumentedTest {
    @Test
    fun captureFile_isStoredInPrivateSessionDirectory_andRemovedOnSessionCleanup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val files = WorkingFileStore(context)
        files.deleteSessionFiles()

        val capture = files.captureFile("camera-fixture")
        capture.writeBytes(byteArrayOf(1, 2, 3))

        assertTrue(capture.canonicalPath.startsWith(context.filesDir.canonicalPath))
        assertTrue(capture.canonicalPath.contains("scan_session${java.io.File.separator}captures"))
        assertTrue(capture.exists())

        files.deleteSessionFiles()

        assertFalse(capture.exists())
    }
}
