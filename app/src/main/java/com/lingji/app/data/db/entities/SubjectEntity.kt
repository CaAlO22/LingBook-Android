package com.lingji.app.data.db.entities

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
    val lastOpenedPageId: String? = null
)
