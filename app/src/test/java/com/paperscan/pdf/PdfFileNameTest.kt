package com.paperscan.pdf

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class PdfFileNameTest {
    @Test
    fun suggested_fixedDate_returnsSafePdfFileName() {
        assertEquals("PaperScan_2026-10-07.pdf", PdfFileName.suggested(LocalDate.of(2026, 10, 7)))
    }
}
