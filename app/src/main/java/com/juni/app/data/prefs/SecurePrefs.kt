@file:Suppress("DEPRECATION") // EncryptedSharedPreferences is deprecated upstream but is still
// the documented AndroidX path for at-rest secret storage; revisit once a successor lands.

package com.juni.app.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Android-Keystore-backed storage for things we never want in plaintext:
 * provider API keys and the Ollama base URL.
 */
class SecurePrefs(context: Context) {

    private val prefs: SharedPreferences = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun apiKey(provider: ProviderId): String =
        prefs.getString(keyApi(provider), null).orEmpty()

    fun setApiKey(provider: ProviderId, value: String) {
        prefs.edit().putString(keyApi(provider), value).apply()
    }

    fun ollamaBaseUrl(): String =
        prefs.getString(KEY_OLLAMA_URL, null).orEmpty()

    fun setOllamaBaseUrl(value: String) {
        prefs.edit().putString(KEY_OLLAMA_URL, value).apply()
    }

    private fun keyApi(provider: ProviderId) = "api_key_${provider.key}"

    companion object {
        private const val FILE_NAME = "juni_secure_prefs"
        private const val KEY_OLLAMA_URL = "ollama_base_url"
    }
}
