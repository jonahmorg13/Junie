package com.juni.app.domain.agent

import com.juni.app.domain.tools.ToolResult
import kotlinx.serialization.json.JsonObject

/**
 * The single stream the chat UI subscribes to. Approval is *not* an event —
 * it goes through a suspending callback the caller provides to AgentLoop.run,
 * which keeps the channel one-way.
 */
sealed interface AgentEvent {
    /** A token chunk from the assistant text. */
    data class TextDelta(val text: String) : AgentEvent

    /** The assistant turn produced a complete message (text + any tool_uses). */
    data class AssistantMessageDone(val message: Message) : AgentEvent

    /**
     * The user-role message containing tool results from the iteration that
     * just finished. Emitted after every tool in the iteration has been
     * approved/rejected and executed, before the loop continues. The chat
     * persists this so future requests include the full tool-use/tool-result
     * pairs the API requires.
     */
    data class ToolResultsBatch(val message: Message) : AgentEvent

    /** A tool call is about to be approved. UI can show the card from this. */
    data class PendingTool(val id: String, val name: String, val input: JsonObject) : AgentEvent

    /** A tool finished — successfully or not. UI can replace the pending card with a result. */
    data class ToolExecuted(val id: String, val name: String, val result: ToolResult) : AgentEvent

    /** The user rejected this tool call. */
    data class ToolRejected(val id: String, val name: String, val reason: String?) : AgentEvent

    /** The agent loop finished a turn (model said end_turn) or hit an iteration cap. */
    data class TurnComplete(val stopReason: StopReason) : AgentEvent

    data class Error(val message: String) : AgentEvent
}

sealed interface ApprovalResult {
    data object Approve : ApprovalResult
    data class Reject(val reason: String? = null) : ApprovalResult
}
