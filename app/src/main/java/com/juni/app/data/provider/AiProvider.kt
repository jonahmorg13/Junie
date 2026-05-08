package com.juni.app.data.provider

import com.juni.app.data.prefs.ProviderId
import com.juni.app.domain.agent.Message
import com.juni.app.domain.agent.StreamEvent
import com.juni.app.domain.tools.ToolSpec
import kotlinx.coroutines.flow.Flow

/**
 * One canonical AI streaming interface. Each implementation translates the
 * shared Message / ToolSpec model into its provider's wire format and emits
 * StreamEvents back as tokens arrive.
 */
interface AiProvider {
    val id: ProviderId

    fun streamTurn(
        systemPrompt: String?,
        messages: List<Message>,
        tools: List<ToolSpec>,
        model: String,
        maxTokens: Int = 4096,
    ): Flow<StreamEvent>
}
