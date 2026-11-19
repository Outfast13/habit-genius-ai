package com.app.ai

enum class AiProvider(val label: String) {
    OPEN_ROUTER("OpenRouter"),
    GEMINI("Gemini"),
    OLLAMA("OLLama"),
    OPENAI_COMPATIBLE("OpenAI-compatible")
}

data class ProviderSettings(
    val apiKeys: List<String> = emptyList(),
    val photoModel: String = "gemini-3.5-flash",
    val photoModelId: String = "gemini-3.5-flash",
    val textModel: String = "gemini-flash-lite-latest",
    val textModelId: String = "gemini-flash-lite-latest",
    val baseUrl: String = ""
)

data class AiSettingsState(
    val isOnline: Boolean = true,
    val autoFailover: Boolean = true,
    val showPaidModels: Boolean = false,
    val activeProvider: AiProvider = AiProvider.GEMINI,
    val providers: Map<AiProvider, ProviderSettings> = AiProvider.entries.associateWith {
        ProviderSettings()
    }
)
