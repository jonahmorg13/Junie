package com.juni.app.ui.terminal

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.juni.app.ui.theme.LocalPalette
import com.juni.app.ui.theme.Palette
import com.juni.app.ui.theme.TermType

enum class TermColor { Fg, Dim, Accent, Muted, Green, Red }

private fun TermColor.resolve(palette: Palette): Color = when (this) {
    TermColor.Fg -> palette.fg
    TermColor.Dim -> palette.dim
    TermColor.Accent -> palette.accent
    TermColor.Muted -> palette.muted
    TermColor.Green -> palette.green
    TermColor.Red -> palette.red
}

@Composable
fun TermText(
    text: String,
    modifier: Modifier = Modifier,
    color: TermColor = TermColor.Fg,
    bold: Boolean = false,
    style: TextStyle = TermType.body,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    val palette = LocalPalette.current
    val resolved = style.copy(
        color = color.resolve(palette),
        fontWeight = if (bold) FontWeight.Bold else style.fontWeight,
    )
    BasicText(
        text = text,
        modifier = modifier,
        style = resolved,
        maxLines = maxLines,
        overflow = overflow,
    )
}
