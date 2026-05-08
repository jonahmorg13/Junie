package com.juni.app.domain.agent

import kotlinx.serialization.json.JsonObject

enum class Role { USER, ASSISTANT }

sealed interface MessageContent {
    data class Text(val text: String) : MessageContent

    /** Inline image, base64-encoded so it can be sent to any provider's vision API. */
    data class Image(val mimeType: String, val base64: String) : MessageContent

    /** Assistant requested a tool call. */
    data class ToolUse(val id: String, val name: String, val input: JsonObject) : MessageContent

    /** Result the user (us, on the assistant's behalf) returns for a tool call. */
    data class ToolResult(
        val toolUseId: String,
        val content: String,
        val isError: Boolean = false,
    ) : MessageContent
}

data class Message(
    val role: Role,
    val content: List<MessageContent>,
) {
    companion object {
        fun userText(text: String) = Message(Role.USER, listOf(MessageContent.Text(text)))
        fun assistantText(text: String) = Message(Role.ASSISTANT, listOf(MessageContent.Text(text)))
    }
}
