package com.juni.app.domain.agent

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
enum class Role { USER, ASSISTANT }

@Serializable
sealed interface MessageContent {
    @Serializable
    @SerialName("text")
    data class Text(val text: String) : MessageContent

    /** Inline image, base64-encoded so it can be sent to any provider's vision API. */
    @Serializable
    @SerialName("image")
    data class Image(val mimeType: String, val base64: String) : MessageContent

    /** Assistant requested a tool call. */
    @Serializable
    @SerialName("tool_use")
    data class ToolUse(val id: String, val name: String, val input: JsonObject) : MessageContent

    /** Result the user (us, on the assistant's behalf) returns for a tool call. */
    @Serializable
    @SerialName("tool_result")
    data class ToolResult(
        val toolUseId: String,
        val content: String,
        val isError: Boolean = false,
    ) : MessageContent
}

@Serializable
data class Message(
    val role: Role,
    val content: List<MessageContent>,
) {
    companion object {
        fun userText(text: String) = Message(Role.USER, listOf(MessageContent.Text(text)))
        fun assistantText(text: String) = Message(Role.ASSISTANT, listOf(MessageContent.Text(text)))
    }
}
