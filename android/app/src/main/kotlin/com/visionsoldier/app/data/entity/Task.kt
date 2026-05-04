package com.visionsoldier.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val completed: Boolean = false,
    val timestamp: String,   // ISO 8601
)
