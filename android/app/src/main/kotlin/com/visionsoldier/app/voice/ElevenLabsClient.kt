package com.visionsoldier.app.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * Cliente para la API de ElevenLabs Text-to-Speech.
 *
 * Uso:
 *   val client = ElevenLabsClient(context, "tu-api-key")
 *   client.speak("Hola señor") { /* onDone */ }
 *
 * Configuración de voces recomendadas para estilo JARVIS:
 *   - "pNInz6obpgDQGcFmaJgB" → Adam (grave, profesional)
 *   - "ErXwobaYiN019PkySvjV" → Antoni (claro, elegante)
 *   - "VR6AewLTigWG4xSOukaG" → Arnold (muy grave)
 *   - "TxGEqnHWrfWFTfGW9XjX" → Josh (natural, calmado)
 *   - "pqHfZKP75CvOlQylNhV4" → Bill (maduro, authoritative)
 */
class ElevenLabsClient(
    private val context: Context,
    private var apiKey: String,
) {
    companion object {
        private const val TAG = "ElevenLabs"
        private const val BASE_URL = "https://api.elevenlabs.io/v1"

        // Voz por defecto — "Adam" (grave, profesional, estilo JARVIS)
        const val DEFAULT_VOICE_ID = "pNInz6obpgDQGcFmaJgB"

        // Modelo multilingüe v2 — soporta español con alta calidad
        private const val MODEL_ID = "eleven_multilingual_v2"
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private var mediaPlayer: MediaPlayer? = null
    private var currentAudioFile: File? = null

    var voiceId: String = DEFAULT_VOICE_ID
    var isSpeaking: Boolean = false
        private set

    fun updateApiKey(newKey: String) {
        if (newKey.isNotBlank()) apiKey = newKey
    }

    /**
     * Sintetiza texto a voz usando ElevenLabs y lo reproduce.
     * @param text Texto a hablar
     * @param onStart Callback al empezar a hablar
     * @param onDone Callback al terminar o en error
     */
    suspend fun speak(
        text: String,
        onStart: (() -> Unit)? = null,
        onDone: (() -> Unit)? = null,
    ) {
        if (apiKey.isBlank() || text.isBlank()) {
            onDone?.invoke()
            return
        }

        // Limpiar markdown
        val clean = text
            .replace(Regex("\\*\\*(.*?)\\*\\*"), "$1")
            .replace(Regex("\\*(.*?)\\*"), "$1")
            .replace(Regex("#{1,6}\\s"), "")
            .trim()

        try {
            // 1. Descargar audio desde ElevenLabs
            val audioFile = withContext(Dispatchers.IO) {
                requestAudio(clean)
            }

            if (audioFile == null || !audioFile.exists()) {
                Log.w(TAG, "No se pudo generar audio")
                onDone?.invoke()
                return
            }

            // 2. Reproducir el audio
            isSpeaking = true
            onStart?.invoke()
            playAudio(audioFile, onDone)

        } catch (e: Exception) {
            Log.e(TAG, "Error en speak: ${e.message}")
            isSpeaking = false
            onDone?.invoke()
        }
    }

    /**
     * Llama a la API de ElevenLabs y descarga el MP3 resultante.
     */
    private fun requestAudio(text: String): File? {
        val payload = JSONObject().apply {
            put("text", text)
            put("model_id", MODEL_ID)
            put("voice_settings", JSONObject().apply {
                put("stability", 0.5)              // Más variación = más natural
                put("similarity_boost", 0.75)       // Parecido a la voz original
                put("style", 0.3)                   // Un poco de dramatismo JARVIS
                put("use_speaker_boost", true)
            })
        }

        val request = Request.Builder()
            .url("$BASE_URL/text-to-speech/$voiceId")
            .addHeader("xi-api-key", apiKey)
            .addHeader("Accept", "audio/mpeg")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = httpClient.newCall(request).execute()

        if (!response.isSuccessful) {
            val errorBody = runCatching { response.body?.string() ?: "" }.getOrDefault("")
            Log.e(TAG, "API error ${response.code}: $errorBody")
            return null
        }

        // Guardar el audio en un archivo temporal
        val audioFile = File(context.cacheDir, "elevenlabs_tts.mp3")
        response.body?.byteStream()?.use { input ->
            FileOutputStream(audioFile).use { output ->
                input.copyTo(output)
            }
        }

        currentAudioFile = audioFile
        return audioFile
    }

    /**
     * Reproduce un archivo de audio MP3 usando MediaPlayer.
     */
    private suspend fun playAudio(file: File, onDone: (() -> Unit)?) {
        stopPlaying()

        suspendCancellableCoroutine { cont ->
            try {
                mediaPlayer = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .setUsage(AudioAttributes.USAGE_ASSISTANT)
                            .build()
                    )
                    setDataSource(file.absolutePath)
                    setOnPreparedListener { start() }
                    setOnCompletionListener {
                        isSpeaking = false
                        onDone?.invoke()
                        if (cont.isActive) cont.resume(Unit)
                    }
                    setOnErrorListener { _, what, extra ->
                        Log.e(TAG, "MediaPlayer error: what=$what extra=$extra")
                        isSpeaking = false
                        onDone?.invoke()
                        if (cont.isActive) cont.resume(Unit)
                        true
                    }
                    prepareAsync()
                }

                cont.invokeOnCancellation {
                    stopPlaying()
                    isSpeaking = false
                    onDone?.invoke()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error reproduciendo audio: ${e.message}")
                isSpeaking = false
                onDone?.invoke()
                if (cont.isActive) cont.resume(Unit)
            }
        }
    }

    /** Detener la reproducción actual */
    fun stopPlaying() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null
    }

    /** Verificar si la API key es válida haciendo una llamada ligera */
    suspend fun verifyApiKey(): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$BASE_URL/user")
                .addHeader("xi-api-key", apiKey)
                .get()
                .build()
            val response = httpClient.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    /** Obtener lista de voces disponibles */
    suspend fun getVoices(): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$BASE_URL/voices")
                .addHeader("xi-api-key", apiKey)
                .get()
                .build()
            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext emptyList()
            val voices = JSONObject(body).getJSONArray("voices")
            (0 until voices.length()).map { i ->
                val v = voices.getJSONObject(i)
                Pair(v.getString("voice_id"), v.getString("name"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo voces: ${e.message}")
            emptyList()
        }
    }

    fun destroy() {
        stopPlaying()
        currentAudioFile?.delete()
        currentAudioFile = null
    }
}
