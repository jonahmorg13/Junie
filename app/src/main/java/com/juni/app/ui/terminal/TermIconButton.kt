package com.juni.app.ui.terminal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.juni.app.ui.theme.MonoFont
import com.juni.app.ui.theme.TermType

/**
 * Naked icon-style button — no fill, no border, just a glyph that flips color
 * when disabled. The glyph is rendered in [MonoFont] (regardless of the app's
 * serif body font) so arrow/symbol glyphs have predictable metrics.
 */
@Composable
fun TermIconButton(
    glyph: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: TermColor = TermColor.Fg,
    enabled: Boolean = true,
    bold: Boolean = true,
    fontSize: androidx.compose.ui.unit.TextUnit = 22.sp,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val effectiveColor = if (enabled) color else TermColor.Muted
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 40.dp, minHeight = 40.dp)
            .clip(CircleShape)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        TermText(
            text = glyph,
            color = effectiveColor,
            style = TermType.body.copy(
                fontFamily = MonoFont,
                fontSize = fontSize,
                fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            ),
        )
    }
}
