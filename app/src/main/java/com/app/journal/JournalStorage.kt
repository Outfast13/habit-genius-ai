package com.app.journal

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class JournalStorage(private val context: Context) {

    private val prefs = context.getSharedPreferences("habit_genius_journal_prefs", Context.MODE_PRIVATE)
    private val mediaDir: File
        get() = File(context.filesDir, "journal_media").apply { mkdirs() }

    fun save(state: JournalState) {
        val entriesArray = JSONArray()
        for (entry in state.entries) {
            val attArray = JSONArray()
            for (att in entry.attachments) {
                attArray.put(
                    JSONObject().apply {
                        put("id", att.id)
                        put("displayName", att.displayName)
                        put("mimeType", att.mimeType)
                        put("localPath", att.localPath)
                        put("sizeBytes", att.sizeBytes)
                    }
                )
            }
            entriesArray.put(
                JSONObject().apply {
                    put("id", entry.id)
                    put("title", entry.title)
                    put("body", entry.body)
                    put("mood", entry.mood)
                    put("timestampMs", entry.timestampMs)
                    put("attachments", attArray)
                }
            )
        }
        val root = JSONObject().put("entries", entriesArray)
        prefs.edit().putString("journal_entries_json", root.toString()).apply()
    }

    fun load(): JournalState {
        val raw = prefs.getString("journal_entries_json", null) ?: return JournalState()
        return try {
            val root = JSONObject(raw)
            val entriesArray = root.optJSONArray("entries") ?: JSONArray()
            val list = mutableListOf<JournalEntry>()

            for (i in 0 until entriesArray.length()) {
                val obj = entriesArray.optJSONObject(i) ?: continue
                val id = obj.optString("id", "").trim()
                if (id.isEmpty()) continue

                val attList = mutableListOf<JournalAttachment>()
                val attArray = obj.optJSONArray("attachments") ?: JSONArray()
                for (j in 0 until attArray.length()) {
                    val aObj = attArray.optJSONObject(j) ?: continue
                    val aId = aObj.optString("id", "").trim()
                    val path = aObj.optString("localPath", "").trim()
                    if (aId.isEmpty() || path.isEmpty()) continue
                    attList.add(
                        JournalAttachment(
                            id = aId,
                            displayName = aObj.optString("displayName", "file"),
                            mimeType = aObj.optString("mimeType", "application/octet-stream"),
                            localPath = path,
                            sizeBytes = aObj.optLong("sizeBytes", 0L)
                        )
                    )
                }

                list.add(
                    JournalEntry(
                        id = id,
                        title = obj.optString("title", ""),
                        body = obj.optString("body", ""),
                        mood = obj.optString("mood", "Neutral"),
                        timestampMs = obj.optLong("timestampMs", 0L),
                        attachments = attList
                    )
                )
            }
            JournalState(entries = list)
        } catch (_: Exception) {
            JournalState()
        }
    }

    fun getMediaDirectory(): File = mediaDir
}
