package com.juni.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.juni.app.JuniApp
import com.juni.app.data.prefs.ProviderId
import com.juni.app.data.prefs.Settings
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
        viewModelScope.launch { app.appSettings.setProvider(provider) }
    }

    fun setModel(provider: ProviderId, model: String) {
        viewModelScope.launch { app.appSettings.setModel(provider, model) }
    }

    fun setApiKey(provider: ProviderId, value: String) {
        app.securePrefs.setApiKey(provider, value)
        _ui.value = _ui.value.copy(
            apiKeyByProvider = _ui.value.apiKeyByProvider + (provider to value),
        )
    }

    fun setOllamaBaseUrl(url: String) {
        viewModelScope.launch { app.appSettings.setOllamaBaseUrl(url) }
    }

    fun setVaultUri(uri: String?) {
        viewModelScope.launch { app.appSettings.setVaultUri(uri) }
    }

    suspend fun currentSettings(): Settings = app.appSettings.flow.first()
}
