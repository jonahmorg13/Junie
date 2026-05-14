package com.juni.app.ui.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.juni.app.ui.theme.LocalPalette
import com.juni.app.ui.theme.MonoFont
import com.juni.app.ui.theme.TermType

/**
 * Square checkbox in our palette. Unchecked = empty box with a muted border;
 * checked = filled with accent and a checkmark glyph. Stateless — the caller
 * supplies [checked] and toggling is handled by the surrounding row's tap.
 */
@Composable
fun TermCheckbox(
    checked: Boolean,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    val shape = RoundedCornerShape(3.dp)
    val border = if (checked) palette.accent else palette.muted
    val fill = if (checked) palette.accent else Color.Transparent
    Box(
        modifier = modifier
            .size(18.dp)
            .clip(shape)
            .background(fill)
            .border(1.5.dp, border, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            TermText(
                text = "✓",
                color = TermColor.Fg,
                style = TermType.body.copy(
                    color = palette.onAccent,
                    fontFamily = MonoFont,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
        }
    }
}
