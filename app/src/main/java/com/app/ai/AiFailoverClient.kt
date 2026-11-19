package com.app.ai

import android.util.Base64
import com.app.habits.TrackerItemType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class AiCompletionResult(
    val success: Boolean,
    val providerUsed: AiProvider?,
    val text: String,
    val errors: List<String> = emptyList()
)

data class ProposedTrackerItem(
    val title: String,
    val type: TrackerItemType,
    val category: String = "General",
    val reason: String = ""
)

data class AttachedMediaPayload(
    val displayName: String,
    val mimeType: String,
    val bytes: ByteArray? = null,
    val sizeBytes: Long = 0L
) {
    val isImage: Boolean
        get() = mimeType.startsWith("image/", ignoreCase = true)

    val isAudio: Boolean
        get() = mimeType.startsWith("audio/", ignoreCase = true)

    val isVideo: Boolean
        get() = mimeType.startsWith("video/", ignoreCase = true)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as AttachedMediaPayload
        return displayName == other.displayName &&
            mimeType == other.mimeType &&
            sizeBytes == other.sizeBytes &&
            bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int {
        var result = displayName.hashCode()
        result = 31 * result + mimeType.hashCode()
        result = 31 * result + sizeBytes.hashCode()
        result = 31 * result + (bytes?.contentHashCode() ?: 0)
        return result
    }
}

class AiFailoverClient {

    suspend fun generateText(
        state: AiSettingsState,
        prompt: String,
        usePhotoModel: Boolean = false,
        media: AttachedMediaPayload? = null
    ): AiCompletionResult = withContext(Dispatchers.IO) {
        if (!state.isOnline) {
            return@withContext AiCompletionResult(
                success = false,
                providerUsed = null,
                text = "",
                errors = listOf("AI mode is set to offline.")
            )
        }

        val hasImage = media?.isImage == true && media.bytes != null && media.bytes.isNotEmpty()
        val effectivePrompt = buildMediaAwarePrompt(prompt, media, hasImage)

        val providerOrder = buildProviderOrder(state)
        val errors = mutableListOf<String>()

        for (provider in providerOrder) {
            val cfg = state.providers[provider] ?: ProviderSettings()
            val modelId = if (usePhotoModel || hasImage) {
                cfg.photoModelId.ifBlank { cfg.photoModel }
            } else {
                cfg.textModelId.ifBlank { cfg.textModel }
            }.trim()

            if (modelId.isEmpty()) {
                errors.add("${provider.label}: no model ID configured.")
                if (!state.autoFailover) break
                continue
            }

            val keysToTry = if (provider == AiProvider.OLLAMA) {
                listOf("")
            } else {
                cfg.apiKeys.map { it.trim() }.filter { it.isNotEmpty() }
            }

            if (keysToTry.isEmpty()) {
                errors.add("${provider.label}: no API keys configured.")
                if (!state.autoFailover) break
                continue
            }

            for ((index, key) in keysToTry.withIndex()) {
                val attempt = runCatching {
                    callProvider(
                        provider = provider,
                        cfg = cfg,
                        modelId = modelId,
                        apiKey = key,
                        prompt = effectivePrompt,
                        imageBytes = if (hasImage) media?.bytes else null,
                        imageMimeType = media?.mimeType?.ifBlank { "image/jpeg" } ?: "image/jpeg"
                    )
                }

                attempt.onSuccess { reply ->
                    return@withContext AiCompletionResult(
                        success = true,
                        providerUsed = provider,
                        text = reply,
                        errors = errors
                    )
                }.onFailure { err ->
                    val labelKey = if (key.isBlank()) "local" else "key#${index + 1}"
                    errors.add("${provider.label} ($labelKey):${err.message ?: "request failed"}")
                }

                if (!state.autoFailover) {
                    break
                }
            }

            if (!state.autoFailover) {
                break
            }
        }

        AiCompletionResult(
            success = false,
            providerUsed = null,
            text = "",
            errors = errors
        )
    }

    suspend fun proposeTrackers(
        state: AiSettingsState,
        contextNotes: String,
        media: AttachedMediaPayload? = null
    ): Pair<AiCompletionResult, List<ProposedTrackerItem>> {
        val guardPrompt = buildString {
            appendLine("You are a careful productivity assistant.")
            appendLine("Extract actionable HABIT or TASK items strictly grounded in the user's input or attached media.")
            appendLine("Do NOT invent tasks that are not supported by the input. Keep titles concise (max 8 words).")
            appendLine("Respond ONLY with a valid JSON array of objects using keys: \"title\", \"type\" (\"HABIT\" or \"TASK\"), \"category\", \"reason\".")
            if (contextNotes.isNotBlank()) {
                appendLine("User notes: ${contextNotes.trim()}")
            }
        }

        val hasImage = media?.isImage == true && media.bytes != null
        val result = generateText(
            state = state,
            prompt = guardPrompt,
            usePhotoModel = hasImage,
            media = media
        )

        if (!result.success || result.text.isBlank()) {
            return result to emptyList()
        }

        val parsed = parseProposedItemsSafely(result.text)
        return result to parsed
    }

