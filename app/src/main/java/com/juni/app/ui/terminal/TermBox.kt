package com.juni.app.ui.terminal

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.juni.app.ui.theme.TermMuted

/**
 * A bordered box with an optional title that "cuts into" the top border,
 * mimicking a TUI panel. Uses a 1px stroke in the muted color.
 */
@Composable
fun TermBox(
    modifier: Modifier = Modifier,
    title: String? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    content: @Composable () -> Unit,
) {
    val borderColor = TermMuted
    val titleHeight: Dp = if (title != null) 8.dp else 0.dp

    Box(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(top = titleHeight)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .drawBehind {
                        val stroke = Stroke(width = 1.dp.toPx())
                        drawRect(
                            color = borderColor,
                            topLeft = Offset.Zero,
                            size = Size(size.width, size.height),
                            style = stroke,
                        )
                    }
                    .padding(contentPadding),
            ) {
                content()
            }
        }
        if (title != null) {
            Box(
                modifier = Modifier.padding(start = 12.dp),
            ) {
                TermText(
                    text = " $title ",
                    color = TermColor.Dim,
                )
            }
        }
    }
}
