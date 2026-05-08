package com.juni.app.ui.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.juni.app.ui.theme.LocalPalette
import com.juni.app.ui.theme.TermType

@Composable
fun TermButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: TermColor = TermColor.Fg,
    enabled: Boolean = true,
) {
    val palette = LocalPalette.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressedBg = palette.accent
    val pressedFg = palette.onAccent

    Box(
        modifier = modifier
            .background(if (pressed && enabled) pressedBg else Color.Transparent)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 4.dp, vertical = 2.dp),
    ) {
        if (pressed && enabled) {
            TermText(
                text = "[ $label ]",
                style = TermType.body.copy(color = pressedFg),
            )
        } else {
            TermText(
                text = "[ $label ]",
                color = if (enabled) color else TermColor.Muted,
            )
        }
    }
}
