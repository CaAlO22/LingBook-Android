package com.lingji.app.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "home_conversations")
data class HomeConversationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val created_at: Long,
    val updated_at: Long
)
