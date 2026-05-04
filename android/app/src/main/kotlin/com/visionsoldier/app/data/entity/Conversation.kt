package com.visionsoldier.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class Conversation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val role: String,        // "user" or "vision"
    val content: String,
    val timestamp: String,   // ISO 8601
)
