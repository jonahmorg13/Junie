package com.juni.app.data.provider

import com.juni.app.data.prefs.ProviderId
import com.juni.app.domain.agent.Message
import com.juni.app.domain.agent.MessageContent
import com.juni.app.domain.agent.Role
import com.juni.app.domain.agent.StopReason
import com.juni.app.domain.agent.StreamEvent
import com.juni.app.domain.tools.ToolSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID

/**
 * Ollama provider against `${baseUrl}/api/chat`. Streaming format is NDJSON
 * (one JSON object per line) rather than SSE, so we manually pump the
 * response body line-by-line on a background dispatcher.
 *
 * Tool-calling requires an Ollama version with tool support and a model that
 * advertises it (e.g. `llama3.1`, `mistral`); vision requires a vision model
 * such as `llama3.2-vision` or `llava`.
 */
class OllamaProvider(private val baseUrl: String) : AiProvider {

    override val id = ProviderId.OLLAMA

    override fun streamTurn(
        systemPrompt: String?,
        messages: List<Message>,
        tools: List<ToolSpec>,
        model: String,
        maxTokens: Int,
    ): Flow<StreamEvent> = callbackFlow {
        val body = buildJsonObject {
            put("model", model)
            put("stream", true)
            put("messages", messages.toOllamaMessages(systemPrompt))
            if (tools.isNotEmpty()) {
                put("tools", buildJsonArray { tools.forEach { add(it.toOllamaTool()) } })
            }
            put(
                "options",
                buildJsonObject { put("num_predict", maxTokens) },
            )
        }

        val url = "${baseUrl.trimEnd('/')}/api/chat"
        val request = Request.Builder()
            .url(url)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val call = HttpClient.okhttp.newCall(request)

        val job = launch(Dispatchers.IO) {
            try {
                val response = call.execute()
                if (!response.isSuccessful) {
                    val errBody = runCatching { response.body?.string() }.getOrNull()
                    trySend(StreamEvent.Error("HTTP ${response.code}: ${errBody ?: response.message}"))
                    return@launch
                }
                val source = response.body?.source() ?: run {
                    trySend(StreamEvent.Error("Empty response body"))
                    return@launch
                }
                val streamedText = StringBuilder()
                while (isActive && !source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (line.isBlank()) continue
                    val payload = runCatching { HttpClient.json.parseToJsonElement(line).jsonObject }
                        .getOrNull() ?: continue

                    payload["message"]?.jsonObject?.let { message ->
                        message["content"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotEmpty() }
                            ?.let {
                                streamedText.append(it)
                                trySend(StreamEvent.TextDelta(it))
                            }
                        message["tool_calls"]?.jsonArray?.forEach { callElem ->
                            val fn = callElem.jsonObject["function"]?.jsonObject ?: return@forEach
                            val name = fn["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                            val args = fn["arguments"]?.jsonObject ?: buildJsonObject { }
                            // Ollama's tool calls don't carry ids; mint one so the agent loop
                            // can pair tool_use with tool_result later.
                            val callId = "ollama-${UUID.randomUUID()}"
                            trySend(StreamEvent.ToolCallEvent(callId, name, args))
                        }
                    }

                    val done = payload["done"]?.jsonPrimitive?.boolean ?: false
                    if (done) {
                        val reason = payload["done_reason"]?.jsonPrimitive?.contentOrNull
                        val stopReason = when (reason) {
                            "stop" -> StopReason.END_TURN
                            "length" -> StopReason.MAX_TOKENS
                            null -> StopReason.END_TURN
                            else -> StopReason.OTHER
                        }
                        trySend(StreamEvent.TurnEnd(stopReason))
                        break
                    }
                }
            } catch (t: Throwable) {
                if (isActive) trySend(StreamEvent.Error(t.message ?: "stream failed"))
            } finally {
                close()
            }
        }

        awaitClose {
            job.cancel()
            call.cancel()
        }
    }
}

// ---------- Message conversion ----------

private fun List<Message>.toOllamaMessages(systemPrompt: String?): JsonArray {
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
                    .joinToString("\n") { it.text }
                val images = msg.content.filterIsInstance<MessageContent.Image>()
                val toolResults = msg.content.filterIsInstance<MessageContent.ToolResult>()

                if (text.isNotEmpty() || images.isNotEmpty()) {
                    out += buildJsonObject {
                        put("role", "user")
                        put("content", text)
                        if (images.isNotEmpty()) {
                            put(
                                "images",
                                buildJsonArray { images.forEach { add(it.base64) } },
                            )
                        }
                    }
                }

                toolResults.forEach { tr ->
                    out += buildJsonObject {
                        put("role", "tool")
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
                    put("content", text)
                    if (toolUses.isNotEmpty()) {
                        put(
                            "tool_calls",
                            buildJsonArray {
                                toolUses.forEach { tu ->
                                    add(
                                        buildJsonObject {
                                            put(
                                                "function",
                                                buildJsonObject {
                                                    put("name", tu.name)
                                                    put("arguments", tu.input)
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

private fun ToolSpec.toOllamaTool(): JsonObject = buildJsonObject {
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
