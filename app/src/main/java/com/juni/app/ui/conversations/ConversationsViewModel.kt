package com.juni.app.ui.conversations

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.juni.app.JuniApp
import com.juni.app.data.db.ConversationEntity
import com.juni.app.ui.terminal.Toaster
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ConversationsViewModel : ViewModel() {

    private val app = JuniApp.get()
    private val repo = app.conversationRepository

    val conversations: StateFlow<List<ConversationEntity>> = repo.observeConversations()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** True once the user has picked a vault folder. Chats are gated on this. */
    val vaultIsSet: StateFlow<Boolean> = app.appSettings.flow
        .map { it.vaultUri != null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _selectedIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedIds: StateFlow<Set<String>> = _selectedIds.asStateFlow()

    /** Create a new conversation in DB and invoke [onCreated] with its id. */
    fun createNew(onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val s = app.appSettings.flow.first()
            if (s.vaultUri == null) {
                Toaster.error("set a vault folder in settings before chatting")
                return@launch
            }
            val provider = s.providerId
            val model = s.modelByProvider[provider].orEmpty()
            val entity = repo.create(provider.key, model)
            Log.d("juni-nav", "createNew → entity.id=${entity.id}")
            onCreated(entity.id)
        }
    }

    fun delete(conversationId: String) {
        viewModelScope.launch {
            repo.delete(conversationId)
            Toaster.success("conversation deleted")
        }
    }

    fun rename(conversationId: String, newTitle: String) {
        val title = newTitle.trim().ifEmpty { return }
        viewModelScope.launch {
            repo.setTitle(conversationId, title)
            Toaster.success("renamed to \"${title.take(40)}\"")
        }
    }

    fun toggleSelection(id: String) {
        _selectedIds.value = _selectedIds.value.toMutableSet().apply {
            if (!add(id)) remove(id)
        }
    }

    fun selectAll() {
        _selectedIds.value = conversations.value.map { it.id }.toSet()
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
    }

    fun deleteSelected() {
        val ids = _selectedIds.value.toList()
        if (ids.isEmpty()) return
        _selectedIds.value = emptySet()
        viewModelScope.launch {
            repo.deleteMany(ids)
            Toaster.success(
                if (ids.size == 1) "conversation deleted"
                else "${ids.size} conversations deleted",
            )
        }
    }
}
