package com.juni.app.ui.conversations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.juni.app.JuniApp
import com.juni.app.data.db.ConversationEntity
import com.juni.app.data.prefs.ProviderId
import com.juni.app.ui.terminal.Toaster
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ConversationsViewModel : ViewModel() {

    private val app = JuniApp.get()
    private val repo = app.conversationRepository

    val conversations: StateFlow<List<ConversationEntity>> = repo.observeConversations()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Create a new conversation in DB and invoke [onCreated] with its id. */
    fun createNew(onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val s = app.appSettings.flow.first()
            val provider = s.providerId
            val model = s.modelByProvider[provider].orEmpty()
            val entity = repo.create(provider.key, model)
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

}
