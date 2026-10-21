package com.paperscan.storage

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SessionStoreRecoveryInstrumentedTest {
    @Test
    fun load_corruptedSession_returnsCorruptedWithoutThrowing() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val files = WorkingFileStore(context)
        files.deleteSessionFiles()
        files.sessionFile.parentFile?.mkdirs()
        files.sessionFile.writeText("{ not valid session json", Charsets.UTF_8)

        val result = SessionStore(files).load()

        assertTrue(result is SessionStore.LoadResult.Corrupted)
        files.deleteSessionFiles()
    }
}
