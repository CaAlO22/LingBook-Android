package com.lingji.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lingji.app.data.db.entities.HomeConversationEntity
import com.lingji.app.data.db.entities.HomeFragmentEntity
import com.lingji.app.data.db.entities.HomeMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HomeChatDao {

    @Query("SELECT * FROM home_conversations ORDER BY updated_at DESC")
    fun getConversations(): Flow<List<HomeConversationEntity>>

    @Query("SELECT * FROM home_conversations WHERE id = :id")
    suspend fun getConversationById(id: String): HomeConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(conversation: HomeConversationEntity)

    @Query("UPDATE home_conversations SET title = :title, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateConversationTimestamp(id: String, title: String, updatedAt: Long)

    @Query("DELETE FROM home_conversations WHERE id = :id")
    suspend fun deleteConversation(id: String)

    @Query("SELECT * FROM home_messages WHERE conversation_id = :conversationId ORDER BY timestamp ASC")
    fun getMessages(conversationId: String): Flow<List<HomeMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: HomeMessageEntity)

    @Query("INSERT OR REPLACE INTO home_messages (id, conversation_id, role, content, tool_calls_json, timestamp) VALUES (:id, :conversationId, :role, :content, :toolCallsJson, :timestamp)")
    suspend fun insertMessageRaw(
        id: String,
        conversationId: String,
        role: String,
        content: String,
        toolCallsJson: String?,
        timestamp: Long
    )

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<HomeMessageEntity>)

    @Query("DELETE FROM home_messages WHERE conversation_id = :conversationId")
    suspend fun deleteMessagesByConversation(conversationId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM home_messages WHERE conversation_id = :id LIMIT 1)")
    suspend fun conversationHasMessages(id: String): Boolean

    // ── Home fragments ──

    @Query("SELECT * FROM home_fragments ORDER BY position ASC")
    fun getFragments(): Flow<List<HomeFragmentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFragment(fragment: HomeFragmentEntity)

    @Query("DELETE FROM home_fragments WHERE id = :id")
    suspend fun deleteFragment(id: Long)

    @Query("DELETE FROM home_fragments")
    suspend fun clearAllFragments()
}
