package com.app.ai

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

class AiFailoverClient {

    suspend fun generateText(
        state: AiSettingsState,
        prompt: String,
        usePhotoModel: Boolean = false
    ): AiCompletionResult = withContext(Dispatchers.IO) {
        if (!state.isOnline) {
            return@withContext AiCompletionResult(
                success = false,
                providerUsed = null,
                text = "",
                errors = listOf("AI mode is set to offline.")
            )
        }

        val providerOrder = buildProviderOrder(state)
        val errors = mutableListOf<String>()

        for (provider in providerOrder) {
            val cfg = state.providers[provider] ?: ProviderSettings()
            val modelId = if (usePhotoModel) {
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
                    callProvider(provider, cfg, modelId, key, prompt)
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
                    errors.add("${provider.label} ($labelKey): ${err.message ?: "request failed"}")
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
        prompt: String
    ): String {
        return when (provider) {
            AiProvider.GEMINI -> callGemini(modelId, apiKey, prompt)
            AiProvider.OPEN_ROUTER -> callOpenAiCompatible(
                endpoint = cfg.baseUrl.ifBlank { "https://openrouter.ai/api/v1/chat/completions" },
                modelId = modelId,
                apiKey = apiKey,
                prompt = prompt,
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
                    prompt = prompt
                )
            }
            AiProvider.OLLAMA -> {
                val base = cfg.baseUrl.trim().trimEnd('/')
                val endpoint = if (base.isEmpty()) {
                    "http://127.0.0.1:11434/api/generate"
                } else if (base.endsWith("/api/generate")) {
                    base
                } else {
                    "$base/api/generate"
                }
                callOllama(endpoint = endpoint, modelId = modelId, prompt = prompt)
            }
        }
    }

    private fun callGemini(modelId: String, apiKey: String, prompt: String): String {
        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$modelId:generateContent?key=$apiKey")
        val body = JSONObject().apply {
            put(
                "contents",
                JSONArray().put(
                    JSONObject().put(
                        "parts",
                        JSONArray().put(JSONObject().put("text", prompt))
                    )
                )
            )
        }.toString()

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
        extraHeaders: Map<String, String> = emptyMap()
    ): String {
        val body = JSONObject().apply {
            put("model", modelId)
            put(
                "messages",
                JSONArray().put(
                    JSONObject()
                        .put("role", "user")
                        .put("content", prompt)
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

    private fun callOllama(endpoint: String, modelId: String, prompt: String): String {
        val body = JSONObject().apply {
            put("model", modelId)
            put("prompt", prompt)
            put("stream", false)
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
