package com.juni.app.ui.chatdebug

import android.net.Uri
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.juni.app.JuniApp
import com.juni.app.data.prefs.ProviderId
import com.juni.app.data.provider.ClaudeProvider
import com.juni.app.data.vault.VaultRepository
import com.juni.app.domain.agent.AgentEvent
import com.juni.app.domain.agent.AgentLoop
import com.juni.app.domain.agent.ApprovalResult
import com.juni.app.domain.agent.Message
import com.juni.app.domain.tools.AttachmentStaging
import com.juni.app.domain.tools.ToolRegistry
import com.juni.app.domain.tools.VaultTools
import com.juni.app.ui.terminal.TermBox
import com.juni.app.ui.terminal.TermButton
import com.juni.app.ui.terminal.TermColor
import com.juni.app.ui.terminal.TermDivider
import com.juni.app.ui.terminal.TermInput
import com.juni.app.ui.terminal.TermSpinner
import com.juni.app.ui.terminal.TermText
import com.juni.app.ui.terminal.randomThinkingWord
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class Turn(val role: String, val text: String, val isError: Boolean = false)

data class ChatDebugUi(
    val turns: List<Turn> = emptyList(),
    val streaming: String = "",
    val isStreaming: Boolean = false,
    val statusLine: String = "",
    val thinkingWord: String = "thinking",
)

class ChatDebugViewModel : ViewModel() {

    private val app = JuniApp.get()
    private val _ui = MutableStateFlow(ChatDebugUi())
    val ui: StateFlow<ChatDebugUi> = _ui.asStateFlow()

    /**
     * Canonical conversation history sent to the API on every turn. Includes
     * assistant messages (text + tool_use blocks) and the user-role
     * tool_result batches that follow them — Claude's API requires the pairs.
     */
    private val history: MutableList<Message> = mutableListOf()

    private var currentJob: Job? = null

    init {
        viewModelScope.launch {
            val s = app.appSettings.flow.first()
            _ui.value = _ui.value.copy(
                statusLine = "${s.providerId.label} · ${s.modelByProvider[s.providerId]}",
            )
        }
    }

    fun send(input: String) {
        val text = input.trim()
        if (text.isEmpty() || _ui.value.isStreaming) return

        currentJob = viewModelScope.launch {
            val settings = app.appSettings.flow.first()
            val provider = settings.providerId
            val key = app.securePrefs.apiKey(provider)
            if (provider != ProviderId.CLAUDE) {
                appendTurn(Turn("system", "Only Claude is wired up so far.", isError = true))
                return@launch
            }
            if (key.isBlank()) {
                appendTurn(Turn("system", "No Claude API key set. Add one in Settings.", isError = true))
                return@launch
            }

            val userMessage = Message.userText(text)
            history += userMessage
            appendTurn(Turn("user", text))
            _ui.value = _ui.value.copy(
                streaming = "",
                isStreaming = true,
                thinkingWord = randomThinkingWord(),
            )

            try {
                runAgent(
                    claude = ClaudeProvider(key),
                    model = settings.modelByProvider[ProviderId.CLAUDE].orEmpty(),
                    vaultUri = settings.vaultUri,
                )
            } catch (t: Throwable) {
                appendTurn(Turn("system", t.message ?: "stream failed", isError = true))
                _ui.value = _ui.value.copy(streaming = "", isStreaming = false)
            }
        }
    }

