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
    val enableThinking: Boolean = false,
    /** 笔记页左右横滑手势行为：NONE / TOGGLE_PREVIEW / CHANGE_PAGE */
    val horizontalSwipeAction: String = "TOGGLE_PREVIEW",
    /** 按供应商名缓存的 API Key JSON，如 {"OPENAI":"sk-xxx","DEEPSEEK":"..."} */
    val providerApiKeys: String = "{}"
)
