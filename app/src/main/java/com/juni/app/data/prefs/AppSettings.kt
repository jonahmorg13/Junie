package com.juni.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.juni.app.domain.agent.JUNI_SYSTEM_PROMPT
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "juni_settings")

data class Settings(
    val providerId: ProviderId,
    val modelByProvider: Map<ProviderId, String>,
    val vaultUri: String?,
    val ollamaBaseUrl: String,
    val systemPrompt: String,
)

private val DEFAULT_MODELS = mapOf(
    ProviderId.CLAUDE to "claude-opus-4-7",
    ProviderId.OPENAI to "gpt-4o",
    ProviderId.GEMINI to "gemini-2.0-flash",
    ProviderId.OLLAMA to "llama3.2-vision",
)

class AppSettings(private val context: Context) {

    val flow: Flow<Settings> = context.dataStore.data.map { prefs -> prefs.toSettings() }

    suspend fun setProvider(provider: ProviderId) {
        context.dataStore.edit { it[KEY_PROVIDER] = provider.key }
    }

    suspend fun setModel(provider: ProviderId, model: String) {
        context.dataStore.edit { it[modelKey(provider)] = model }
    }

    suspend fun setVaultUri(uri: String?) {
        context.dataStore.edit {
            if (uri == null) it.remove(KEY_VAULT_URI) else it[KEY_VAULT_URI] = uri
        }
    }

    suspend fun setOllamaBaseUrl(url: String) {
        context.dataStore.edit { it[KEY_OLLAMA_URL] = url }
    }

    suspend fun setSystemPrompt(value: String) {
        context.dataStore.edit { it[KEY_SYSTEM_PROMPT] = value }
    }

    suspend fun resetSystemPrompt() {
        context.dataStore.edit { it.remove(KEY_SYSTEM_PROMPT) }
    }

    private fun Preferences.toSettings(): Settings {
        val provider = ProviderId.fromKey(this[KEY_PROVIDER])
        val models = ProviderId.entries.associateWith { p ->
            this[modelKey(p)] ?: DEFAULT_MODELS.getValue(p)
        }
        return Settings(
            providerId = provider,
            modelByProvider = models,
            vaultUri = this[KEY_VAULT_URI],
            ollamaBaseUrl = this[KEY_OLLAMA_URL] ?: "http://localhost:11434",
            systemPrompt = this[KEY_SYSTEM_PROMPT] ?: JUNI_SYSTEM_PROMPT,
        )
    }

    private fun modelKey(provider: ProviderId) =
        stringPreferencesKey("model_${provider.key}")

    companion object {
        private val KEY_PROVIDER = stringPreferencesKey("provider")
        private val KEY_VAULT_URI = stringPreferencesKey("vault_uri")
        private val KEY_OLLAMA_URL = stringPreferencesKey("ollama_base_url")
        private val KEY_SYSTEM_PROMPT = stringPreferencesKey("system_prompt")
    }
}
