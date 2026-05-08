package com.juni.app.ui.chatdebug

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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.juni.app.JuniApp
import com.juni.app.data.prefs.ProviderId
import com.juni.app.data.provider.ClaudeProvider
import com.juni.app.domain.agent.Message
import com.juni.app.domain.agent.StreamEvent
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
                _ui.value = _ui.value.copy(
                    turns = _ui.value.turns + Turn(
                        role = "system",
                        text = "Only Claude is wired up so far. Switch provider in Settings.",
                        isError = true,
                    ),
                )
                return@launch
            }
            if (key.isBlank()) {
                _ui.value = _ui.value.copy(
                    turns = _ui.value.turns + Turn(
                        role = "system",
                        text = "No Claude API key set. Add one in Settings.",
                        isError = true,
                    ),
                )
                return@launch
            }

            val nextTurns = _ui.value.turns + Turn("user", text)
            _ui.value = _ui.value.copy(
                turns = nextTurns,
                streaming = "",
                isStreaming = true,
                thinkingWord = randomThinkingWord(),
            )

            val history = nextTurns.map {
                if (it.role == "user") Message.userText(it.text) else Message.assistantText(it.text)
            }
            val claude = ClaudeProvider(key)
            val streamed = StringBuilder()
            try {
                claude.streamTurn(
                    systemPrompt = "You are juni, a brief and friendly assistant.",
                    messages = history,
                    tools = emptyList(),
                    model = settings.modelByProvider[ProviderId.CLAUDE].orEmpty(),
                ).collect { event ->
                    when (event) {
                        is StreamEvent.TextDelta -> {
                            streamed.append(event.text)
                            _ui.value = _ui.value.copy(streaming = streamed.toString())
                        }
                        is StreamEvent.ToolCallEvent -> Unit // ignored in debug screen
                        is StreamEvent.TurnEnd -> {
                            _ui.value = _ui.value.copy(
                                turns = _ui.value.turns + Turn("assistant", streamed.toString()),
                                streaming = "",
                                isStreaming = false,
                            )
                        }
                        is StreamEvent.Error -> {
                            _ui.value = _ui.value.copy(
                                turns = _ui.value.turns + Turn(
                                    role = "system",
                                    text = event.message,
                                    isError = true,
                                ),
                                streaming = "",
                                isStreaming = false,
                            )
                        }
                    }
                }
            } catch (t: Throwable) {
                _ui.value = _ui.value.copy(
                    turns = _ui.value.turns + Turn("system", t.message ?: "stream failed", isError = true),
                    streaming = "",
                    isStreaming = false,
                )
            }
        }
    }

    fun stop() {
        currentJob?.cancel()
        _ui.value = _ui.value.copy(isStreaming = false, streaming = "")
    }

    fun clear() {
        _ui.value = _ui.value.copy(turns = emptyList(), streaming = "", isStreaming = false)
    }
}

@Composable
fun ChatDebugScreen(onBack: () -> Unit) {
    val vm: ChatDebugViewModel = viewModel()
    val ui by vm.ui.collectAsState()
    var draft by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
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
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ui.turns.forEach { turn ->
                when (turn.role) {
                    "user" -> Row { TermText(text = "❯ ${turn.text}", color = TermColor.Accent) }
                    "assistant" -> TermText(text = turn.text, color = TermColor.Fg)
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
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
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
