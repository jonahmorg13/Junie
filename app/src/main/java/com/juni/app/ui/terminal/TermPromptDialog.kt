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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.juni.app.ui.theme.TermSurface

/** Modal text-input dialog. Same skin as TermConfirm but the body is a TermInput. */
@Composable
fun TermPromptDialog(
    title: String,
    initialValue: String,
    confirmLabel: String = "save",
    cancelLabel: String = "cancel",
    placeholder: String? = null,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember(initialValue) { mutableStateOf(initialValue) }
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
        Box(
            modifier = Modifier
                .padding(24.dp)
                .clickable(
                    interactionSource = passthrough,
                    indication = null,
                    onClick = {},
                ),
        ) {
            TermBox(title = title, background = TermSurface) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    TermInput(
                        value = draft,
                        onValueChange = { draft = it },
                        prompt = "  ",
                        placeholder = placeholder,
                        singleLine = true,
                        imeAction = ImeAction.Done,
                        onSubmit = { onConfirm(draft.trim()) },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TermButton(
                            label = confirmLabel,
                            color = TermColor.Green,
                            onClick = { onConfirm(draft.trim()) },
                        )
                        TermButton(label = cancelLabel, onClick = onDismiss)
                    }
                }
            }
        }
    }
}
