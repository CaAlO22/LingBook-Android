package com.lingji.app.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "note_revisions", indices = [Index("subjectId")])
data class NoteRevisionEntity(
    @PrimaryKey val id: String,
    val subjectId: String,
    val pageId: String?,
    val field: String,
    val prevContent: String,
    val prevTitle: String?,
    val batchId: String?,
    val createdAt: Long
)
