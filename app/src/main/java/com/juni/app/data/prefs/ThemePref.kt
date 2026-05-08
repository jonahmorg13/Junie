package com.juni.app.data.prefs

enum class ThemePref(val key: String, val label: String) {
    DARK("dark", "dark"),
    LIGHT("light", "light");

    companion object {
        fun fromKey(key: String?): ThemePref =
            entries.firstOrNull { it.key == key } ?: DARK
    }
}
