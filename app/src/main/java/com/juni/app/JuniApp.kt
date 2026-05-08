package com.juni.app

import android.app.Application
import com.juni.app.data.db.AppDatabase
import com.juni.app.data.db.ConversationRepository
import com.juni.app.data.prefs.AppSettings
import com.juni.app.data.prefs.SecurePrefs
import kotlinx.coroutines.flow.MutableStateFlow

class JuniApp : Application() {

    lateinit var securePrefs: SecurePrefs
        private set

    lateinit var appSettings: AppSettings
        private set

    lateinit var conversationRepository: ConversationRepository
        private set

    /**
     * Images the user has attached in the chat composer but not yet sent.
     * Camera writes here on capture; Chat reads here to render thumbnails
     * and bakes the bytes into the user message on send. Cleared after send.
     */
    val composerImages: MutableStateFlow<List<ByteArray>> = MutableStateFlow(emptyList())

    override fun onCreate() {
        super.onCreate()
        instance = this
        securePrefs = SecurePrefs(this)
        appSettings = AppSettings(this)
        val db = AppDatabase.build(this)
        conversationRepository = ConversationRepository(db.conversations(), db.messages())
    }

    fun addComposerImage(bytes: ByteArray) {
        composerImages.value = composerImages.value + bytes
    }

    fun clearComposerImages() {
        composerImages.value = emptyList()
    }

    companion object {
        private lateinit var instance: JuniApp

        fun get(): JuniApp = instance
    }
}
