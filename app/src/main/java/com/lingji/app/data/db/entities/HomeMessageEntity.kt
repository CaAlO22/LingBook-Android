package com.lingji.app.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "home_messages",
    foreignKeys = [ForeignKey(
        entity = HomeConversationEntity::class,
        parentColumns = ["id"],
        childColumns = ["conversation_id"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("conversation_id")]
)
data class HomeMessageEntity(
    @PrimaryKey val id: String,
    val conversation_id: String,
    val role: String,
    val content: String,
    val tool_calls_json: String?,
    val timestamp: Long,
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = System.currentTimeMillis()
)
