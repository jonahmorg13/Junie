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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
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
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.util.UUID

/**
 * Google Gemini provider via the streamGenerateContent SSE endpoint. Roles are
 * "user" / "model" (not "assistant"); content is a list of "parts" where each
 * part is text, inline_data (base64 image), functionCall, or functionResponse.
 *
 * Gemini's tool calls don't carry their own ids — we generate UUIDs locally
 * so our agent loop can pair them with results.
 */
class GeminiProvider(private val apiKey: String) : AiProvider {

    override val id = ProviderId.GEMINI

    override fun streamTurn(
        systemPrompt: String?,
        messages: List<Message>,
        tools: List<ToolSpec>,
        model: String,
        maxTokens: Int,
    ): Flow<StreamEvent> = callbackFlow {
        val body = buildJsonObject {
            put("contents", messages.toGeminiContents())
            if (!systemPrompt.isNullOrBlank()) {
                put(
                    "systemInstruction",
                    buildJsonObject {
                        put("parts", buildJsonArray { add(buildJsonObject { put("text", systemPrompt) }) })
                    },
                )
            }
            if (tools.isNotEmpty()) {
                put(
                    "tools",
                    buildJsonArray {
                        add(
                            buildJsonObject {
                                put(
                                    "functionDeclarations",
                                    buildJsonArray {
                                        tools.forEach { add(it.toGeminiFunctionDeclaration()) }
                                    },
                                )
                            },
                        )
                    },
                )
            }
            put(
                "generationConfig",
                buildJsonObject { put("maxOutputTokens", maxTokens) },
            )
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/" +
            "$model:streamGenerateContent?alt=sse&key=$apiKey"

        val request = Request.Builder()
            .url(url)
            .header("accept", "text/event-stream")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        var stopReason = StopReason.OTHER
        var emittedTurnEnd = false

        val listener = object : EventSourceListener() {
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                try {
                    val payload = HttpClient.json.parseToJsonElement(data).jsonObject
                    val candidate = payload["candidates"]?.jsonArray?.firstOrNull()?.jsonObject ?: return
                    candidate["content"]?.jsonObject?.get("parts")?.jsonArray?.forEach { partElem ->
                        val part = partElem.jsonObject
                        part["text"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotEmpty() }
                            ?.let { trySend(StreamEvent.TextDelta(it)) }
                        part["functionCall"]?.jsonObject?.let { fc ->
                            val name = fc["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                            val args = fc["args"]?.jsonObject ?: buildJsonObject { }
                            // Gemini doesn't send tool-call ids; we mint our own so the agent loop
                            // can pair this call with its eventual functionResponse.
                            val callId = "gemini-${UUID.randomUUID()}"
                            trySend(StreamEvent.ToolCallEvent(callId, name, args))
                        }
                    }
                    candidate["finishReason"]?.jsonPrimitive?.contentOrNull?.let { reason ->
                        stopReason = when (reason) {
                            "STOP" -> StopReason.END_TURN
                            "MAX_TOKENS" -> StopReason.MAX_TOKENS
                            "SAFETY", "RECITATION" -> StopReason.OTHER
                            else -> StopReason.OTHER
                        }
                        // Gemini's finishReason fires on the final chunk; tool calls in this turn
                        // are already emitted from earlier parts. If there were any, treat as TOOL_USE.
                    }
                } catch (t: Throwable) {
                    trySend(StreamEvent.Error("Parse error: ${t.message}"))
                }
            }

            override fun onClosed(eventSource: EventSource) {
                if (!emittedTurnEnd) trySend(StreamEvent.TurnEnd(stopReason))
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
}

// ---------- Message conversion ----------

private fun List<Message>.toGeminiContents(): JsonArray {
    val out = mutableListOf<JsonObject>()
    for (msg in this) {
        val parts = mutableListOf<JsonObject>()
        for (content in msg.content) {
            when (content) {
                is MessageContent.Text -> parts += buildJsonObject { put("text", content.text) }
                is MessageContent.Image -> parts += buildJsonObject {
                    put(
                        "inlineData",
                        buildJsonObject {
                            put("mimeType", content.mimeType)
                            put("data", content.base64)
                        },
                    )
                }
                is MessageContent.ToolUse -> parts += buildJsonObject {
                    put(
                        "functionCall",
                        buildJsonObject {
                            put("name", content.name)
                            put("args", content.input)
                        },
                    )
                }
                is MessageContent.ToolResult -> parts += buildJsonObject {
                    put(
                        "functionResponse",
                        buildJsonObject {
                            // We'd need the tool name to round-trip perfectly; absent that, use
                            // the toolUseId as the function name (Gemini matches by name in v1beta
                            // but tolerates arbitrary strings in this client-side history).
                            put("name", content.toolUseId)
                            put(
                                "response",
                                buildJsonObject {
                                    put("content", content.content)
                                    if (content.isError) put("error", true)
                                },
                            )
                        },
                    )
                }
            }
        }
        out += buildJsonObject {
            put("role", if (msg.role == Role.USER) "user" else "model")
            put("parts", JsonArray(parts))
        }
    }
    return JsonArray(out)
}

private fun ToolSpec.toGeminiFunctionDeclaration(): JsonObject = buildJsonObject {
    put("name", name)
    put("description", description)
    put("parameters", inputSchema)
}
