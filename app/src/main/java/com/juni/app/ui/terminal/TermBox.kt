package com.juni.app.ui.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.juni.app.ui.theme.LocalPalette

/**
 * Bordered panel with rounded corners and an optional title rendered inside
 * the box as a small dim header. Pass [background] for floating elements
 * (toasts, dialogs); leave it null for inline cards that blend with the chat.
 */
@Composable
fun TermBox(
    modifier: Modifier = Modifier,
    title: String? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    background: Color? = null,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    val borderColor = LocalPalette.current.muted

    val outer = modifier
        .fillMaxWidth()
        .let { if (background != null) it.clip(shape).background(background) else it }
        .border(width = 1.dp, color = borderColor, shape = shape)
        .padding(contentPadding)

    Column(modifier = outer) {
        if (title != null) {
            TermText(text = title, color = TermColor.Dim)
            Spacer(Modifier.height(6.dp))
        }
        content()
    }
}
