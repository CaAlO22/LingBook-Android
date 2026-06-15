package com.lingji.app.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notebook_pages")
data class NotebookPageEntity(
    @PrimaryKey val id: String,
    val subjectId: String,
    val title: String,
    val content: String,
    val orderIndex: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val indexedAt: Long = 0
)
