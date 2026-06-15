package com.lingji.app.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val id: String = "singleton",
    val provider: String = "OPENAI",
    val baseUrl: String = "",
    val apiKey: String = "",
    val modelName: String = "gpt-4o",
    val enableThinking: Boolean = false
)
