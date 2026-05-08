package com.juni.app.ui.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.juni.app.ui.theme.LocalPalette

/** A single row in [TermMenuSheet]. Pass a label and an optional sublabel/description. */
data class TermMenuItem(
    val key: String,
    val label: String,
    val description: String? = null,
)

/**
 * Bottom-anchored modal menu of choices, terminal-styled. Tap outside or on a
 * row to dismiss; selected row's [TermMenuItem.key] is delivered to [onPick].
 */
@Composable
fun TermMenuSheet(
    title: String,
    items: List<TermMenuItem>,
    onPick: (String) -> Unit,
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
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .clickable(
                    interactionSource = passthrough,
                    indication = null,
                    onClick = {},
                ),
        ) {
            TermBox(title = title, background = LocalPalette.current.surface) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items.forEach { item ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(
                                    interactionSource = remember(item.key) { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { onPick(item.key) },
                                )
                                .padding(vertical = 6.dp),
                        ) {
                            TermText(text = "▸ ${item.label}", color = TermColor.Accent)
                            item.description?.let {
                                TermText(text = "  $it", color = TermColor.Dim)
                            }
                        }
                    }
                }
            }
        }
    }
}
