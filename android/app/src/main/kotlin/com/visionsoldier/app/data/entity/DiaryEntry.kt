package com.visionsoldier.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "diary")
data class DiaryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,        // ISO 8601
    val title: String = "",
    val content: String,
    val category: String = "personal",  // personal, trabajo, salud, meta, otros
    val importance: Int = 3,            // 1-5
    val tags: String = "",              // comma-separated
)
