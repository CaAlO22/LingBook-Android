package com.lingji.app.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "fragments")
data class FragmentEntity(
    @PrimaryKey val id: String,
    val subjectId: String,
    val content: String,
    val timestamp: Long,
    val isUnmerged: Boolean = false
)
