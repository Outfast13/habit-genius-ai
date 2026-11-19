package com.app.ui

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.app.journal.JournalAttachment
import com.app.journal.JournalEntry
import com.app.journal.JournalState
import com.app.journal.JournalStorage
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

private val MOOD_OPTIONS = listOf("Focused", "Calm", "Neutral", "Tired", "Stressed")

@Composable
fun JournalScreen(
    initialState: JournalState,
    storage: JournalStorage,
    onStateChanged: (JournalState) -> Unit
) {
    val context = LocalContext.current
    var state by remember(initialState) { mutableStateOf(initialState) }
    var titleInput by remember { mutableStateOf("") }
    var bodyInput by remember { mutableStateOf("") }
    var selectedMood by remember { mutableStateOf("Neutral") }
    var pendingAttachments by remember { mutableStateOf<List<JournalAttachment>>(emptyList()) }
    var attachError by remember { mutableStateOf<String?>(null) }

    val bgDark = Color(0xFF0E110F)
    val cardDark = Color(0xFF171C18)
    val rowDark = Color(0xFF111512)
    val accentGreen = Color(0xFF7FD18B)
    val bannerGreen = Color(0xFF28422E)

    fun commit(newState: JournalState) {
        state = newState
        onStateChanged(newState)
    }

    fun copyUriToJournalStorage(uri: Uri) {
        attachError = null
        val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
        var displayName = uri.lastPathSegment ?: "attachment"
        var size = 0L

        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIdx >= 0) {
                    val qName = cursor.getString(nameIdx)
                    if (!qName.isNullOrBlank()) displayName = qName
                }
                if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                    size = cursor.getLong(sizeIdx)
                }
            }
        }

        val safeName = displayName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val attId = UUID.randomUUID().toString()
        val destFile = File(storage.getMediaDirectory(), "${attId}_$safeName")

        val copied = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    val buffer = ByteArray(8192)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        total += read
                    }
                    if (size <= 0L) size = total
                }
            } ?: throw IllegalStateException("Cannot read selected file.")
        }

        copied.onSuccess {
            pendingAttachments = pendingAttachments + JournalAttachment(
                id = attId,
                displayName = displayName,
                mimeType = mime,
                localPath = destFile.absolutePath,
                sizeBytes = size
            )
        }.onFailure { err ->
            attachError = err.message ?: "Failed to attach file."
        }
    }

    val mediaPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            copyUriToJournalStorage(uri)
        }
    }

    val dateFormatter = remember {
        SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgDark)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Journal",
            color = Color.White,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(bannerGreen, RoundedCornerShape(14.dp))
                .padding(14.dp)
        ) {
            Column {
                Text(
                    text = "${state.entries.size} entries saved locally",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Text, mood tags, and photo/audio/video/file attachments",
                    color = Color(0xFFB7D4BC),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        // New Entry Composer
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(cardDark, RoundedCornerShape(16.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "New Entry",
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )

            OutlinedTextField(
                value = titleInput,
                onValueChange = { titleInput = it },
                label = { Text("Title (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = bodyInput,
                onValueChange = { bodyInput = it },
                label = { Text("Write your thoughts...") },
                singleLine = false,
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )

            Text("Mood", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MOOD_OPTIONS.forEach { mood ->
                    val active = mood == selectedMood
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                if (active) bannerGreen else rowDark,
                                RoundedCornerShape(8.dp)
                            )
                            .border(
                                1.dp,
                                if (active) accentGreen else Color(0xFF2B332C),
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { selectedMood = mood }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = mood,
                            color = if (active) accentGreen else Color.LightGray,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { mediaPicker.launch(arrayOf("image/*")) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Photo")
                }
                Button(
                    onClick = { mediaPicker.launch(arrayOf("audio/*")) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Audio")
                }
                Button(
                    onClick = { mediaPicker.launch(arrayOf("video/*")) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Video")
                }
                Button(
                    onClick = { mediaPicker.launch(arrayOf("*/*")) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("File")
                }
            }

            pendingAttachments.forEach { att ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(rowDark, RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = att.displayName,
                            color = accentGreen,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "${att.mimeType} • ${att.sizeBytes} B",
                            color = Color(0xFF9FB0A3),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    Text(
                        text = "✕",
                        color = Color(0xFFE57373),
                        modifier = Modifier
                            .clickable {
                                File(att.localPath).delete()
                                pendingAttachments = pendingAttachments.filterNot { it.id == att.id }
                            }
                            .padding(horizontal = 6.dp)
                    )
                }
            }

            attachError?.let { err ->
                Text(
                    text = err,
                    color = Color(0xFFE57373),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Button(
                onClick = {
                    val cleanTitle = titleInput.trim()
                    val cleanBody = bodyInput.trim()
                    if (cleanTitle.isNotEmpty() || cleanBody.isNotEmpty() || pendingAttachments.isNotEmpty()) {
                        val entry = JournalEntry(
                            id = UUID.randomUUID().toString(),
                            title = cleanTitle.ifEmpty { dateFormatter.format(Date()) },
                            body = cleanBody,
                            mood = selectedMood,
                            timestampMs = System.currentTimeMillis(),
                            attachments = pendingAttachments
                        )
                        commit(state.copy(entries = listOf(entry) + state.entries))
                        titleInput = ""
                        bodyInput = ""
                        pendingAttachments = emptyList()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = accentGreen),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Entry", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }

        // Saved Entries
        if (state.entries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(cardDark, RoundedCornerShape(14.dp))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("No journal entries yet.", color = Color.Gray)
            }
        } else {
            state.entries.forEach { entry ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(cardDark, RoundedCornerShape(16.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = entry.title,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "${dateFormatter.format(Date(entry.timestampMs))} • ${entry.mood}",
                                color = accentGreen,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        Text(
                            text = "✕",
                            color = Color(0xFFE57373),
                            modifier = Modifier
                                .clickable {
                                    entry.attachments.forEach { File(it.localPath).delete() }
                                    commit(state.copy(entries = state.entries.filterNot { it.id == entry.id }))
                                }
                                .padding(horizontal = 6.dp)
                        )
                    }

                    if (entry.body.isNotBlank()) {
                        Text(
                            text = entry.body,
                            color = Color(0xFFDCE6DE),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    if (entry.attachments.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            entry.attachments.forEach { att ->
                                Text(
                                    text = "Attachment: ${att.displayName} (${att.mimeType})",
                                    color = Color(0xFF9FB0A3),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
