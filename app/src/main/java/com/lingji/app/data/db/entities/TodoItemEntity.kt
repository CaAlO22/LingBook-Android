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
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = System.currentTimeMillis()
)
