package com.juni.app.data.prefs

enum class ProviderId(val key: String, val label: String) {
    CLAUDE("claude", "claude"),
    OPENAI("openai", "openai"),
    GEMINI("gemini", "gemini"),
    OLLAMA("ollama", "ollama");

    companion object {
        fun fromKey(key: String?): ProviderId =
            entries.firstOrNull { it.key == key } ?: CLAUDE
    }
}
