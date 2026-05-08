package com.juni.app.ui.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.juni.app.ui.terminal.TermBox
import com.juni.app.ui.terminal.TermButton
import com.juni.app.ui.terminal.TermColor
import com.juni.app.ui.terminal.TermDivider
import com.juni.app.ui.terminal.TermInput
import com.juni.app.ui.terminal.TermSpinner
import com.juni.app.ui.terminal.TermText
import android.graphics.BitmapFactory

@Composable
fun ChatScreen(
    onBack: () -> Unit,
    onOpenCamera: () -> Unit,
) {
    val vm: ChatViewModel = viewModel()
    val ui by vm.ui.collectAsState()
    val pendingImages by vm.pendingImages.collectAsState()
    var draft by remember { mutableStateOf("") }
    val transcriptScroll = rememberScrollState()

    LaunchedEffect(ui.items.size, ui.streaming.length, ui.isStreaming, pendingImages.size) {
        transcriptScroll.scrollTo(transcriptScroll.maxValue)
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TermButton(label = "back", onClick = onBack)
            TermText(text = "juni", color = TermColor.Accent, bold = true)
            TermButton(label = "clear", onClick = { vm.clear() })
        }
        TermText(text = ui.statusLine, color = TermColor.Dim)
        TermDivider()

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(transcriptScroll)
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ui.items.forEach { item ->
                when (item) {
                    is ChatItem.UserMessage -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (item.imageCount > 0) {
                            TermText(
                                text = "[${item.imageCount} ${if (item.imageCount == 1) "image" else "images"}]",
                                color = TermColor.Muted,
                            )
                        }
                        if (item.text.isNotEmpty()) {
                            TermText(text = "❯ ${item.text}", color = TermColor.Accent)
                        }
                    }
                    is ChatItem.AssistantText ->
                        TermText(text = item.text, color = TermColor.Fg)
                    is ChatItem.ToolCall ->
                        ToolCallCard(
                            item = item,
                            onApprove = { vm.approve(it) },
                            onReject = { vm.reject(it) },
                        )
                    is ChatItem.SystemError ->
                        TermBox(title = "error") { TermText(text = item.text, color = TermColor.Red) }
                }
            }
            if (ui.streaming.isNotEmpty()) {
                TermText(text = ui.streaming, color = TermColor.Fg)
            } else if (ui.isStreaming) {
                TermSpinner(label = ui.thinkingWord)
            }
        }

        TermDivider()

        if (pendingImages.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            PendingImageStrip(
                images = pendingImages,
                onRemove = { vm.removePendingImage(it) },
            )
        }

        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TermInput(
                modifier = Modifier.weight(1f),
                value = draft,
                onValueChange = { draft = it },
                placeholder = if (ui.isStreaming) "streaming…" else "ask juni…",
                singleLine = false,
                imeAction = ImeAction.Send,
                onSubmit = {
                    val t = draft
                    if ((t.isNotBlank() || pendingImages.isNotEmpty()) && !ui.isStreaming) {
                        draft = ""
                        vm.send(t)
                    }
                },
            )
            TermButton(label = "cam", onClick = onOpenCamera, enabled = !ui.isStreaming)
            if (ui.isStreaming) {
                TermButton(label = "stop", color = TermColor.Red, onClick = { vm.stop() })
            } else {
                TermButton(
                    label = "send",
                    color = TermColor.Green,
                    onClick = {
                        val t = draft
                        if (t.isNotBlank() || pendingImages.isNotEmpty()) {
                            draft = ""
                            vm.send(t)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun PendingImageStrip(
    images: List<ByteArray>,
    onRemove: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        images.forEachIndexed { index, bytes ->
            val bitmap = remember(bytes) {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }
            Box {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                    )
                } else {
                    TermText(text = "[img]", color = TermColor.Muted)
                }
                Box(modifier = Modifier.align(Alignment.TopEnd)) {
                    TermButton(
                        label = "x",
                        color = TermColor.Red,
                        onClick = { onRemove(index) },
                    )
                }
            }
        }
    }
}
