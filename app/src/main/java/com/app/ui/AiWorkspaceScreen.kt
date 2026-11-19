package com.app.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import com.app.ai.AiCompletionResult
import com.app.ai.AiFailoverClient
import com.app.ai.AiSettingsState
import com.app.ai.AttachedMediaPayload
import com.app.ai.ProposedTrackerItem
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

@Composable
fun AiWorkspaceScreen(
    aiState: AiSettingsState,
    onApproveProposedItem: (ProposedTrackerItem) -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val client = remember { AiFailoverClient() }

    var notesInput by remember { mutableStateOf("") }
    var attachedMedia by remember { mutableStateOf<AttachedMediaPayload?>(null) }
    var mediaError by remember { mutableStateOf<String?>(null) }
    var isExtracting by remember { mutableStateOf(false) }
    var lastStatus by remember { mutableStateOf<AiCompletionResult?>(null) }
    var proposals by remember { mutableStateOf<List<ProposedTrackerItem>>(emptyList()) }

    fun loadMediaFromUri(uri: Uri) {
        mediaError = null
        val resolvedMime = context.contentResolver.getType(uri) ?: "application/octet-stream"
        var displayName = uri.lastPathSegment ?: "attachment"
        var sizeBytes = 0L

        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIdx >= 0) {
                    val qName = cursor.getString(nameIdx)
                    if (!qName.isNullOrBlank()) displayName = qName
                }
                if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                    sizeBytes = cursor.getLong(sizeIdx)
                }
            }
        }

        val readResult = runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                if (resolvedMime.startsWith("image/", ignoreCase = true)) {
                    val bitmap = BitmapFactory.decodeStream(stream)
                        ?: throw IllegalStateException("Could not decode image.")
                    val out = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
                    val bytes = out.toByteArray()
                    AttachedMediaPayload(
                        displayName = displayName,
                        mimeType = "image/jpeg",
                        bytes = bytes,
                        sizeBytes = if (sizeBytes > 0L) sizeBytes else bytes.size.toLong()
                    )
                } else {
                    val maxPreviewBytes = 512 * 1024
                    val buffer = ByteArray(maxPreviewBytes)
                    val read = stream.read(buffer).coerceAtLeast(0)
                    val slice = if (read > 0) buffer.copyOf(read) else ByteArray(0)
                    AttachedMediaPayload(
                        displayName = displayName,
                        mimeType = resolvedMime,
                        bytes = slice,
                        sizeBytes = if (sizeBytes > 0L) sizeBytes else slice.size.toLong()
                    )
                }
            } ?: throw IllegalStateException("Unable to open selected media.")
        }

        readResult.onSuccess { payload ->
            attachedMedia = payload
        }.onFailure { err ->
            mediaError = err.message ?: "Failed to load attachment."
        }
    }

    val mediaPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            loadMediaFromUri(uri)
        }
    }

    val bgDark = Color(0xFF0E110F)
    val cardDark = Color(0xFF171C18)
    val accentGreen = Color(0xFF7FD18B)
    val bannerGreen = Color(0xFF28422E)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgDark)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "AI Workspace",
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
            TextButton(onClick = onOpenSettings) {
                Text("AI Settings", color = accentGreen)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(cardDark, RoundedCornerShape(16.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Media (Photo / Audio / Video / File) -> Safe Drafts",
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Active provider: ${aiState.activeProvider.label}. Nothing is added until you approve it.",
                color = Color(0xFF9FB0A3),
                style = MaterialTheme.typography.bodySmall
            )

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

            attachedMedia?.let { media ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF111512), RoundedCornerShape(10.dp))
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = media.displayName,
                            color = accentGreen,
                            fontWeight = FontWeight.Medium,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "${media.mimeType} • ${media.sizeBytes} bytes",
                            color = Color(0xFF9FB0A3),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Text(
                        text = "Clear",
                        color = Color(0xFFE57373),
                        modifier = Modifier
                            .clickable { attachedMedia = null }
                            .padding(start = 8.dp)
                    )
                }
            }

            mediaError?.let { err ->
                Text(
                    text = err,
                    color = Color(0xFFE57373),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            OutlinedTextField(
                value = notesInput,
                onValueChange = { notesInput = it },
                label = { Text("Context or instructions (optional)") },
                singleLine = false,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                enabled = !isExtracting,
                onClick = {
                    isExtracting = true
                    lastStatus = null
                    scope.launch {
                        val (res, items) = client.proposeTrackers(
                            state = aiState,
                            contextNotes = notesInput,
                            media = attachedMedia
                        )
                        lastStatus = res
                        proposals = items
                        isExtracting = false
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = accentGreen),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (isExtracting) "Analyzing..." else "Extract Safe Suggestions",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold
                )
            }

            lastStatus?.let { res ->
                if (!res.success && res.errors.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF331919), RoundedCornerShape(10.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        res.errors.forEach { err ->
                            Text(
                                text = "• $err",
                                color = Color(0xFFFFCDD2),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                } else if (res.success && proposals.isEmpty()) {
                    Text(
                        text = "No safe habit/task suggestions found in input.",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            proposals.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(bannerGreen, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.title,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "${item.type.name} • ${item.category}",
                            color = accentGreen,
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (item.reason.isNotBlank()) {
                            Text(
                                text = item.reason,
                                color = Color(0xFFD0E4D3),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            onApproveProposedItem(item)
                            proposals = proposals.filterNot { it === item }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = accentGreen)
                    ) {
                        Text("Add", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
