package com.app.journal

data class JournalAttachment(
    val id: String,
    val displayName: String,
    val mimeType: String,
    val localPath: String,
    val sizeBytes: Long = 0L
)

data class JournalEntry(
    val id: String,
    val title: String,
    val body: String,
    val mood: String = "Neutral",
    val timestampMs: Long,
    val attachments: List<JournalAttachment> = emptyList()
)

data class JournalState(
    val entries: List<JournalEntry> = emptyList()
)
