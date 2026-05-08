package com.juni.app.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
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

@Composable
fun ToolCallCard(
    item: ChatItem.ToolCall,
    onApprove: (String) -> Unit,
    onReject: (String) -> Unit,
    onOpenInObsidian: ((path: String) -> Unit)? = null,
) {
    val title = "tool · ${item.name}"

    TermBox(title = title) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                    val color = if (state.result.isError) TermColor.Red else TermColor.Dim
                    TermText(text = state.result.content.take(800), color = color)
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

/** For tools that produce a vault-relative .md path, return it so the chat can offer "open in obsidian". */
private fun openableNotePath(toolName: String, input: JsonObject): String? = when (toolName) {
    "create_note", "edit_note" -> input.string("path")?.takeIf { it.isNotEmpty() }
    "move_note" -> input.string("to")?.takeIf { it.isNotEmpty() }
    else -> null
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
    val question = input.string("question")

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
            "ask_clarifying_question" -> TermText(text = question.orEmpty(), color = TermColor.Accent)
            else -> TermText(text = input.toString(), color = TermColor.Dim)
        }
    }
}

@Composable
private fun DiffPreview(prefix: String, body: String, color: TermColor) {
    val lines = body.lineSequence().take(30).joinToString("\n") { "$prefix$it" }
    val truncated = if (body.lineSequence().count() > 30) "$lines\n…" else lines
    TermText(text = truncated, color = color)
}

private fun JsonObject.string(key: String): String? =
    get(key)?.jsonPrimitive?.contentOrNull
