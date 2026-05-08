package com.juni.app.domain.tools

import kotlinx.serialization.json.JsonObject

/**
 * Provider-agnostic tool description. Each AiProvider knows how to translate this
 * into its own tool format (Anthropic tool_use, OpenAI tools, Gemini functionDeclarations).
 *
 * [inputSchema] is a JSON Schema object describing the tool's parameters.
 */
data class ToolSpec(
    val name: String,
    val description: String,
    val inputSchema: JsonObject,
)
