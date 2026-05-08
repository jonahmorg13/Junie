package com.juni.app.ui.chat

import android.net.Uri
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.juni.app.JuniApp
import com.juni.app.data.prefs.ProviderId
import com.juni.app.data.provider.ClaudeProvider
import com.juni.app.data.vault.VaultRepository
import com.juni.app.domain.agent.AgentEvent
import com.juni.app.domain.agent.AgentLoop
import com.juni.app.domain.agent.ApprovalResult
import com.juni.app.domain.agent.Message
import com.juni.app.domain.agent.MessageContent
import com.juni.app.domain.agent.Role
import com.juni.app.domain.tools.AttachmentStaging
import com.juni.app.domain.tools.ToolRegistry
import com.juni.app.domain.tools.VaultTools
import com.juni.app.ui.terminal.randomThinkingWord
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class ChatUi(
    val items: List<ChatItem> = emptyList(),
    val streaming: String = "",
    val isStreaming: Boolean = false,
    val statusLine: String = "",
    val thinkingWord: String = "thinking",
)

/**
 * Auto-approved tools — read-only or pure I/O passthrough where requiring a
 * tap per call would just be friction. Everything that *writes* still
 * requires explicit approval.
 */
private val AUTO_APPROVE = setOf(
    "list_files", "read_note", "search_notes", "ask_clarifying_question",
)

class ChatViewModel : ViewModel() {

    private val app = JuniApp.get()
    private val _ui = MutableStateFlow(ChatUi())
    val ui: StateFlow<ChatUi> = _ui.asStateFlow()

    /** Canonical message history sent on every turn. */
    private val history: MutableList<Message> = mutableListOf()

    /** Pending approval handles, keyed by tool_use id. */
    private val pendingApprovals: MutableMap<String, CompletableDeferred<ApprovalResult>> = mutableMapOf()

    /** Backing AttachmentStaging the agent's save_attachment tool pulls from. */
    private val attachmentStaging = AttachmentStaging()

    /** Pending attachments visible in the composer, observed for image-count display. */
    val pendingImages: StateFlow<List<ByteArray>> = app.composerImages

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
        val images = app.composerImages.value
        if ((text.isEmpty() && images.isEmpty()) || _ui.value.isStreaming) return

        currentJob = viewModelScope.launch {
            val settings = app.appSettings.flow.first()
            val provider = settings.providerId
            val key = app.securePrefs.apiKey(provider)
            if (provider != ProviderId.CLAUDE) {
                appendItem(ChatItem.SystemError("Only Claude is wired up so far."))
                return@launch
            }
            if (key.isBlank()) {
                appendItem(ChatItem.SystemError("No Claude API key set. Add one in Settings."))
                return@launch
            }

            // Build the user message: any images first, then the text. Also feed
            // copies into the AttachmentStaging so save_attachment can persist them.
            val content = buildList<MessageContent> {
                images.forEach { bytes ->
                    val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    add(MessageContent.Image(mimeType = "image/jpeg", base64 = b64))
                    attachmentStaging.add(bytes)
                }
                if (text.isNotEmpty()) add(MessageContent.Text(text))
            }
            history += Message(Role.USER, content)
            appendItem(ChatItem.UserMessage(text = text, imageCount = images.size))
            app.clearComposerImages()

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
                appendItem(ChatItem.SystemError(t.message ?: "stream failed"))
                _ui.value = _ui.value.copy(streaming = "", isStreaming = false)
            }
        }
    }

    fun removePendingImage(index: Int) {
        val current = app.composerImages.value
        if (index !in current.indices) return
        app.composerImages.value = current.toMutableList().also { it.removeAt(index) }
    }

    fun approve(toolUseId: String) {
        pendingApprovals.remove(toolUseId)?.complete(ApprovalResult.Approve)
        updateToolState(toolUseId, ToolState.Running)
    }

    fun reject(toolUseId: String, reason: String? = null) {
        pendingApprovals.remove(toolUseId)?.complete(ApprovalResult.Reject(reason))
        updateToolState(toolUseId, ToolState.Rejected(reason))
    }

    fun stop() {
        currentJob?.cancel()
        // Reject any in-flight approvals so the loop terminates cleanly.
        pendingApprovals.values.toList().forEach {
            it.complete(ApprovalResult.Reject("User cancelled."))
        }
        pendingApprovals.clear()
        _ui.value = _ui.value.copy(isStreaming = false, streaming = "")
    }

    fun clear() {
        history.clear()
        pendingApprovals.clear()
        _ui.value = _ui.value.copy(items = emptyList(), streaming = "", isStreaming = false)
    }

    private suspend fun runAgent(
        claude: ClaudeProvider,
        model: String,
        vaultUri: String?,
    ) {
        val tools = if (vaultUri != null) {
            val vault = VaultRepository(app, Uri.parse(vaultUri))
            ToolRegistry(VaultTools.all(vault, attachmentStaging))
        } else {
            ToolRegistry(emptyList())
        }
        val loop = AgentLoop(provider = claude, tools = tools, model = model)
        val streamed = StringBuilder()

        loop.run(
            initialMessages = history.toList(),
            approvalGate = { id, name, _ ->
                if (name in AUTO_APPROVE) {
                    updateToolState(id, ToolState.Running)
                    ApprovalResult.Approve
                } else {
                    val deferred = CompletableDeferred<ApprovalResult>()
                    pendingApprovals[id] = deferred
                    deferred.await()
                }
            },
        ).collect { event ->
            when (event) {
                is AgentEvent.TextDelta -> {
                    streamed.append(event.text)
                    _ui.value = _ui.value.copy(streaming = streamed.toString())
                }
                is AgentEvent.AssistantMessageDone -> {
                    history += event.message
                    if (streamed.isNotEmpty()) {
                        appendItem(ChatItem.AssistantText(streamed.toString()))
                        streamed.clear()
                        _ui.value = _ui.value.copy(streaming = "")
                    }
                }
                is AgentEvent.ToolResultsBatch -> history += event.message
                is AgentEvent.PendingTool -> {
                    appendItem(
                        ChatItem.ToolCall(
                            id = event.id,
                            name = event.name,
                            input = event.input,
                            state = ToolState.Pending,
                        ),
                    )
                }
                is AgentEvent.ToolExecuted -> updateToolState(event.id, ToolState.Done(event.result))
                is AgentEvent.ToolRejected -> updateToolState(event.id, ToolState.Rejected(event.reason))
                is AgentEvent.TurnComplete -> {
                    _ui.value = _ui.value.copy(streaming = "", isStreaming = false)
                }
                is AgentEvent.Error -> {
                    appendItem(ChatItem.SystemError(event.message))
                    _ui.value = _ui.value.copy(streaming = "", isStreaming = false)
                }
            }
        }
    }

    private fun appendItem(item: ChatItem) {
        _ui.value = _ui.value.copy(items = _ui.value.items + item)
    }

    private fun updateToolState(toolUseId: String, state: ToolState) {
        _ui.value = _ui.value.copy(
            items = _ui.value.items.map { item ->
                if (item is ChatItem.ToolCall && item.id == toolUseId) item.copy(state = state) else item
            },
        )
    }
}
