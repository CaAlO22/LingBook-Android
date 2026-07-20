package com.lingji.app.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "home_fragments")
data class HomeFragmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val position: Int,
    val content: String,
    val timestamp: Long? = null
)
