package com.app.ai

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class AiSettingsStorage(context: Context) {

    private val prefs = context.getSharedPreferences("habit_genius_ai_prefs", Context.MODE_PRIVATE)

    fun save(state: AiSettingsState) {
        val root = JSONObject().apply {
            put("isOnline", state.isOnline)
            put("autoFailover", state.autoFailover)
            put("showPaidModels", state.showPaidModels)
            put("activeProvider", state.activeProvider.name)

            val providersJson = JSONObject()
            for (provider in AiProvider.entries) {
                val cfg = state.providers[provider] ?: ProviderSettings()
                val keysArray = JSONArray().apply {
                    cfg.apiKeys.forEach { put(it) }
                }
                val cfgJson = JSONObject().apply {
                    put("apiKeys", keysArray)
                    put("photoModel", cfg.photoModel)
                    put("photoModelId", cfg.photoModelId)
                    put("textModel", cfg.textModel)
                    put("textModelId", cfg.textModelId)
                    put("baseUrl", cfg.baseUrl)
                }
                providersJson.put(provider.name, cfgJson)
            }
            put("providers", providersJson)
        }

        prefs.edit().putString("ai_settings_json", root.toString()).apply()
    }

    fun load(): AiSettingsState {
        val raw = prefs.getString("ai_settings_json", null) ?: return AiSettingsState()

        return try {
            val root = JSONObject(raw)
            val isOnline = root.optBoolean("isOnline", true)
            val autoFailover = root.optBoolean("autoFailover", true)
            val showPaidModels = root.optBoolean("showPaidModels", false)

            val providerName = root.optString("activeProvider", AiProvider.GEMINI.name)
            val activeProvider = runCatching { AiProvider.valueOf(providerName) }
                .getOrDefault(AiProvider.GEMINI)

            val providersJson = root.optJSONObject("providers")
            val loadedProviders = AiProvider.entries.associateWith { provider ->
                val cfgJson = providersJson?.optJSONObject(provider.name)
                if (cfgJson == null) {
                    ProviderSettings()
                } else {
                    val keysArray = cfgJson.optJSONArray("apiKeys") ?: JSONArray()
                    val keys = mutableListOf<String>()
                    for (i in 0 until keysArray.length()) {
                        val k = keysArray.optString(i, "").trim()
                        if (k.isNotEmpty()) keys.add(k)
                    }
                    ProviderSettings(
                        apiKeys = keys,
                        photoModel = cfgJson.optString("photoModel", "gemini-3.5-flash"),
                        photoModelId = cfgJson.optString("photoModelId", "gemini-3.5-flash"),
                        textModel = cfgJson.optString("textModel", "gemini-flash-lite-latest"),
                        textModelId = cfgJson.optString("textModelId", "gemini-flash-lite-latest"),
                        baseUrl = cfgJson.optString("baseUrl", "")
                    )
                }
            }

            AiSettingsState(
                isOnline = isOnline,
                autoFailover = autoFailover,
                showPaidModels = showPaidModels,
                activeProvider = activeProvider,
                providers = loadedProviders
            )
        } catch (_: Exception) {
            AiSettingsState()
        }
    }
}
