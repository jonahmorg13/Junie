package com.juni.app.data.prefs

// Order here is the order shown in settings. Keep [NOTO_SERIF] first since it's
// the app default and the value [fromKey] falls back to.
enum class FontPref(val key: String, val label: String) {
    NOTO_SERIF("noto_serif", "noto serif"),
    NOTO_SANS("noto_sans", "noto sans"),
    NOTO_SANS_MONO("noto_sans_mono", "noto sans mono"),
    JETBRAINS_MONO("jetbrains_mono", "jetbrains mono"),
    HACK("hack", "hack"),
    DEJAVU_SANS("dejavu_sans", "dejavu sans"),
    DEJAVU_SERIF("dejavu_serif", "dejavu serif"),
    DEJAVU_SANS_MONO("dejavu_sans_mono", "dejavu sans mono"),
    ADWAITA_SANS("adwaita_sans", "adwaita sans"),
    ADWAITA_MONO("adwaita_mono", "adwaita mono");

    companion object {
        fun fromKey(key: String?): FontPref =
            entries.firstOrNull { it.key == key } ?: NOTO_SERIF
    }
}
