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
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = System.currentTimeMillis()
)
