package com.visionsoldier.app.ui.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.visionsoldier.app.data.BackupManager
import com.visionsoldier.app.data.VisionRepository
import com.visionsoldier.app.data.entity.Conversation
import com.visionsoldier.app.data.entity.DiaryEntry
import com.visionsoldier.app.data.entity.Task
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class OrbState { LISTENING, WOKEN, THINKING, SPEAKING, IDLE }

data class ChatMessage(val role: String, val text: String)

data class UiState(
    val orbState: OrbState = OrbState.LISTENING,
    val statusText: String = "Di \"Jarvis\" para comenzar",
    val transcriptText: String = "",
    val messages: List<ChatMessage> = emptyList(),
    val fullHistory: List<Conversation> = emptyList(),
    val isHistoryLoaded: Boolean = false,
    val isMinimized: Boolean = false,
    val isDrawerOpen: Boolean = false,
    val colorPalette: String = "light",
    // ── Datos ──
    val profile: com.visionsoldier.app.data.entity.Profile? = null,
    val diaryEntries: List<DiaryEntry> = emptyList(),
    val tasks: List<Task> = emptyList(),
    val currentView: String = "main",
    val isDiaryUnlocked: Boolean = false,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = VisionRepository.getInstance(application)
    private val prefs = application.getSharedPreferences("vision_prefs", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        // Cargar paleta guardada
        val savedPalette = prefs.getString("colorPalette", "light") ?: "light"
        _uiState.value = _uiState.value.copy(colorPalette = savedPalette)

        // Observar Room
        viewModelScope.launch {
            repository.diaryFlow().collect { entries ->
                _uiState.value = _uiState.value.copy(diaryEntries = entries)
            }
        }
        viewModelScope.launch {
            repository.tasksFlow().collect { tasks ->
                _uiState.value = _uiState.value.copy(tasks = tasks)
            }
        }
        viewModelScope.launch {
            repository.profileFlow().collect { prof ->
                val nextView = if (prof == null || prof.apiKey.isBlank()) {
                    "onboarding"
                } else if (_uiState.value.currentView == "onboarding") {
                    "main"
                } else {
                    _uiState.value.currentView
                }
                _uiState.value = _uiState.value.copy(profile = prof, currentView = nextView)
            }
        }

        // Cargar historial de conversación desde Room al iniciar
        viewModelScope.launch {
            val recent = repository.getRecentMessages(30)
            // getRecentMessages devuelve DESC — invertir para orden cronológico
            val history = recent.reversed().map { ChatMessage(it.role, it.content) }
            if (history.isNotEmpty()) {
                _uiState.value = _uiState.value.copy(messages = history, isDrawerOpen = false)
            }
        }
    }

    // ── Paleta de colores ────────────────────────────────────

    fun setPalette(name: String) {
        prefs.edit().putString("colorPalette", name).apply()
        _uiState.value = _uiState.value.copy(colorPalette = name)
    }

    // ── Perfil y Seguridad ───────────────────────────────────

    fun updateProfile(profile: com.visionsoldier.app.data.entity.Profile) {
        viewModelScope.launch { repository.updateProfile(profile) }
    }

    fun unlockDiary() {
        _uiState.value = _uiState.value.copy(isDiaryUnlocked = true)
    }

    fun lockDiary() {
        _uiState.value = _uiState.value.copy(isDiaryUnlocked = false)
    }

    // ── Estado del orbe ──────────────────────────────────────

    fun setOrbState(state: OrbState) {
        _uiState.value = _uiState.value.copy(orbState = state)
    }

    fun setStatus(text: String) {
        _uiState.value = _uiState.value.copy(statusText = text)
    }

    fun setTranscript(text: String) {
        _uiState.value = _uiState.value.copy(transcriptText = text)
    }

    fun addMessage(role: String, text: String) {
        val current = _uiState.value.messages.toMutableList()
        current.add(ChatMessage(role, text))
        _uiState.value = _uiState.value.copy(messages = current, isDrawerOpen = true)
    }

    fun toggleDrawer() {
        _uiState.value = _uiState.value.copy(isDrawerOpen = !_uiState.value.isDrawerOpen)
    }

    fun setMinimized(minimized: Boolean) {
        _uiState.value = _uiState.value.copy(isMinimized = minimized)
    }

    fun setCurrentView(view: String) {
        _uiState.value = _uiState.value.copy(currentView = view)
    }

    fun onWakeWord(command: String) {
        setOrbState(OrbState.WOKEN)
        setStatus(if (command.isNotEmpty()) "\"$command\"" else "¿En qué puedo ayudarle, señor?")
        setTranscript("")
    }

    fun onProcessing() {
        setOrbState(OrbState.THINKING)
        setStatus("Procesando...")
        setTranscript("")
    }

    fun onSpeaking(response: String) {
        setOrbState(OrbState.SPEAKING)
        setStatus("VISION habla...")
        addMessage("vision", response)
    }

    fun onListening() {
        setOrbState(OrbState.LISTENING)
        setStatus("Di \"Jarvis\" para comenzar")
        setTranscript("")
    }

    // ── Historial completo ─────────────────────────────────

    fun loadFullHistory() {
        if (_uiState.value.isHistoryLoaded) return
        viewModelScope.launch {
            val all = repository.getAllMessages()
            _uiState.value = _uiState.value.copy(fullHistory = all, isHistoryLoaded = true)
        }
    }

    // ── Diario ──────────────────────────────────────────────

    fun addDiaryEntry(content: String, title: String = "", category: String = "personal", importance: Int = 3) {
        viewModelScope.launch {
            repository.addDiaryEntry(content, title, category, importance)
        }
    }

    fun deleteDiaryEntry(id: Long) {
        viewModelScope.launch { repository.deleteDiaryEntry(id) }
    }

    // ── Tareas ──────────────────────────────────────────────

    fun addTask(text: String) {
        viewModelScope.launch { repository.addTask(text) }
    }

    fun toggleTask(id: Long, completed: Boolean) {
        viewModelScope.launch { repository.toggleTask(id, completed) }
    }

    fun deleteTask(id: Long) {
        viewModelScope.launch { repository.deleteTask(id) }
    }

    // ── Copia de Seguridad ───────────────────────────────────

    suspend fun exportToFile(uri: Uri, password: String, context: Context): Boolean {
        return try {
            val profile = repository.getProfile()
            val diary = repository.getAllDiary()
            val tasks = repository.getAllTasks()
            val encryptedData = BackupManager.exportData(profile, diary, tasks, password)
            context.contentResolver.openOutputStream(uri)?.use { it.write(encryptedData.toByteArray(Charsets.UTF_8)) }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun importFromFile(uri: Uri, password: String, context: Context): Boolean {
        return try {
            val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: return false
            val backupData = BackupManager.importData(content, password)
            if (backupData != null) {
                repository.restoreData(backupData.profile, backupData.diary, backupData.tasks)
                true
            } else false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
