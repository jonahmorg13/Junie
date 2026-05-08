package com.juni.app.domain.agent

import kotlinx.serialization.json.JsonObject

enum class StopReason { END_TURN, TOOL_USE, MAX_TOKENS, OTHER }

sealed interface StreamEvent {
    data class TextDelta(val text: String) : StreamEvent

    /** A complete tool call after streaming the input JSON has finished. */
    data class ToolCallEvent(val id: String, val name: String, val input: JsonObject) : StreamEvent

    /** The assistant turn finished. Look at [stopReason] to decide whether to loop. */
    data class TurnEnd(val stopReason: StopReason) : StreamEvent

    data class Error(val message: String) : StreamEvent
}
