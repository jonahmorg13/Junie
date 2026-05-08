package com.juni.app.ui.chat

import com.juni.app.domain.tools.ToolResult
import kotlinx.serialization.json.JsonObject

/** What the chat transcript renders. Distinct from canonical [com.juni.app.domain.agent.Message]. */
sealed interface ChatItem {
    data class UserMessage(val text: String, val imageCount: Int = 0) : ChatItem
    data class AssistantText(val text: String) : ChatItem
    data class ToolCall(
        val id: String,
        val name: String,
        val input: JsonObject,
        val state: ToolState,
    ) : ChatItem
    data class SystemError(val text: String) : ChatItem
}

sealed interface ToolState {
    data object Pending : ToolState
    data object Running : ToolState
    data class Done(val result: ToolResult) : ToolState
    data class Rejected(val reason: String?) : ToolState
}