    private fun buildMediaAwarePrompt(
        basePrompt: String,
        media: AttachedMediaPayload?,
        hasImage: Boolean
    ): String {
        if (media == null || hasImage) return basePrompt

        val mediaTypeLabel = when {
            media.isAudio -> "Audio file"
            media.isVideo -> "Video file"
            else -> "Attached file"
        }

        val textSnippet = if (media.bytes != null && media.bytes.isNotEmpty() && isLikelyText(media.mimeType, media.displayName)) {
            val rawText = String(media.bytes, Charsets.UTF_8).take(4000)
            "\nFile text preview:\n$rawText"
        } else {
            ""
        }

        return buildString {
            append(basePrompt)
            appendLine()
            appendLine("[$mediaTypeLabel: name=\"${media.displayName}\", mime=\"${media.mimeType}\", bytes=${media.sizeBytes}]")
            appendLine("Note: Binary audio/video/file streams are not decoded as raw waveforms/frames here; rely on the metadata, file text preview if present, and the user's notes without guessing unseen details.")
            append(textSnippet)
        }
    }

    private fun isLikelyText(mimeType: String, fileName: String): Boolean {
        val lowerMime = mimeType.lowercase()
        val lowerName = fileName.lowercase()
        return lowerMime.startsWith("text/") ||
            lowerMime.contains("json") ||
            lowerMime.contains("csv") ||
            lowerName.endsWith(".txt") ||
            lowerName.endsWith(".md") ||
            lowerName.endsWith(".csv") ||
            lowerName.endsWith(".json")
    }

