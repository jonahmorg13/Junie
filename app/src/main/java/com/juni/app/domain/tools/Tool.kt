package com.juni.app.domain.tools

import kotlinx.serialization.json.JsonObject

/**
 * A tool the agent can call. Each tool owns its own [ToolSpec] (which
 * the provider translates into the per-API tool format) and an [execute]
 * coroutine that performs the actual side effect.
 */
interface Tool {
    val spec: ToolSpec
    suspend fun execute(input: JsonObject): ToolResult
}

data class ToolResult(
    val content: String,
    val isError: Boolean = false,
)
