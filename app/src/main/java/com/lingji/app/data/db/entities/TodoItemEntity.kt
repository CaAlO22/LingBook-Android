package com.lingji.app.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "todo_items", indices = [Index("subjectId")])
data class TodoItemEntity(
    @PrimaryKey val id: String,
    val subjectId: String,
    val content: String,
    val isDone: Boolean = false,
    /** 手动拖动排序位次（升序），新增条目的位次取当前最大值 +1。 */
    @ColumnInfo(defaultValue = "0") val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = System.currentTimeMillis(),
    // 信息岛：单条待办提醒（详细语义见 TodoItem）
    @ColumnInfo(defaultValue = "0") val reminderEnabled: Boolean = false,
    @ColumnInfo(defaultValue = "-1") val reminderMinutes: Int = -1,
    @ColumnInfo(defaultValue = "0") val reminderAt: Long = 0
)
