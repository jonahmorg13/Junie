package com.juni.app.ui.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.juni.app.ui.theme.LocalPalette

/**
 * Modal confirmation dialog rendered in our terminal aesthetic. Dim
 * background; tap outside or [cancelLabel] to dismiss; [confirmLabel]
 * fires [onConfirm]. Default coloring is destructive (red confirm) since
 * that's our most common use case.
 */
@Composable
fun TermConfirm(
    title: String,
    message: String,
    confirmLabel: String = "confirm",
    cancelLabel: String = "cancel",
    confirmColor: TermColor = TermColor.Red,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scrim = remember { MutableInteractionSource() }
    val passthrough = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.65f))
            .clickable(
                interactionSource = scrim,
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        // Inner Box swallows clicks so tapping inside the dialog doesn't dismiss it.
        Box(
            modifier = Modifier
                .padding(24.dp)
                .clickable(
                    interactionSource = passthrough,
                    indication = null,
                    enabled = true,
                    onClick = {},
                ),
        ) {
            TermBox(title = title, background = LocalPalette.current.surface) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    TermText(text = message, color = TermColor.Fg)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TermButton(label = confirmLabel, color = confirmColor, onClick = onConfirm)
                        TermButton(label = cancelLabel, onClick = onDismiss)
                    }
                }
            }
        }
    }
}