    private fun parseProposedItemsSafely(rawText: String): List<ProposedTrackerItem> {
        val cleaned = rawText
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        val startIdx = cleaned.indexOf('[')
        val endIdx = cleaned.lastIndexOf(']')
        if (startIdx == -1 || endIdx <= startIdx) return emptyList()

        val jsonArray = runCatching { JSONArray(cleaned.substring(startIdx, endIdx + 1)) }.getOrNull()
            ?: return emptyList()

        val safeList = mutableListOf<ProposedTrackerItem>()
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.optJSONObject(i) ?: continue
            val title = obj.optString("title", "").trim()
            if (title.isBlank() || title.length > 100) continue

            val rawType = obj.optString("type", "TASK").trim().uppercase()
            val type = if (rawType == "HABIT") TrackerItemType.HABIT else TrackerItemType.TASK

            val category = obj.optString("category", "General").trim().ifEmpty { "General" }.take(30)
            val reason = obj.optString("reason", "").trim().take(140)

            safeList.add(
                ProposedTrackerItem(
                    title = title,
                    type = type,
                    category = category,
                    reason = reason
                )
            )
        }
        return safeList.distinctBy { it.title.lowercase() to it.type }
    }

    private fun buildProviderOrder(state: AiSettingsState): List<AiProvider> {
        if (!state.autoFailover) {
            return listOf(state.activeProvider)
        }
        return listOf(state.activeProvider) + AiProvider.entries.filter { it != state.activeProvider }
    }

    private fun callProvider(
        provider: AiProvider,
        cfg: ProviderSettings,
        modelId: String,
        apiKey: String,
        prompt: String,
        imageBytes: ByteArray?,
        imageMimeType: String
    ): String {
        val base64Image = imageBytes?.let { Base64.encodeToString(it, Base64.NO_WRAP) }

        return when (provider) {
            AiProvider.GEMINI -> callGemini(modelId, apiKey, prompt, base64Image, imageMimeType)
            AiProvider.OPEN_ROUTER -> callOpenAiCompatible(
                endpoint = cfg.baseUrl.ifBlank { "[https://openrouter.ai/api/v1/chat/completions](https://openrouter.ai/api/v1/chat/completions)" },
                modelId = modelId,
                apiKey = apiKey,
                prompt = prompt,
                base64Image = base64Image,
                imageMimeType = imageMimeType,
                extraHeaders = mapOf("X-Title" to "HabitGenius AI")
            )
            AiProvider.OPENAI_COMPATIBLE -> {
                val base = cfg.baseUrl.trim().trimEnd('/')
                require(base.isNotEmpty()) { "Base URL is required for OpenAI-compatible provider." }
                val endpoint = if (base.endsWith("/chat/completions")) base else "$base/chat/completions"
                callOpenAiCompatible(
                    endpoint = endpoint,
                    modelId = modelId,
                    apiKey = apiKey,
                    prompt = prompt,
                    base64Image = base64Image,
                    imageMimeType = imageMimeType
                )
            }
            AiProvider.OLLAMA -> {
                val base = cfg.baseUrl.trim().trimEnd('/')
                val endpoint = if (base.isEmpty()) {
                    "[http://127.0.0.1:11434/api/generate](http://127.0.0.1:11434/api/generate)"
                } else if (base.endsWith("/api/generate")) {
                    base
                } else {
                    "$base/api/generate"
                }
                callOllama(endpoint = endpoint, modelId = modelId, prompt = prompt, base64Image = base64Image)
            }
        }
    }

    private fun callGemini(
        modelId: String,
        apiKey: String,
        prompt: String,
        base64Image: String?,
        imageMimeType: String
    ): String {
        val url = URL("[https://generativelanguage.googleapis.com/v1beta/models/$modelId:generateContent?key=$apiKey](https://generativelanguage.googleapis.com/v1beta/models/$modelId:generateContent?key=$apiKey)")
        val partsArray = JSONArray().put(JSONObject().put("text", prompt))
        if (base64Image != null) {
            partsArray.put(
                JSONObject().put(
                    "inlineData",
                    JSONObject()
                        .put("mimeType", imageMimeType)
                        .put("data", base64Image)
                )
            )
        }

        val body = JSONObject().put(
            "contents",
            JSONArray().put(JSONObject().put("parts", partsArray))
        ).toString()

        val rawResponse = postJson(url.toString(), body, emptyMap())
        val json = JSONObject(rawResponse)
        val candidates = json.optJSONArray("candidates") ?: throw IllegalStateException("Empty Gemini response")
        val content = candidates.optJSONObject(0)?.optJSONObject("content")
        val parts = content?.optJSONArray("parts")
        val text = parts?.optJSONObject(0)?.optString("text", "").orEmpty()
        if (text.isBlank()) throw IllegalStateException("No text in Gemini response")
        return text.trim()
    }

    private fun callOpenAiCompatible(
        endpoint: String,
        modelId: String,
        apiKey: String,
        prompt: String,
        base64Image: String?,
        imageMimeType: String,
        extraHeaders: Map<String, String> = emptyMap()
    ): String {
        val contentValue: Any = if (base64Image != null) {
            JSONArray()
                .put(JSONObject().put("type", "text").put("text", prompt))
                .put(
                    JSONObject()
                        .put("type", "image_url")
                        .put(
                            "image_url",
                            JSONObject().put("url", "data:$imageMimeType;base64,$base64Image")
                        )
                )
        } else {
            prompt
        }

        val body = JSONObject().apply {
            put("model", modelId)
            put(
                "messages",
                JSONArray().put(
                    JSONObject()
                        .put("role", "user")
                        .put("content", contentValue)
                )
            )
        }.toString()

        val headers = mutableMapOf(
            "Authorization" to "Bearer $apiKey"
        ).apply {
            putAll(extraHeaders)
        }

        val rawResponse = postJson(endpoint, body, headers)
        val json = JSONObject(rawResponse)
        val choices = json.optJSONArray("choices") ?: throw IllegalStateException("Empty choices array")
        val message = choices.optJSONObject(0)?.optJSONObject("message")
        val content = message?.optString("content", "").orEmpty()
        if (content.isBlank()) throw IllegalStateException("Empty message content")
        return content.trim()
    }

    private fun callOllama(
        endpoint: String,
        modelId: String,
        prompt: String,
        base64Image: String?
    ): String {
        val body = JSONObject().apply {
            put("model", modelId)
            put("prompt", prompt)
            put("stream", false)
            if (base64Image != null) {
                put("images", JSONArray().put(base64Image))
            }
        }.toString()

        val rawResponse = postJson(endpoint, body, emptyMap())
        val json = JSONObject(rawResponse)
        val responseText = json.optString("response", "").trim()
        if (responseText.isBlank()) throw IllegalStateException("Empty Ollama response")
        return responseText
    }

    private fun postJson(
        endpoint: String,
        jsonBody: String,
        headers: Map<String, String>
    ): String {
        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 30_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }

        try {
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(jsonBody)
                writer.flush()
            }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.let { s ->
                BufferedReader(InputStreamReader(s, Charsets.UTF_8)).use { it.readText() }
            }.orEmpty()

            if (code !in 200..299) {
                throw IllegalStateException("HTTP $code: ${text.take(200)}")
            }
            return text
        } finally {
            conn.disconnect()
        }
    }
}
