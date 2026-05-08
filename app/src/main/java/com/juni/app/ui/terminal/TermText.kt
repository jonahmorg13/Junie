package com.juni.app.ui.terminal

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import com.juni.app.ui.theme.TermAccent
import com.juni.app.ui.theme.TermDim
import com.juni.app.ui.theme.TermFg
import com.juni.app.ui.theme.TermGreen
import com.juni.app.ui.theme.TermMuted
import com.juni.app.ui.theme.TermRed
import com.juni.app.ui.theme.TermType

enum class TermColor { Fg, Dim, Accent, Muted, Green, Red }

private fun TermColor.toColor(): Color = when (this) {
    TermColor.Fg -> TermFg
    TermColor.Dim -> TermDim
    TermColor.Accent -> TermAccent
    TermColor.Muted -> TermMuted
    TermColor.Green -> TermGreen
    TermColor.Red -> TermRed
}

@Composable
fun TermText(
    text: String,
    modifier: Modifier = Modifier,
    color: TermColor = TermColor.Fg,
    bold: Boolean = false,
    style: TextStyle = TermType.body,
) {
    val resolved = style.copy(
        color = color.toColor(),
        fontWeight = if (bold) FontWeight.Bold else style.fontWeight,
    )
    BasicText(text = text, modifier = modifier, style = resolved)
}
