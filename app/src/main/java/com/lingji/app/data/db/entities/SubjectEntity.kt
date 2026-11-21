package com.lingji.app.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "subjects")
data class SubjectEntity(
    @PrimaryKey val id: String,
    val title: String,
    val type: String,
    val aggregatedNote: String,
    val prevAggregatedNote: String?,
    val studyPlan: String,
    val createdAt: Long,
    val orderIndex: Int = 0,
    val pageIndexJson: String = "",
    val lastOpenedPageId: String? = null,
    val folderId: String? = null,  // null = 在首页, 非 null = 在对应文件夹内
    // 待办列表重复设置（仅 type = todo 使用）
    val todoRepeatType: String = "ONCE",
    val todoRepeatDays: String = "",
    val todoIntervalDays: Int = 2,
    val todoRepeatAnchorAt: Long = 0,
    val todoLastResetAt: Long = 0,
    // 信息岛：待办列表整体提醒（详细语义见 Subject）
    @ColumnInfo(defaultValue = "0") val todoReminderEnabled: Boolean = false,
    @ColumnInfo(defaultValue = "-1") val todoReminderMinutes: Int = -1,
    @ColumnInfo(defaultValue = "0") val todoReminderAt: Long = 0,
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = System.currentTimeMillis()
)
