package com.paperscan.pdf

import java.time.LocalDate

object PdfFileName {
    fun suggested(date: LocalDate = LocalDate.now()): String = "PaperScan_$date.pdf"
}
