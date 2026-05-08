package com.juni.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.juni.app.JuniApp
import com.juni.app.data.prefs.ProviderId
import com.juni.app.data.prefs.Settings
import com.juni.app.data.prefs.ThemePref
import com.juni.app.ui.terminal.Toaster
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class SettingsUi(
    val settings: Settings? = null,
    val apiKeyByProvider: Map<ProviderId, String> = emptyMap(),
)

class SettingsViewModel : ViewModel() {

    private val app = JuniApp.get()
    private val _ui = MutableStateFlow(SettingsUi())
    val ui: StateFlow<SettingsUi> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            app.appSettings.flow.collect { settings ->
                val keys = ProviderId.entries.associateWith { app.securePrefs.apiKey(it) }
                _ui.value = SettingsUi(settings = settings, apiKeyByProvider = keys)
            }
        }
    }

    fun selectProvider(provider: ProviderId) {
        viewModelScope.launch {
            app.appSettings.setProvider(provider)
            Toaster.success("provider: ${provider.label}")
        }
    }

    fun setModel(provider: ProviderId, model: String) {
        viewModelScope.launch { app.appSettings.setModel(provider, model) }
        // Model edits fire on every keystroke; no toast — too chatty.
    }

    fun setApiKey(provider: ProviderId, value: String) {
        app.securePrefs.setApiKey(provider, value)
        _ui.value = _ui.value.copy(
            apiKeyByProvider = _ui.value.apiKeyByProvider + (provider to value),
        )
        // No toast on every keystroke; the masked field itself is the feedback.
    }

    fun clearApiKey(provider: ProviderId) {
        setApiKey(provider, "")
        Toaster.success("${provider.label} key cleared")
    }

    fun setOllamaBaseUrl(url: String) {
        viewModelScope.launch { app.appSettings.setOllamaBaseUrl(url) }
    }

    fun setVaultUri(uri: String?) {
        viewModelScope.launch {
            app.appSettings.setVaultUri(uri)
            if (uri == null) Toaster.success("vault cleared")
            // Setting a fresh vault URI is toasted from the SAF picker callback so
            // we can include the chosen path.
        }
    }

    fun setSystemPrompt(value: String) {
        viewModelScope.launch {
            app.appSettings.setSystemPrompt(value)
            Toaster.success("system prompt saved")
        }
    }

    fun setTheme(theme: ThemePref) {
        viewModelScope.launch {
            app.appSettings.setTheme(theme)
            Toaster.success("theme: ${theme.label}")
        }
    }

    fun resetSystemPrompt() {
        viewModelScope.launch {
            app.appSettings.resetSystemPrompt()
            Toaster.success("system prompt reset")
        }
    }

    fun deleteAllConversations() {
        viewModelScope.launch {
            val n = app.conversationRepository.deleteAll()
            Toaster.success(if (n == 0) "no conversations to delete" else "deleted $n conversation${if (n == 1) "" else "s"}")
        }
    }

    suspend fun currentSettings(): Settings = app.appSettings.flow.first()
}
