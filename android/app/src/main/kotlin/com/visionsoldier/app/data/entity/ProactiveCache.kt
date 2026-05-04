package com.visionsoldier.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Caché local para los comentarios proactivos generados por Gemini.
 * Evita re-generar el mismo tipo de comentario dentro del mismo día.
 */
@Entity(tableName = "proactive_cache")
data class ProactiveCache(
    @PrimaryKey val type: String,     // curiosity, news, english_word, debate
    val title: String,
    val content: String,
    val dateGenerated: String,         // "2026-04-29" — solo el día
)
