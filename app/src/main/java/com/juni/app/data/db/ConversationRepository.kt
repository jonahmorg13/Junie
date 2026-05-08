package com.juni.app.data.db

import com.juni.app.data.provider.HttpClient
import com.juni.app.domain.agent.Message
import com.juni.app.domain.agent.Role
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import java.util.UUID

class ConversationRepository(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
) {
    fun observeConversations(): Flow<List<ConversationEntity>> = conversationDao.observeAll()

    suspend fun get(id: String): ConversationEntity? = conversationDao.get(id)

    suspend fun create(providerId: String, modelId: String): ConversationEntity {
        val now = System.currentTimeMillis()
        val entity = ConversationEntity(
            id = UUID.randomUUID().toString(),
            title = themedSessionName(),
            createdAt = now,
            updatedAt = now,
            providerId = providerId,
            modelId = modelId,
        )
        conversationDao.upsert(entity)
        return entity
    }

    suspend fun delete(id: String) = conversationDao.delete(id)

    suspend fun deleteMany(ids: List<String>) {
        if (ids.isEmpty()) return
        conversationDao.deleteIds(ids)
    }

    suspend fun deleteAll(): Int {
        val n = conversationDao.count()
        conversationDao.deleteAll()
        return n
    }

    suspend fun loadMessages(conversationId: String): List<Message> =
        messageDao.forConversation(conversationId).map { row ->
            Message(
                role = Role.valueOf(row.role),
                content = HttpClient.json.decodeFromString(row.contentJson),
            )
        }

    /** Append a single canonical message to a conversation and bump its updatedAt. */
    suspend fun appendMessage(conversationId: String, message: Message) {
        val entity = MessageEntity(
            id = UUID.randomUUID().toString(),
            conversationId = conversationId,
            role = message.role.name,
            contentJson = HttpClient.json.encodeToString(message.content),
            createdAt = System.currentTimeMillis(),
        )
        messageDao.insert(entity)
        conversationDao.touch(conversationId, entity.createdAt)
    }

    /** Replace the conversation title (we use this once per conversation, after the first user message). */
    suspend fun setTitle(conversationId: String, title: String) {
        conversationDao.setTitle(conversationId, title.take(80), System.currentTimeMillis())
    }
}

/**
 * Pull a one-line title out of an arbitrary user prompt. Strips newlines, collapses
 * whitespace, and truncates so we don't end up with a 2KB chat title.
 */
fun titleFromUserText(text: String, maxChars: Int = 60): String {
    val cleaned = text.replace(Regex("\\s+"), " ").trim()
    if (cleaned.isEmpty()) return "new chat"
    return if (cleaned.length <= maxChars) cleaned else cleaned.take(maxChars).trimEnd() + "…"
}

private val SESSION_ADJECTIVES = listOf(
    "wandering", "pondering", "glowing", "smoldering", "drifting",
    "kindling", "flickering", "dancing", "humming", "whispering",
    "simmering", "burning", "crackling", "shimmering", "lingering",
    "rising", "swirling", "fading", "tumbling", "rolling",
)
private val SESSION_NOUNS = listOf(
    "ember", "spark", "flame", "wisp", "candle", "lantern", "torch",
    "blaze", "flicker", "smoke", "ash", "glow", "fire", "halo", "fuse",
)

/** A short, evocative default name for a fresh session, like "pondering ember". */
fun themedSessionName(): String =
    "${SESSION_ADJECTIVES.random()} ${SESSION_NOUNS.random()}"