    private suspend fun runAgent(
        claude: ClaudeProvider,
        model: String,
        vaultUri: String?,
    ) {
        val tools = if (vaultUri != null) {
            val vault = VaultRepository(app, Uri.parse(vaultUri))
            ToolRegistry(VaultTools.all(vault, AttachmentStaging()))
        } else {
            ToolRegistry(emptyList())
        }
        val loop = AgentLoop(provider = claude, tools = tools, model = model)
        val streamed = StringBuilder()

        loop.run(
            initialMessages = history.toList(),
            approvalGate = { _, _, _ -> ApprovalResult.Approve }, // task #8 will add the UI gate
        ).collect { event ->
            when (event) {
                is AgentEvent.TextDelta -> {
                    streamed.append(event.text)
                    _ui.value = _ui.value.copy(streaming = streamed.toString())
                }
                is AgentEvent.AssistantMessageDone -> {
                    history += event.message
                    if (streamed.isNotEmpty()) {
                        appendTurn(Turn("assistant", streamed.toString()))
                        streamed.clear()
                        _ui.value = _ui.value.copy(streaming = "")
                    }
                }
                is AgentEvent.ToolResultsBatch -> history += event.message
                is AgentEvent.PendingTool -> appendTurn(Turn("tool", "→ ${event.name} ${event.input}"))
                is AgentEvent.ToolExecuted -> {
                    val prefix = if (event.result.isError) "✗" else "✓"
                    val preview = event.result.content.lineSequence().take(3).joinToString("\n")
                    appendTurn(Turn("tool", "$prefix ${event.name}\n$preview"))
                }
                is AgentEvent.ToolRejected -> appendTurn(Turn("tool", "✗ ${event.name} (rejected)"))
                is AgentEvent.TurnComplete -> _ui.value = _ui.value.copy(streaming = "", isStreaming = false)
                is AgentEvent.Error -> {
                    appendTurn(Turn("system", event.message, isError = true))
                    _ui.value = _ui.value.copy(streaming = "", isStreaming = false)
                }
            }
        }
    }

    private fun appendTurn(turn: Turn) {
        _ui.value = _ui.value.copy(turns = _ui.value.turns + turn)
    }

    fun stop() {
        currentJob?.cancel()
        _ui.value = _ui.value.copy(isStreaming = false, streaming = "")
    }

    fun clear() {
        history.clear()
        _ui.value = _ui.value.copy(turns = emptyList(), streaming = "", isStreaming = false)
    }
}

@Composable
fun ChatDebugScreen(onBack: () -> Unit) {
    val vm: ChatDebugViewModel = viewModel()
    val ui by vm.ui.collectAsState()
    var draft by remember { mutableStateOf("") }
    val transcriptScroll = rememberScrollState()

    // Auto-scroll to bottom on new turn or streaming token.
    LaunchedEffect(ui.turns.size, ui.streaming.length, ui.isStreaming) {
        transcriptScroll.scrollTo(transcriptScroll.maxValue)
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TermButton(label = "back", onClick = onBack)
            TermText(text = "chat debug", color = TermColor.Accent, bold = true)
            TermButton(label = "clear", onClick = { vm.clear() })
        }
        TermText(text = ui.statusLine, color = TermColor.Dim)
        TermDivider()

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(transcriptScroll)
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ui.turns.forEach { turn ->
                when (turn.role) {
                    "user" -> Row { TermText(text = "❯ ${turn.text}", color = TermColor.Accent) }
                    "assistant" -> TermText(text = turn.text, color = TermColor.Fg)
                    "tool" -> TermBox(title = "tool") {
                        TermText(text = turn.text, color = TermColor.Dim)
                    }
                    else -> TermBox(title = "error") {
                        TermText(text = turn.text, color = TermColor.Red)
                    }
                }
            }
            if (ui.streaming.isNotEmpty()) {
                TermText(text = ui.streaming, color = TermColor.Fg)
            } else if (ui.isStreaming) {
                TermSpinner(label = ui.thinkingWord)
            }
        }

        TermDivider()
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TermInput(
                modifier = Modifier.weight(1f),
                value = draft,
                onValueChange = { draft = it },
                placeholder = if (ui.isStreaming) "streaming…" else "ask claude…",
                singleLine = false,
                imeAction = ImeAction.Send,
                onSubmit = {
                    val t = draft
                    if (t.isNotBlank() && !ui.isStreaming) {
                        draft = ""
                        vm.send(t)
                    }
                },
            )
            if (ui.isStreaming) {
                TermButton(label = "stop", color = TermColor.Red, onClick = { vm.stop() })
            } else {
                TermButton(
                    label = "send",
                    color = TermColor.Green,
                    onClick = {
                        val t = draft
                        if (t.isNotBlank()) {
                            draft = ""
                            vm.send(t)
                        }
                    },
                )
            }
        }
    }
}
