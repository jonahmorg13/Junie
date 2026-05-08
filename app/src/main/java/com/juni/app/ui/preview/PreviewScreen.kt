package com.juni.app.ui.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.juni.app.ui.terminal.TermBox
import com.juni.app.ui.terminal.TermButton
import com.juni.app.ui.terminal.TermColor
import com.juni.app.ui.terminal.TermDivider
import com.juni.app.ui.terminal.TermInput
import com.juni.app.ui.terminal.TermText

/**
 * Placeholder home screen exercising every terminal primitive so we can
 * eyeball spacing, color, and font rendering on a device. Replaced by the
 * Conversations + Chat screens later.
 */
@Composable
fun PreviewScreen(
    onOpenSettings: () -> Unit = {},
    onOpenVault: () -> Unit = {},
) {
    var typed by remember { mutableStateOf("") }
    var counter by remember { mutableStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TermText(text = "juni · ui preview", color = TermColor.Accent, bold = true)
            TermButton(label = "settings", onClick = onOpenSettings)
            TermButton(label = "vault", onClick = onOpenVault)
        }
        TermText(text = "every terminal primitive on one screen", color = TermColor.Dim)

        TermDivider()

        TermText(text = "TermText colors", color = TermColor.Accent)
        TermText(text = "fg     normal foreground", color = TermColor.Fg)
        TermText(text = "dim    deemphasized text", color = TermColor.Dim)
        TermText(text = "muted  borders, dividers", color = TermColor.Muted)
        TermText(text = "accent flame orange", color = TermColor.Accent)
        TermText(text = "green  added / approved", color = TermColor.Green)
        TermText(text = "red    removed / error", color = TermColor.Red)

        Spacer(Modifier.height(4.dp))
        TermText(text = "TermBox", color = TermColor.Accent)
        TermBox(title = "tool · create_note") {
            Column {
                TermText(text = "path: monarchs.md", color = TermColor.Dim)
                TermText(text = "+ # Monarch butterflies", color = TermColor.Green)
                TermText(text = "+ Monarchs migrate up to 3,000 miles…", color = TermColor.Green)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TermButton(label = "approve", color = TermColor.Green, onClick = { counter++ })
                    TermButton(label = "reject", color = TermColor.Red, onClick = { counter++ })
                    TermButton(label = "edit", onClick = { counter++ })
                }
            }
        }

        TermBox(title = null) {
            TermText(text = "untitled box for plain panels.", color = TermColor.Dim)
        }

        Spacer(Modifier.height(4.dp))
        TermText(text = "TermInput", color = TermColor.Accent)
        TermInput(
            value = typed,
            onValueChange = { typed = it },
            placeholder = "type something…",
            imeAction = ImeAction.Send,
            onSubmit = { typed = "" },
        )
        TermText(text = "you typed: \"$typed\"", color = TermColor.Dim)

        Spacer(Modifier.height(4.dp))
        TermText(text = "TermButton", color = TermColor.Accent)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TermButton(label = "tap me", onClick = { counter++ })
            TermButton(label = "disabled", onClick = { }, enabled = false)
            TermButton(label = "++", color = TermColor.Accent, onClick = { counter++ })
        }
        TermText(text = "press count: $counter", color = TermColor.Dim)

        Spacer(Modifier.height(4.dp))
        TermText(text = "TermDivider", color = TermColor.Accent)
        TermDivider()
        TermText(text = "above and below", color = TermColor.Dim)
        TermDivider()
    }
}
