package com.paperscan.model

data class ScanSession(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val id: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val pages: List<PageItem>,
    val selectedPageId: String?
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}
