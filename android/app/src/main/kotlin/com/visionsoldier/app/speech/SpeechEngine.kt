package com.visionsoldier.app.speech

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.util.Log
import com.visionsoldier.app.voice.ElevenLabsClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale
import android.media.AudioManager

/**
 * Motor de voz unificado: reconocimiento (SpeechRecognizer) + síntesis (Native TTS o ElevenLabs).
 *
 * voiceEngine:
 *   - "native"     → Android TextToSpeech (gratis, offline, latencia baja)
 *   - "elevenlabs"  → ElevenLabs API (calidad premium, requiere API key, requiere internet)
 */
class SpeechEngine(
    private val context: Context,
    private val onWakeWord: (command: String) -> Unit,
    private val onInterim: (text: String) -> Unit,
    private val onError: (String) -> Unit,
) {
    companion object {
        private const val TAG = "SpeechEngine"
        val WAKE_WORDS = listOf("jarvis", "jarwis", "yarvis", "harvis", "jarvís", "jarvis,", "jarvis!")

        // Palabras clave de voces femeninas a evitar
        private val FEMALE_KWS = Regex(
            "female|mujer|zira|susan|hazel|kate|heather|samantha|victoria|anna|cortana|elena|sabina",
            RegexOption.IGNORE_CASE
        )
    }

    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var elevenLabs: ElevenLabsClient? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.Main)
    private var isListening = false
    private var isSpeaking = false
    private var isWoken = false
    private var wokenTimer: Runnable? = null
    private var shouldRestart = true

    /** Motor de voz actual: "native" o "elevenlabs" */
    var voiceEngine: String = "native"
        private set

    var onSpeakStart: (() -> Unit)? = null
    var onSpeakEnd: (() -> Unit)? = null

    fun isSpeakingNow(): Boolean = isSpeaking

    // ── Inicializar TTS ──────────────────────────────────────

    fun initTts(onReady: () -> Unit) {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                // Configuración estilo Jarvis: voz grave, deliberada
                tts?.setSpeechRate(0.87f)   // velocidad deliberada, como Jarvis
                tts?.setPitch(0.68f)         // tono grave masculino
                tts?.language = Locale("es", "MX")

                // Intentar seleccionar una voz masculina de calidad
                selectBestVoice()

                onReady()
            }
        }
    }

    private fun selectBestVoice() {
        val voices = tts?.voices ?: return

        // Prioridad de selección (de mayor a menor)
        val priorities: List<(Voice) -> Boolean> = listOf(
            // 1. Español masculino de Google
            { v -> v.locale.language == "es" && v.name.contains("google", ignoreCase = true) && !FEMALE_KWS.containsMatchIn(v.name) },
            // 2. Español masculino con nombres conocidos
            { v -> v.locale.language == "es" && Regex("pablo|jorge|diego|carlos|miguel|antonio", RegexOption.IGNORE_CASE).containsMatchIn(v.name) },
            // 3. Español masculino sin keywords femeninos
            { v -> v.locale.language == "es" && !FEMALE_KWS.containsMatchIn(v.name) },
            // 4. Inglés UK masculino (más parecido a Jarvis)
            { v -> v.name.contains("google uk english male", ignoreCase = true) },
            // 5. Microsoft David / Mark / George
            { v -> Regex("david|mark\\b|george|james", RegexOption.IGNORE_CASE).containsMatchIn(v.name) },
            // 6. Inglés UK sin voz femenina
            { v -> v.locale == Locale.UK && !FEMALE_KWS.containsMatchIn(v.name) },
            // 7. Cualquier inglés masculino
            { v -> v.locale.language == "en" && !FEMALE_KWS.containsMatchIn(v.name) },
            // 8. Cualquier voz no femenina
            { v -> !FEMALE_KWS.containsMatchIn(v.name) },
        )

        for (check in priorities) {
            val found = voices.firstOrNull { v -> !v.isNetworkConnectionRequired && check(v) }
                ?: voices.firstOrNull(check) // fallback a red si no hay local
            if (found != null) {
                tts?.voice = found
                Log.d(TAG, "Voz seleccionada: ${found.name} [${found.locale}]")
                return
            }
        }
        Log.w(TAG, "No se encontró voz masculina preferida, usando default.")
    }

    // ── Iniciar reconocimiento continuo ──────────────────────

    fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError("SpeechRecognizer no disponible en este dispositivo.")
            return
        }
        shouldRestart = true
        mainHandler.post { startRecognizer() }
    }

    fun stopListening() {
        shouldRestart = false
        mainHandler.post {
            recognizer?.stopListening()
            recognizer?.destroy()
            recognizer = null
            isListening = false
        }
    }

    private fun startRecognizer() {
        if (isListening || isSpeaking) return
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0)

            recognizer?.destroy()
            recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            recognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { isListening = true }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}

                override fun onResults(results: Bundle?) {
                    isListening = false
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: return
                    handleResult(matches)
                    if (shouldRestart && !isSpeaking) {
                        mainHandler.postDelayed({ startRecognizer() }, 300)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    partial?.firstOrNull()?.let { onInterim(it) }
                }

                override fun onError(error: Int) {
                    isListening = false
                    val ignorable = listOf(
                        SpeechRecognizer.ERROR_NO_MATCH,
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                        SpeechRecognizer.ERROR_AUDIO,
                    )
                    if (shouldRestart && !isSpeaking) {
                        val delay = if (error in ignorable) 500L else 1500L
                        mainHandler.postDelayed({ startRecognizer() }, delay)
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-MX")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }
            recognizer?.startListening(intent)

            mainHandler.postDelayed({
                try {
                    audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
                } catch (e: Exception) {}
            }, 300)
        } catch (e: Exception) {
            Log.e(TAG, "Error iniciando reconocedor: ${e.message}")
            try {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
            } catch (ex: Exception) {}
            if (shouldRestart) mainHandler.postDelayed({ startRecognizer() }, 2000)
        }
    }

    private fun handleResult(matches: List<String>) {
        val allText = matches.joinToString(" ").lowercase()

        if (!isWoken) {
            val wakeWord = WAKE_WORDS.firstOrNull { allText.contains(it) }
            if (wakeWord != null) {
                val best = matches.firstOrNull { it.lowercase().contains(wakeWord) } ?: ""
                val idx = best.lowercase().indexOf(wakeWord)
                val command = if (idx >= 0) best.substring(idx + wakeWord.length).trim()
                    .replace(Regex("^[,.:!?;\\s]+"), "") else ""

                isWoken = true
                onWakeWord(command)

                if (command.length > 2) {
                    isWoken = false
                } else {
                    wokenTimer = Runnable { isWoken = false }
                    mainHandler.postDelayed(wokenTimer!!, 8000)
                }
            }
        } else {
            val command = matches.firstOrNull()?.trim() ?: return
            if (command.length >= 2) {
                mainHandler.removeCallbacks(wokenTimer ?: return)
                isWoken = false
                onWakeWord(command)
            }
        }
    }

    // ── Configuración del motor de voz ─────────────────────────

    /**
     * Cambia el motor de síntesis de voz.
     * @param engine "native" o "elevenlabs"
     * @param elevenLabsApiKey API key de ElevenLabs (solo si engine == "elevenlabs")
     * @param elevenLabsVoiceId Voice ID de ElevenLabs (opcional, usa Adam por defecto)
     */
    fun setVoiceEngine(engine: String, elevenLabsApiKey: String = "", elevenLabsVoiceId: String = "") {
        voiceEngine = engine
        Log.d(TAG, "Motor de voz cambiado a: $engine")

        when (engine) {
            "elevenlabs" -> {
                if (elevenLabsApiKey.isNotBlank()) {
                    if (elevenLabs == null) {
                        elevenLabs = ElevenLabsClient(context, elevenLabsApiKey)
                    } else {
                        elevenLabs?.updateApiKey(elevenLabsApiKey)
                    }
                    if (elevenLabsVoiceId.isNotBlank()) {
                        elevenLabs?.voiceId = elevenLabsVoiceId
                    }
                    Log.d(TAG, "ElevenLabs configurado. Voice ID: ${elevenLabs?.voiceId}")
                } else {
                    Log.w(TAG, "ElevenLabs seleccionado pero sin API key — se usará TTS nativo como fallback")
                }
            }
            else -> {
                // "native" — no necesita configuración adicional
            }
        }
    }

    // ── Síntesis de Voz (unificada) ──────────────────────────

    fun speak(text: String, onDone: (() -> Unit)? = null) {
        if (text.isBlank()) { onDone?.invoke(); return }

        isSpeaking = true
        shouldRestart = false
        stopListening()
        onSpeakStart?.invoke()

        when {
            // Intentar ElevenLabs si está configurado
            voiceEngine == "elevenlabs" && elevenLabs != null && elevenLabs?.isSpeaking != true -> {
                speakWithElevenLabs(text, onDone)
            }
            // Fallback a TTS nativo
            else -> {
                speakWithNativeTts(text, onDone)
            }
        }
    }

    private fun speakWithNativeTts(text: String, onDone: (() -> Unit)?) {
        if (tts == null) {
            finishSpeaking(onDone)
            return
        }

        val clean = cleanTextForSpeech(text)

        tts?.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "vision_utterance")
        tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                mainHandler.post { finishSpeaking(onDone) }
            }
            override fun onError(utteranceId: String?) {
                mainHandler.post { finishSpeaking(onDone) }
            }
        })
    }

    private fun speakWithElevenLabs(text: String, onDone: (() -> Unit)?) {
        val client = elevenLabs ?: run {
            Log.w(TAG, "ElevenLabs client es null, fallback a nativo")
            speakWithNativeTts(text, onDone)
            return
        }

        scope.launch {
            try {
                client.speak(
                    text = text,
                    onStart = { /* ya se llamó onSpeakStart arriba */ },
                    onDone = {
                        mainHandler.post { finishSpeaking(onDone) }
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error con ElevenLabs, fallback a TTS nativo: ${e.message}")
                mainHandler.post {
                    // Fallback automático a TTS nativo si ElevenLabs falla
                    speakWithNativeTts(text, onDone)
                }
            }
        }
    }

    /** Detener cualquier reproducción activa */
    fun stopSpeaking() {
        tts?.stop()
        elevenLabs?.stopPlaying()
        if (isSpeaking) {
            isSpeaking = false
            onSpeakEnd?.invoke()
        }
    }

    private fun finishSpeaking(onDone: (() -> Unit)?) {
        isSpeaking = false
        onSpeakEnd?.invoke()
        onDone?.invoke()
        shouldRestart = true
        startListening()
    }

    private fun cleanTextForSpeech(text: String): String {
        return text
            .replace(Regex("\\*\\*(.*?)\\*\\*"), "$1")
            .replace(Regex("\\*(.*?)\\*"), "$1")
            .replace(Regex("#{1,6}\\s"), "")
            .trim()
    }

    fun destroy() {
        shouldRestart = false
        mainHandler.post {
            recognizer?.destroy()
            recognizer = null
        }
        tts?.stop()
        tts?.shutdown()
        tts = null
        elevenLabs?.destroy()
        elevenLabs = null
    }
}
