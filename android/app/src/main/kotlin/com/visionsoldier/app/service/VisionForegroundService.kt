package com.visionsoldier.app.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.visionsoldier.app.MainActivity
import com.visionsoldier.app.R
import com.visionsoldier.app.VisionApp
import com.visionsoldier.app.ai.ChatMessage
import com.visionsoldier.app.ai.GeminiClient
import com.visionsoldier.app.commands.Command
import com.visionsoldier.app.commands.CommandRouter
import com.visionsoldier.app.commands.CommandType
import com.visionsoldier.app.commands.SystemActions
import com.visionsoldier.app.data.VisionRepository
import com.visionsoldier.app.proactive.ProactiveScheduler
import com.visionsoldier.app.speech.SpeechEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class VisionForegroundService : Service() {

    companion object {
        private const val TAG = "VisionService"
        private const val NOTIFICATION_ID = 1001

        // Clave por defecto — se sobreescribe con la del Perfil del usuario
        const val GEMINI_API_KEY = "AIzaSyC4Ks0506P_zSPuvLJUA8D1KMM6wrICn20"

        const val ACTION_WAKE_WORD = "vision.WAKE_WORD"
        const val ACTION_COMMAND   = "vision.COMMAND"
        const val ACTION_RESPONSE  = "vision.RESPONSE"
        const val ACTION_LISTENING = "vision.LISTENING"
        const val ACTION_SPEAKING  = "vision.SPEAKING"

        const val EXTRA_TEXT = "text"
    }

    inner class VisionBinder : Binder() {
        fun getService(): VisionForegroundService = this@VisionForegroundService
    }

    private val binder = VisionBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private lateinit var speechEngine: SpeechEngine
    private lateinit var geminiClient: GeminiClient
    private lateinit var commandRouter: CommandRouter
    private lateinit var systemActions: SystemActions
    private lateinit var repository: VisionRepository

    private val messageHistory = mutableListOf<ChatMessage>()
    var isProcessing = false
        private set

    var onWakeWordDetected: ((String) -> Unit)? = null
    var onResponseReady: ((String) -> Unit)? = null
    var onListening: (() -> Unit)? = null
    var onSpeaking: (() -> Unit)? = null

    // ── Receiver: comentarios proactivos ─────────────────────

    private val proactiveReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == "vision.PROACTIVE_COMMENT") {
                val title = intent.getStringExtra("title") ?: "VISION"
                val text  = intent.getStringExtra("text")  ?: return
                if (!isProcessing && !speechEngine.isSpeakingNow()) {
                    val fullText = "$title. $text"
                    broadcastAction(ACTION_RESPONSE, fullText)
                    onResponseReady?.invoke(fullText)
                    showBubbleText(title, text)
                    speechEngine.speak(fullText, null)
                }
            }
        }
    }

    // ── Receiver: resultados de AccessibilityService ──────────

    private val a11yReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val message = intent.getStringExtra("message") ?: return
            if (!isProcessing) {
                broadcastAction(ACTION_RESPONSE, message)
                onResponseReady?.invoke(message)
                showBubbleText("SISTEMA", message)
                speechEngine.speak(message, null)
            }
        }
    }

    // ── Ciclo de vida ─────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Servicio iniciando...")

        repository    = VisionRepository.getInstance(this)
        geminiClient  = GeminiClient(GEMINI_API_KEY)  // clave por defecto
        commandRouter = CommandRouter()
        systemActions = SystemActions(this)

        speechEngine = SpeechEngine(
            context    = this,
            onWakeWord = { command -> handleWakeWord(command) },
            onInterim  = { text -> broadcastAction(ACTION_LISTENING, text) },
            onError    = { err -> Log.w(TAG, "Speech error: $err") }
        )
        speechEngine.onSpeakStart = { broadcastAction(ACTION_SPEAKING, "") }
        speechEngine.onSpeakEnd   = { broadcastAction(ACTION_LISTENING, "") }

        speechEngine.initTts {
            speechEngine.startListening()
            Log.d(TAG, "TTS listo. Escuchando...")
        }

        // Observar cambios en el perfil: API key + motor de voz
        serviceScope.launch(Dispatchers.IO) {
            repository.profileFlow().collect { profile ->
                if (profile == null) return@collect

                // Actualizar Gemini API key
                val geminiKey = profile.apiKey.takeIf { it.isNotBlank() }
                if (geminiKey != null) {
                    geminiClient.updateApiKey(geminiKey)
                    Log.d(TAG, "Gemini API key cargada desde Perfil.")
                }

                // Configurar motor de voz (native / elevenlabs)
                speechEngine.setVoiceEngine(
                    engine = profile.voiceEngine,
                    elevenLabsApiKey = profile.elevenLabsApiKey,
                    elevenLabsVoiceId = profile.elevenLabsVoiceId,
                )
                Log.d(TAG, "Motor de voz configurado: ${profile.voiceEngine}")
            }
        }

        // Pre-cargar historial de conversación en memoria
        serviceScope.launch(Dispatchers.IO) {
            val recent = repository.getRecentMessages(20)
            val history = recent.reversed()
            messageHistory.addAll(history.map { ChatMessage(it.role, it.content) })
            Log.d(TAG, "Historial cargado: ${messageHistory.size} mensajes.")
        }



        registerProactiveReceiver()
        registerA11yReceiver()

        ProactiveScheduler.startRegularLoop(this)
        ProactiveScheduler.scheduleDebate(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onDestroy() {
        super.onDestroy()
        speechEngine.destroy()
        serviceScope.cancel()
        try { unregisterReceiver(proactiveReceiver) } catch (_: Exception) {}
        try { unregisterReceiver(a11yReceiver) } catch (_: Exception) {}
        Log.d(TAG, "Servicio detenido.")
    }

    // ── Procesamiento de comandos ─────────────────────────────

    private fun handleWakeWord(command: String) {
        Log.d(TAG, "Wake word! Comando: '$command'")
        onWakeWordDetected?.invoke(command)
        broadcastAction(ACTION_WAKE_WORD, command)
        showBubbleText("ESCUCHANDO", if (command.isBlank()) "..." else command)
        if (command.length > 2) processInput(command)
    }

    fun processInput(input: String) {
        if (isProcessing) return
        isProcessing = true

        serviceScope.launch {
            try {
                val command = commandRouter.route(input)

                when {
                    command.type in listOf(CommandType.ADD_DIARY, CommandType.ADD_TASK, CommandType.LIST_TASKS) -> {
                        val response = systemActions.executeSuspend(command) ?: "No entendí la solicitud, señor."
                        deliverResponse(input, response)
                    }

                    command.type == CommandType.AI_CHAT -> {
                        val diaryContext = repository.buildDiaryContext()
                        val enrichedInput = if (diaryContext.isNotBlank()) {
                            "[Contexto del diario del usuario]\n$diaryContext\n\n[Mensaje actual]\n$input"
                        } else input

                        val response = try {
                            geminiClient.ask(enrichedInput, messageHistory)
                        } catch (e: Exception) {
                            geminiClient.friendlyError(e)
                        }

                        messageHistory.add(ChatMessage("user", input))
                        messageHistory.add(ChatMessage("vision", response))
                        if (messageHistory.size > 20) messageHistory.removeAt(0)

                        repository.addMessage("user", input)
                        repository.addMessage("vision", response)
                        repository.trimOldMessages(5000)

                        deliverResponse(input, response)
                    }

                    else -> {
                        val result = systemActions.execute(command)
                        if (result.isNotEmpty()) deliverResponse(input, result)
                        else isProcessing = false
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error procesando input: ${e.message}")
                val errMsg = geminiClient.friendlyError(e)
                broadcastAction(ACTION_RESPONSE, errMsg)
                onResponseReady?.invoke(errMsg)
                showBubbleText("ERROR", errMsg)
                speechEngine.speak(errMsg) { isProcessing = false }
            }
        }
    }

    private fun deliverResponse(input: String, response: String) {
        broadcastAction(ACTION_RESPONSE, response)
        onResponseReady?.invoke(response)
        showBubbleText("VISION", response)
        speechEngine.speak(response) { isProcessing = false }
    }

    private fun showBubbleText(title: String, text: String) {
        if (!android.provider.Settings.canDrawOverlays(this)) return
        val intent = Intent(this, BubbleOverlayService::class.java).apply {
            action = BubbleOverlayService.ACTION_UPDATE
            putExtra(BubbleOverlayService.EXTRA_TITLE, title)
            putExtra(BubbleOverlayService.EXTRA_TEXT, text)
        }
        startService(intent)
    }

    // ── Notificación persistente ──────────────────────────────

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, VisionApp.CHANNEL_ID_SERVICE)
            .setContentTitle("VISION SOLDIER — Activo")
            .setContentText("Escuchando... di \"Jarvis\" para activar")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(openIntent)
            .build()
    }

    private fun broadcastAction(action: String, text: String) {
        sendBroadcast(Intent(action).putExtra(EXTRA_TEXT, text))
    }

    private fun registerProactiveReceiver() {
        val filter = IntentFilter("vision.PROACTIVE_COMMENT")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(proactiveReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(proactiveReceiver, filter)
        }
    }

    private fun registerA11yReceiver() {
        val filter = IntentFilter("vision.A11Y_RESULT")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(a11yReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(a11yReceiver, filter)
        }
    }
}
