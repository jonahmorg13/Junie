package com.juni.app.ui.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.juni.app.ui.theme.TermBg
import com.juni.app.ui.theme.TermMuted

/**
 * Bordered panel with rounded corners and an optional title that "cuts into"
 * the top border. The title's background matches the chat background
 * ([TermBg]), so it masks the border line behind it — producing the
 * `┌─ title ─┐` look without us having to draw a partial rect.
 */
@Composable
fun TermBox(
    modifier: Modifier = Modifier,
    title: String? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    content: @Composable () -> Unit,
) {
    val borderColor = TermMuted
    val cornerRadiusDp = 8.dp
    // Approximate half-height of TermType.body (14sp ≈ 18dp): pushes the bordered
    // area down so the title sits centered on the top stroke.
    val titleInset = 9.dp

    Box(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = if (title != null) titleInset else 0.dp)
                .drawBehind {
                    drawRoundRect(
                        color = borderColor,
                        cornerRadius = CornerRadius(cornerRadiusDp.toPx()),
                        style = Stroke(width = 1.dp.toPx()),
                    )
                }
                .padding(contentPadding),
        ) {
            content()
        }
        if (title != null) {
            Box(
                modifier = Modifier
                    .padding(start = 14.dp)
                    .background(TermBg)
                    .padding(horizontal = 6.dp),
            ) {
                TermText(text = title, color = TermColor.Dim)
            }
        }
    }
}
