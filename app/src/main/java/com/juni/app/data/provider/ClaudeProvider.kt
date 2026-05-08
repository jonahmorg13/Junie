package com.juni.app.data.provider

import com.juni.app.data.prefs.ProviderId
import com.juni.app.domain.agent.Message
import com.juni.app.domain.agent.MessageContent
import com.juni.app.domain.agent.Role
import com.juni.app.domain.agent.StopReason
import com.juni.app.domain.agent.StreamEvent
import com.juni.app.domain.tools.ToolSpec
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources

class ClaudeProvider(private val apiKey: String) : AiProvider {

    override val id = ProviderId.CLAUDE

    override fun streamTurn(
        systemPrompt: String?,
        messages: List<Message>,
        tools: List<ToolSpec>,
        model: String,
        maxTokens: Int,
    ): Flow<StreamEvent> = callbackFlow {
        val body = buildJsonObject {
            put("model", model)
            put("max_tokens", maxTokens)
            put("stream", true)
            systemPrompt?.takeIf { it.isNotBlank() }?.let { put("system", it) }
            put("messages", buildJsonArray { messages.forEach { add(it.toClaudeJson()) } })
            if (tools.isNotEmpty()) {
                put("tools", buildJsonArray { tools.forEach { add(it.toClaudeJson()) } })
            }
        }

        val request = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .header("accept", "text/event-stream")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val blocks = mutableMapOf<Int, BlockState>()
        var emittedTurnEnd = false

        val listener = object : EventSourceListener() {
            override fun onEvent(
                eventSource: EventSource,
                id: String?,
                type: String?,
                data: String,
            ) {
                try {
                    val payload = HttpClient.json.parseToJsonElement(data).jsonObject
                    when (type) {
                        "content_block_start" -> handleBlockStart(payload, blocks)
                        "content_block_delta" -> handleBlockDelta(payload, blocks) { ev -> trySend(ev) }
                        "content_block_stop" -> handleBlockStop(payload, blocks) { ev -> trySend(ev) }
                        "message_delta" -> {
                            val stop = payload["delta"]?.jsonObject?.get("stop_reason")?.jsonPrimitive?.contentOrNull
                            val reason = mapStopReason(stop)
                            trySend(StreamEvent.TurnEnd(reason))
                            emittedTurnEnd = true
                        }
                        "message_stop" -> { /* connection will close shortly */ }
                        "error" -> {
                            val msg = payload["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
                                ?: data
                            trySend(StreamEvent.Error(msg))
                        }
                        else -> { /* ignore message_start, ping */ }
                    }
                } catch (t: Throwable) {
                    trySend(StreamEvent.Error("Parse error: ${t.message}"))
                }
            }

            override fun onClosed(eventSource: EventSource) {
                if (!emittedTurnEnd) trySend(StreamEvent.TurnEnd(StopReason.OTHER))
                close()
            }

            override fun onFailure(
                eventSource: EventSource,
                t: Throwable?,
                response: Response?,
            ) {
                val errBody = runCatching { response?.body?.string() }.getOrNull()
                val msg = buildString {
                    if (response != null) append("HTTP ${response.code}: ")
                    append(errBody ?: t?.message ?: "stream failed")
                }
                trySend(StreamEvent.Error(msg))
                close(t)
            }
        }

        val es = EventSources.createFactory(HttpClient.okhttp).newEventSource(request, listener)
        awaitClose { es.cancel() }
    }

    // ---------- streaming state ----------

    private sealed class BlockState {
        object Text : BlockState()
        data class ToolUse(
            val id: String,
            val name: String,
            val jsonBuf: StringBuilder = StringBuilder(),
        ) : BlockState()
    }

    private fun handleBlockStart(payload: JsonObject, blocks: MutableMap<Int, BlockState>) {
        val index = payload["index"]?.jsonPrimitive?.intOrNull ?: return
        val cb = payload["content_block"]?.jsonObject ?: return
        when (cb["type"]?.jsonPrimitive?.contentOrNull) {
            "text" -> blocks[index] = BlockState.Text
            "tool_use" -> {
                val id = cb["id"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val name = cb["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                blocks[index] = BlockState.ToolUse(id, name)
            }
        }
    }

    private fun handleBlockDelta(
        payload: JsonObject,
        blocks: MutableMap<Int, BlockState>,
        emit: (StreamEvent) -> Unit,
    ) {
        val index = payload["index"]?.jsonPrimitive?.intOrNull ?: return
        val delta = payload["delta"]?.jsonObject ?: return
        when (delta["type"]?.jsonPrimitive?.contentOrNull) {
            "text_delta" -> {
                val text = delta["text"]?.jsonPrimitive?.contentOrNull ?: return
                emit(StreamEvent.TextDelta(text))
            }
            "input_json_delta" -> {
                val state = blocks[index] as? BlockState.ToolUse ?: return
                val partial = delta["partial_json"]?.jsonPrimitive?.contentOrNull ?: return
                state.jsonBuf.append(partial)
            }
        }
    }

    private fun handleBlockStop(
        payload: JsonObject,
        blocks: MutableMap<Int, BlockState>,
        emit: (StreamEvent) -> Unit,
    ) {
        val index = payload["index"]?.jsonPrimitive?.intOrNull ?: return
        val state = blocks.remove(index) ?: return
        if (state is BlockState.ToolUse) {
            val raw = state.jsonBuf.toString().ifBlank { "{}" }
            val input = runCatching { HttpClient.json.parseToJsonElement(raw).jsonObject }
                .getOrElse { buildJsonObject { } }
            emit(StreamEvent.ToolCallEvent(state.id, state.name, input))
        }
    }

    private fun mapStopReason(raw: String?): StopReason = when (raw) {
        "end_turn", "stop_sequence" -> StopReason.END_TURN
        "tool_use" -> StopReason.TOOL_USE
        "max_tokens" -> StopReason.MAX_TOKENS
        else -> StopReason.OTHER
    }

    // ---------- request shaping ----------

    private fun Message.toClaudeJson(): JsonObject = buildJsonObject {
        put("role", if (role == Role.USER) "user" else "assistant")
        val onlyText = content.singleOrNull() as? MessageContent.Text
        if (onlyText != null) {
            put("content", onlyText.text)
        } else {
            put("content", buildJsonArray { content.forEach { add(it.toClaudeBlock()) } })
        }
    }

    private fun MessageContent.toClaudeBlock(): JsonObject = when (this) {
        is MessageContent.Text -> buildJsonObject {
            put("type", "text")
            put("text", text)
        }
        is MessageContent.Image -> buildJsonObject {
            put("type", "image")
            put(
                "source",
                buildJsonObject {
                    put("type", "base64")
                    put("media_type", mimeType)
                    put("data", base64)
                },
            )
        }
        is MessageContent.ToolUse -> buildJsonObject {
            put("type", "tool_use")
            put("id", id)
            put("name", name)
            put("input", input)
        }
        is MessageContent.ToolResult -> buildJsonObject {
            put("type", "tool_result")
            put("tool_use_id", toolUseId)
            put("content", content)
            if (isError) put("is_error", JsonPrimitive(true))
        }
    }

    private fun ToolSpec.toClaudeJson(): JsonObject = buildJsonObject {
        put("name", name)
        put("description", description)
        put("input_schema", inputSchema)
    }
}
