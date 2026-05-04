package com.visionsoldier.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "profile")
data class Profile(
    @PrimaryKey val id: Int = 1,
    val name: String = "",
    val profession: String = "",
    val interests: String = "",
    val traits: String = "",
    val diaryPassword: String = "",
    val apiKey: String = "",
    /** Motor de voz: "native" (Android TTS) o "elevenlabs" */
    val voiceEngine: String = "native",
    /** API Key de ElevenLabs (solo se usa si voiceEngine == "elevenlabs") */
    val elevenLabsApiKey: String = "",
    /** Voice ID de ElevenLabs (voz seleccionada) */
    val elevenLabsVoiceId: String = ""
)
