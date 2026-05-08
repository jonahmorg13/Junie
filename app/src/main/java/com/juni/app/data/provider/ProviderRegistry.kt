package com.juni.app.data.provider

import com.juni.app.data.prefs.AppSettings
import com.juni.app.data.prefs.ProviderId
import com.juni.app.data.prefs.SecurePrefs
import com.juni.app.data.prefs.Settings
import kotlinx.coroutines.flow.first

/**
 * Picks the right [AiProvider] implementation for the user's currently
 * selected provider. Returns [Resolution.Incomplete] with an explanatory
 * message when configuration is missing (no API key, no model name, etc.).
 */
object ProviderRegistry {

    sealed class Resolution {
        data class Ready(val provider: AiProvider, val model: String) : Resolution()
        data class Incomplete(val message: String) : Resolution()
    }

    suspend fun resolve(
        appSettings: AppSettings,
        securePrefs: SecurePrefs,
    ): Resolution = resolveFor(appSettings.flow.first(), securePrefs)

    fun resolveFor(settings: Settings, securePrefs: SecurePrefs): Resolution {
        val provider = settings.providerId
        val model = settings.modelByProvider[provider].orEmpty()
        if (model.isBlank()) {
            return Resolution.Incomplete("No model name set for ${provider.label}. Open Settings → ai provider.")
        }
        val key = securePrefs.apiKey(provider)
        return when (provider) {
            ProviderId.CLAUDE -> {
                if (key.isBlank()) Resolution.Incomplete("Paste your Claude API key in Settings.")
                else Resolution.Ready(ClaudeProvider(key), model)
            }
            ProviderId.OPENAI -> {
                if (key.isBlank()) Resolution.Incomplete("Paste your OpenAI API key in Settings.")
                else Resolution.Ready(OpenAiProvider(key), model)
            }
            ProviderId.GEMINI -> {
                if (key.isBlank()) Resolution.Incomplete("Paste your Gemini API key in Settings.")
                else Resolution.Ready(GeminiProvider(key), model)
            }
            ProviderId.OLLAMA -> {
                val baseUrl = settings.ollamaBaseUrl.ifBlank { "http://localhost:11434" }
                Resolution.Ready(OllamaProvider(baseUrl), model)
            }
        }
    }
}
