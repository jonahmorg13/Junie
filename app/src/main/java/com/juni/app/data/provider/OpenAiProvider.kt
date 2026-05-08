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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
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

/**
 * OpenAI Chat Completions provider. Translates the canonical Message/ToolSpec
 * model into OpenAI's wire format (which differs from Anthropic's mainly in
 * how tool calls are attached to assistant messages and how tool results are
 * sent back as their own `tool`-role messages).
 */
class OpenAiProvider(private val apiKey: String) : AiProvider {

    override val id = ProviderId.OPENAI

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
            put("messages", messages.toOpenAiMessages(systemPrompt))
            if (tools.isNotEmpty()) {
                put("tools", buildJsonArray { tools.forEach { add(it.toOpenAiTool()) } })
            }
        }

        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .header("authorization", "Bearer $apiKey")
            .header("accept", "text/event-stream")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        // Tool calls stream in fragments keyed by an index — accumulate name + JSON arguments.
        val toolAccum = mutableMapOf<Int, ToolCallAccum>()
        var stopReason = StopReason.OTHER
        var emittedTurnEnd = false

        val listener = object : EventSourceListener() {
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                if (data == "[DONE]") return
                try {
                    val payload = HttpClient.json.parseToJsonElement(data).jsonObject
                    val choices = payload["choices"]?.jsonArray ?: return
                    val choice = choices.firstOrNull()?.jsonObject ?: return

                    choice["delta"]?.jsonObject?.let { delta ->
                        delta["content"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotEmpty() }
                            ?.let { trySend(StreamEvent.TextDelta(it)) }

                        delta["tool_calls"]?.jsonArray?.forEach { callElem ->
                            val call = callElem.jsonObject
                            val index = call["index"]?.jsonPrimitive?.intOrNull ?: 0
                            val accum = toolAccum.getOrPut(index) { ToolCallAccum() }
                            call["id"]?.jsonPrimitive?.contentOrNull?.let { accum.id = it }
                            call["function"]?.jsonObject?.let { fn ->
                                fn["name"]?.jsonPrimitive?.contentOrNull?.let { accum.name = it }
                                fn["arguments"]?.jsonPrimitive?.contentOrNull?.let { accum.args.append(it) }
                            }
                        }
                    }

                    choice["finish_reason"]?.jsonPrimitive?.contentOrNull?.let { reason ->
                        stopReason = when (reason) {
                            "stop" -> StopReason.END_TURN
                            "tool_calls" -> StopReason.TOOL_USE
                            "length" -> StopReason.MAX_TOKENS
                            else -> StopReason.OTHER
                        }
                        // Flush any accumulated tool calls before the turn ends.
                        toolAccum.values.sortedBy { it.id }.forEach { accum ->
                            val parsed = runCatching {
                                HttpClient.json.parseToJsonElement(accum.args.toString().ifBlank { "{}" }).jsonObject
                            }.getOrElse { buildJsonObject { } }
                            trySend(StreamEvent.ToolCallEvent(accum.id, accum.name, parsed))
                        }
                        toolAccum.clear()
                        trySend(StreamEvent.TurnEnd(stopReason))
                        emittedTurnEnd = true
                    }
                } catch (t: Throwable) {
                    trySend(StreamEvent.Error("Parse error: ${t.message}"))
                }
            }

            override fun onClosed(eventSource: EventSource) {
                if (!emittedTurnEnd) trySend(StreamEvent.TurnEnd(StopReason.OTHER))
                close()
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
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

    private class ToolCallAccum(
        var id: String = "",
        var name: String = "",
        val args: StringBuilder = StringBuilder(),
    )
}

// ---------- Message conversion ----------

private fun List<Message>.toOpenAiMessages(systemPrompt: String?): JsonArray {
    val out = mutableListOf<JsonObject>()
    if (!systemPrompt.isNullOrBlank()) {
        out += buildJsonObject {
            put("role", "system")
            put("content", systemPrompt)
        }
    }
    for (msg in this) {
        when (msg.role) {
            Role.USER -> {
                val text = msg.content.filterIsInstance<MessageContent.Text>()
                val images = msg.content.filterIsInstance<MessageContent.Image>()
                val toolResults = msg.content.filterIsInstance<MessageContent.ToolResult>()

                if (text.isNotEmpty() || images.isNotEmpty()) {
                    if (images.isEmpty() && text.size == 1) {
                        out += buildJsonObject {
                            put("role", "user")
                            put("content", text.first().text)
                        }
                    } else {
                        out += buildJsonObject {
                            put("role", "user")
                            put(
                                "content",
                                buildJsonArray {
                                    text.forEach {
                                        add(
                                            buildJsonObject {
                                                put("type", "text")
                                                put("text", it.text)
                                            },
                                        )
                                    }
                                    images.forEach { img ->
                                        add(
                                            buildJsonObject {
                                                put("type", "image_url")
                                                put(
                                                    "image_url",
                                                    buildJsonObject {
                                                        put("url", "data:${img.mimeType};base64,${img.base64}")
                                                    },
                                                )
                                            },
                                        )
                                    }
                                },
                            )
                        }
                    }
                }

                // Each tool_result becomes its own tool-role message in OpenAI's format.
                toolResults.forEach { tr ->
                    out += buildJsonObject {
                        put("role", "tool")
                        put("tool_call_id", tr.toolUseId)
                        put("content", tr.content)
                    }
                }
            }
            Role.ASSISTANT -> {
                val text = msg.content.filterIsInstance<MessageContent.Text>()
                    .joinToString("\n") { it.text }
                val toolUses = msg.content.filterIsInstance<MessageContent.ToolUse>()

                out += buildJsonObject {
                    put("role", "assistant")
                    if (text.isNotEmpty()) {
                        put("content", text)
                    } else {
                        put("content", JsonNull)
                    }
                    if (toolUses.isNotEmpty()) {
                        put(
                            "tool_calls",
                            buildJsonArray {
                                toolUses.forEach { tu ->
                                    add(
                                        buildJsonObject {
                                            put("id", tu.id)
                                            put("type", "function")
                                            put(
                                                "function",
                                                buildJsonObject {
                                                    put("name", tu.name)
                                                    // OpenAI expects arguments as a JSON-encoded string, not an object.
                                                    put("arguments", tu.input.toString())
                                                },
                                            )
                                        },
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }
    }
    return JsonArray(out)
}

private fun ToolSpec.toOpenAiTool(): JsonObject = buildJsonObject {
    put("type", "function")
    put(
        "function",
        buildJsonObject {
            put("name", name)
            put("description", description)
            put("parameters", inputSchema)
        },
    )
}
