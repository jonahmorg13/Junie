package com.juni.app.domain.agent

import com.juni.app.data.provider.AiProvider
import com.juni.app.domain.tools.ToolRegistry
import com.juni.app.domain.tools.ToolResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.serialization.json.JsonObject

/**
 * Drives the multi-turn agent: stream provider → collect text + tool calls →
 * gate each tool through [ApprovalGate] → execute → feed tool_result back
 * → repeat until the model says end_turn or we hit [maxIterations].
 *
 * Approval is a suspending callback rather than an event: the loop pauses
 * while it awaits the user's tap, the UI completes the gate, and the loop
 * resumes. This keeps the AgentEvent stream one-way and easy to render.
 */
typealias ApprovalGate = suspend (id: String, name: String, input: JsonObject) -> ApprovalResult

class AgentLoop(
    private val provider: AiProvider,
    private val tools: ToolRegistry,
    private val systemPrompt: String? = JUNI_SYSTEM_PROMPT,
    private val model: String,
    private val maxIterations: Int = 12,
) {
    fun run(
        initialMessages: List<Message>,
        approvalGate: ApprovalGate,
    ): Flow<AgentEvent> = channelFlow {
        val messages = initialMessages.toMutableList()
        var iteration = 0
        while (iteration < maxIterations) {
            iteration++

            val streamedText = StringBuilder()
            val pendingToolUses = mutableListOf<MessageContent.ToolUse>()
            var stopReason = StopReason.OTHER
            var streamErrored = false

            provider.streamTurn(
                systemPrompt = systemPrompt,
                messages = messages,
                tools = tools.specs,
                model = model,
            ).collect { event ->
                when (event) {
                    is StreamEvent.TextDelta -> {
                        streamedText.append(event.text)
                        send(AgentEvent.TextDelta(event.text))
                    }
                    is StreamEvent.ToolCallEvent ->
                        pendingToolUses += MessageContent.ToolUse(event.id, event.name, event.input)
                    is StreamEvent.TurnEnd -> stopReason = event.stopReason
                    is StreamEvent.Error -> {
                        send(AgentEvent.Error(event.message))
                        streamErrored = true
                    }
                }
            }

            if (streamErrored) return@channelFlow

            // Build the assistant message from text + tool uses, in order.
            val content = buildList<MessageContent> {
                if (streamedText.isNotEmpty()) add(MessageContent.Text(streamedText.toString()))
                addAll(pendingToolUses)
            }
            val assistantMessage = Message(Role.ASSISTANT, content)
            messages += assistantMessage
            send(AgentEvent.AssistantMessageDone(assistantMessage))

            if (pendingToolUses.isEmpty()) {
                send(AgentEvent.TurnComplete(stopReason))
                return@channelFlow
            }

            // Run each requested tool through the approval gate.
            val toolResults = mutableListOf<MessageContent>()
            for (call in pendingToolUses) {
                send(AgentEvent.PendingTool(call.id, call.name, call.input))
                val approval = approvalGate(call.id, call.name, call.input)
                when (approval) {
                    is ApprovalResult.Approve -> {
                        val tool = tools.find(call.name)
                        val result: ToolResult = if (tool == null) {
                            ToolResult("Unknown tool: ${call.name}.", isError = true)
                        } else {
                            runCatching { tool.execute(call.input) }.getOrElse { t ->
                                ToolResult(
                                    "Tool failed: ${t.message ?: t::class.simpleName}",
                                    isError = true,
                                )
                            }
                        }
                        send(AgentEvent.ToolExecuted(call.id, call.name, result))
                        toolResults += MessageContent.ToolResult(call.id, result.content, result.isError)
                    }
                    is ApprovalResult.Reject -> {
                        send(AgentEvent.ToolRejected(call.id, call.name, approval.reason))
                        val reason = approval.reason ?: "User rejected this tool call."
                        toolResults += MessageContent.ToolResult(call.id, reason, isError = true)
                    }
                }
            }

            // Send tool results back, then loop for the next turn.
            val toolResultsMessage = Message(Role.USER, toolResults)
            messages += toolResultsMessage
            send(AgentEvent.ToolResultsBatch(toolResultsMessage))
        }

        send(AgentEvent.Error("Agent loop hit the iteration cap ($maxIterations)."))
    }
}
