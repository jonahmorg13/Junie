package com.juni.app.ui.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontFamily
import com.juni.app.ui.theme.LocalPalette
import com.juni.app.ui.theme.MonoFont
import com.juni.app.ui.theme.Palette
import com.juni.app.ui.theme.TermType

/**
 * Pill button. The `color` param picks the semantic action color, which fills
 * the background; the label takes a contrasting foreground from the palette.
 * Neutral (`Fg`) renders as a quiet surface chip so it doesn't compete with
 * accent / green / red primary actions in the same row.
 */
@Composable
fun TermButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: TermColor = TermColor.Fg,
    enabled: Boolean = true,
    // Override the label's font. Defaults to the app's body font (via [TermType.body]);
    // the font-picker passes per-row faces so each label previews itself.
    labelFontFamily: FontFamily? = null,
) {
    val palette = LocalPalette.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val (bg, fg, borderColor) = resolveColors(palette, color, enabled)
    val effectiveBg = if (pressed && enabled) darken(bg, palette) else bg

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 32.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(effectiveBg)
            .then(
                if (borderColor != null) {
                    Modifier.border(1.dp, borderColor, RoundedCornerShape(6.dp))
                } else Modifier,
            )
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        TermText(
            text = label,
            style = TermType.body.copy(
                color = fg,
                fontFamily = labelFontFamily ?: MonoFont,
                fontWeight = FontWeight.Bold,
            ),
        )
    }
}

/** (background, foreground, optional border-color) for a button's resting state. */
private fun resolveColors(
    palette: Palette,
    color: TermColor,
    enabled: Boolean,
): Triple<Color, Color, Color?> {
    if (!enabled) return Triple(palette.muted, palette.dim, null)
    // White-on-colored is the conventional primary-CTA pattern and reads better
    // than near-black-on-bright-saturated. We force it for the strongly tinted
    // semantics (green/red); accent (lavender) keeps `onAccent` since lavender
    // is light enough that dark text on it is already high-contrast.
    val onColored = Color(0xFFFFFFFF)
    return when (color) {
        TermColor.Accent -> Triple(palette.accent, palette.onAccent, null)
        TermColor.Green -> Triple(palette.green, onColored, null)
        TermColor.Red -> Triple(palette.red, onColored, null)
        TermColor.Muted -> Triple(palette.muted, palette.dim, null)
        TermColor.Dim -> Triple(palette.surface, palette.dim, palette.muted)
        TermColor.Fg -> Triple(palette.surface, palette.fg, palette.muted)
    }
}

/**
 * Cheap pressed-state shading: blend toward the bg color of the app. Avoids
 * adding a second palette token; works fine for both light and dark themes
 * because `palette.bg` is the right "tint toward" target in both.
 */
private fun darken(base: Color, palette: Palette): Color =
    lerpColor(base, palette.bg, 0.25f)

private fun lerpColor(a: Color, b: Color, t: Float): Color = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = a.alpha + (b.alpha - a.alpha) * t,
)
