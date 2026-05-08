package com.juni.app.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.juni.app.ui.terminal.TermBox
import com.juni.app.ui.terminal.TermButton
import com.juni.app.ui.terminal.TermColor
import com.juni.app.ui.terminal.TermSpinner
import com.juni.app.ui.terminal.TermText
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Read-only and chat-side tools that default to a single-line collapsed
 * row in the transcript. Write tools and any pending approval always
 * render fully expanded.
 */
private val DEFAULT_COLLAPSED = setOf(
    "list_files",
    "read_note",
    "search_notes",
    "ask_clarifying_question",
    "rename_chat",
)

@Composable
fun ToolCallCard(
    item: ChatItem.ToolCall,
    onApprove: (String) -> Unit,
    onReject: (String) -> Unit,
    onOpenInObsidian: ((path: String) -> Unit)? = null,
) {
    val initiallyExpanded = item.name !in DEFAULT_COLLAPSED
    var expanded by remember(item.id) { mutableStateOf(initiallyExpanded) }
    val pending = item.state is ToolState.Pending
    val effectiveExpanded = expanded || pending  // pending always shows controls

    val headerClick = remember(item.id) { MutableInteractionSource() }

    TermBox {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // Header row — click to toggle.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = headerClick,
                        indication = null,
                        enabled = !pending,
                        onClick = { expanded = !expanded },
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                TermText(
                    text = if (effectiveExpanded) "▾" else "▸",
                    color = TermColor.Dim,
                )
                TermText(text = "tool · ${item.name}", color = TermColor.Dim)
                Spacer(Modifier.weight(1f, fill = false))
                TermText(
                    text = headerSummary(item),
                    color = headerColor(item),
                )
            }

            if (effectiveExpanded) {
                ToolInputPreview(name = item.name, input = item.input)
                Spacer(Modifier.height(2.dp))
                when (val state = item.state) {
                    is ToolState.Pending -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TermButton(
                                label = "approve",
                                color = TermColor.Green,
                                onClick = { onApprove(item.id) },
                            )
                            TermButton(
                                label = "reject",
                                color = TermColor.Red,
                                onClick = { onReject(item.id) },
                            )
                        }
                    }
                    is ToolState.Running -> TermSpinner(label = "running ${item.name}")
                    is ToolState.Done -> {
                        // ask_clarifying_question's result echoes the question back; the
                        // accompanying assistant text already says it, so suppress here.
                        if (item.name != "ask_clarifying_question") {
                            val color = if (state.result.isError) TermColor.Red else TermColor.Dim
                            TermText(text = state.result.content.take(800), color = color)
                        }
                        val openablePath = if (!state.result.isError) {
                            openableNotePath(item.name, item.input)
                        } else null
                        if (openablePath != null && onOpenInObsidian != null) {
                            Spacer(Modifier.height(2.dp))
                            TermButton(
                                label = "open in obsidian",
                                color = TermColor.Accent,
                                onClick = { onOpenInObsidian(openablePath) },
                            )
                        }
                    }
                    is ToolState.Rejected -> {
                        TermText(
                            text = "rejected" + (state.reason?.let { ": $it" } ?: ""),
                            color = TermColor.Red,
                        )
                    }
                }
            }
        }
    }
}

/** Tail-end summary that goes on the collapsed header row. */
private fun headerSummary(item: ChatItem.ToolCall): String {
    val short = when (item.name) {
        "list_files" -> "path: \"${item.input.string("path").orEmpty()}\""
        "read_note" -> item.input.string("path").orEmpty()
        "search_notes" -> "\"${item.input.string("query").orEmpty()}\""
        "create_note", "edit_note" -> item.input.string("path").orEmpty()
        "move_note" -> "${item.input.string("from").orEmpty()} → ${item.input.string("to").orEmpty()}"
        "save_attachment" -> item.input.string("filename").orEmpty()
        "rename_chat" -> "→ \"${item.input.string("title").orEmpty()}\""
        else -> ""
    }
    return when (val state = item.state) {
        is ToolState.Pending -> short
        is ToolState.Running -> short
        is ToolState.Done -> if (state.result.isError) "✗ $short" else "✓ $short"
        is ToolState.Rejected -> "✗ $short"
    }
}

private fun headerColor(item: ChatItem.ToolCall): TermColor = when (val s = item.state) {
    is ToolState.Pending -> TermColor.Accent
    is ToolState.Running -> TermColor.Accent
    is ToolState.Done -> if (s.result.isError) TermColor.Red else TermColor.Green
    is ToolState.Rejected -> TermColor.Red
}

@Composable
private fun ToolInputPreview(name: String, input: JsonObject) {
    val path = input.string("path")
    val content = input.string("content")
    val find = input.string("find")
    val replace = input.string("replace")
    val from = input.string("from")
    val to = input.string("to")
    val query = input.string("query")
    val filename = input.string("filename")
    val title = input.string("title")

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        when (name) {
            "create_note" -> {
                TermText(text = "path: ${path.orEmpty()}", color = TermColor.Dim)
                content?.let { DiffPreview(prefix = "+ ", body = it, color = TermColor.Green) }
            }
            "edit_note" -> {
                TermText(text = "path: ${path.orEmpty()}", color = TermColor.Dim)
                find?.let { DiffPreview(prefix = "- ", body = it, color = TermColor.Red) }
                replace?.let { DiffPreview(prefix = "+ ", body = it, color = TermColor.Green) }
            }
            "move_note" -> TermText(
                text = "${from.orEmpty()} → ${to.orEmpty()}",
                color = TermColor.Dim,
            )
            "save_attachment" -> TermText(
                text = "filename: ${filename.orEmpty()}",
                color = TermColor.Dim,
            )
            "search_notes" -> TermText(text = "query: \"${query.orEmpty()}\"", color = TermColor.Dim)
            "list_files" -> TermText(text = "path: \"${path.orEmpty()}\"", color = TermColor.Dim)
            "read_note" -> TermText(text = "path: ${path.orEmpty()}", color = TermColor.Dim)
            "ask_clarifying_question" -> {
                // Question text is shown in the assistant's accompanying text; render nothing here.
            }
            "rename_chat" -> TermText(text = "→ \"${title.orEmpty()}\"", color = TermColor.Dim)
            else -> {
                input.entries.forEach { (k, v) ->
                    val pretty = v.jsonPrimitive.contentOrNull ?: v.toString()
                    TermText(text = "$k: $pretty", color = TermColor.Dim)
                }
            }
        }
    }
}

@Composable
private fun DiffPreview(prefix: String, body: String, color: TermColor) {
    val lines = body.lineSequence().take(30).joinToString("\n") { "$prefix$it" }
    val truncated = if (body.lineSequence().count() > 30) "$lines\n…" else lines
    TermText(text = truncated, color = color)
}

private fun openableNotePath(toolName: String, input: JsonObject): String? = when (toolName) {
    "create_note", "edit_note" -> input.string("path")?.takeIf { it.isNotEmpty() }
    "move_note" -> input.string("to")?.takeIf { it.isNotEmpty() }
    else -> null
}

private fun JsonObject.string(key: String): String? =
    get(key)?.jsonPrimitive?.contentOrNull
