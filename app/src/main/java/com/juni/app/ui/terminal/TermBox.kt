package com.juni.app.ui.terminal

import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.juni.app.ui.theme.TermMuted

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
    val cornerRadiusDp = 8.dp
    val shape = RoundedCornerShape(cornerRadiusDp)

    val outer = modifier
        .fillMaxWidth()
        .let { if (background != null) it.clip(shape).background(background) else it }
        .drawBehind {
            drawRoundRect(
                color = TermMuted,
                cornerRadius = CornerRadius(cornerRadiusDp.toPx()),
                style = Stroke(width = 1.dp.toPx()),
            )
        }
        .padding(contentPadding)

    Column(modifier = outer) {
        if (title != null) {
            TermText(text = title, color = TermColor.Dim)
            Spacer(Modifier.height(6.dp))
        }
        content()
    }
}
