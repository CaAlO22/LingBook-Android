package com.lingji.app.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "subject_summaries")
data class SubjectSummaryEntity(
    @PrimaryKey val subjectId: String,
    val summary: String,
    val summarizedAt: Long,
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = System.currentTimeMillis()
)
