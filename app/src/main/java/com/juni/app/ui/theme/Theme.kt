package com.juni.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowInsetsControllerCompat
import com.juni.app.JuniApp
import com.juni.app.R
import com.juni.app.data.prefs.ThemePref

/**
 * Full color set for one theme. Every Term* primitive resolves colors against
 * `LocalPalette.current` so a single switch in settings flips the whole UI.
 */
data class Palette(
    val bg: Color,
    val surface: Color,
    val fg: Color,
    val dim: Color,
    val accent: Color,
    val muted: Color,
    val green: Color,
    val red: Color,
    val selection: Color,
    /** Foreground used on top of `accent` (e.g. pressed buttons). */
    val onAccent: Color,
)

val DarkPalette = Palette(
    bg = Color(0xFF0E0E10),
    surface = Color(0xFF1F1F25),
    fg = Color(0xFFC8C8C2),
    dim = Color(0xFF888884),
    accent = Color(0xFFA593D5),
    muted = Color(0xFF3A3A3D),
    green = Color(0xFF16A34A),
    red = Color(0xFFCF6A6A),
    selection = Color(0xFF2A2438),
    onAccent = Color(0xFF0E0E10),
)

/** Derived from the launcher icon: cream paper + lavender comet. */
val LightPalette = Palette(
    bg = Color(0xFFFDF4E6),
    surface = Color(0xFFFFFAF1),
    fg = Color(0xFF2B2520),
    dim = Color(0xFF6F665C),
    accent = Color(0xFF8E7AC6),
    muted = Color(0xFFD9CFC0),
    green = Color(0xFF15803D),
    red = Color(0xFFB94A4A),
    selection = Color(0xFFE5DEF5),
    onAccent = Color(0xFFFDF4E6),
)

val LocalPalette = staticCompositionLocalOf { DarkPalette }

// Primary app font — Noto Serif, picked to feel closer to the Claude apps than
// JetBrains Mono. Italic is synthesized from the regular face (no italic TTF
// bundled to keep the APK leaner).
val TermFont = FontFamily(
    Font(R.font.noto_serif_regular, FontWeight.Normal),
    Font(R.font.noto_serif_bold, FontWeight.Bold),
)

// Dedicated monospace, still JetBrains Mono. Used only where character-cell
// alignment matters: code blocks, inline code, tool I/O.
val MonoFont = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
)

/**
 * Text style accessors. Composable getters so they pick up palette changes —
 * `TermType.body` resolved in light mode returns a TextStyle colored with
 * `LightPalette.fg` automatically.
 */
object TermType {
    val body: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = TextStyle(
            fontFamily = TermFont,
            fontSize = 14.sp,
            color = LocalPalette.current.fg,
        )

    val small: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = TextStyle(
            fontFamily = TermFont,
            fontSize = 12.sp,
            color = LocalPalette.current.fg,
        )

    val header: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = TextStyle(
            fontFamily = TermFont,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = LocalPalette.current.accent,
        )

    /** Page-level title — appears in the top bar of each screen. */
    val title: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = TextStyle(
            fontFamily = TermFont,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = LocalPalette.current.accent,
        )
}

/**
 * App-root theme wrapper. Reads the persisted ThemePref from AppSettings and
 * provides the matching Palette down the tree, plus paints the root background.
 */
@Composable
fun JuniTheme(content: @Composable () -> Unit) {
    val settingsFlow = JuniApp.get().appSettings.flow
    val settings by settingsFlow.collectAsState(initial = null)
    val palette = when (settings?.theme) {
        ThemePref.LIGHT -> LightPalette
        else -> DarkPalette
    }
    val isLightTheme = palette === LightPalette

    // Tell the system to use dark status- and navigation-bar icons in light
    // mode (so they're legible on the cream bg) and light icons in dark mode.
    // enableEdgeToEdge() in MainActivity makes the bars transparent; the icon
    // tint is independent and lives on WindowInsetsControllerCompat.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowInsetsControllerCompat(window, view).apply {
                isAppearanceLightStatusBars = isLightTheme
                isAppearanceLightNavigationBars = isLightTheme
            }
        }
    }

    CompositionLocalProvider(LocalPalette provides palette) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(palette.bg),
        ) {
            content()
        }
    }
}
