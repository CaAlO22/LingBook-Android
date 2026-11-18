package com.lingji.app.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 用户添加的模型配置，主模型与辅助模型都从这里选择。 */
@Entity(tableName = "model_configs")
data class ModelConfigEntity(
    @PrimaryKey val id: String,
    /** 用户自定义的模型 ID（别名），为空时展示 modelName */
    val alias: String,
    val provider: String,
    val baseUrl: String,
    val apiKey: String,
    val modelName: String,
    /** [com.lingji.app.domain.model.ThinkingLevel] 名称；旧库的 enableThinking 在迁移时换算。 */
    val thinkingLevel: String,
    val createdAt: Long,
    /** 最后修改时间，云同步增量同步用。 */
    val updatedAt: Long
)
