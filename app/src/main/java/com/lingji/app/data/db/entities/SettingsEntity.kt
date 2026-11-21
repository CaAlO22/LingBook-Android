package com.lingji.app.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val id: String = "singleton",
    /** 主模型对应的 model_configs.id */
    val primaryModelId: String = "",
    /** 辅助模型对应的 model_configs.id，空表示不使用 */
    val assistantModelId: String = "",
    /** Agent 工具调用温度 */
    val agentTemperature: Double = 0.2,
    /** 笔记页左右横滑手势行为：NONE / TOGGLE_PREVIEW / CHANGE_PAGE */
    val horizontalSwipeAction: String = "TOGGLE_PREVIEW",
    /** 首页默认落地页：CARD_LIST / AGGREGATION */
    val homePageMode: String = "CARD_LIST"
)
