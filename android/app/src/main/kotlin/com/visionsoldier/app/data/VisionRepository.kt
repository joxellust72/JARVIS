package com.visionsoldier.app.data

import android.content.Context
import androidx.room.withTransaction
import com.visionsoldier.app.data.dao.*
import com.visionsoldier.app.data.entity.*
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.*

/**
 * Repositorio unificado — punto de acceso a toda la persistencia de VISION SOLDIER.
 * Equivalente al `VisionDB` (IndexedDB) del web app, pero con Room.
 */
class VisionRepository private constructor(context: Context) {

    private val db = VisionDatabase.getInstance(context)
    private val diaryDao: DiaryDao = db.diaryDao()
    private val taskDao: TaskDao = db.taskDao()
    private val conversationDao: ConversationDao = db.conversationDao()
    private val cacheDao: ProactiveCacheDao = db.proactiveCacheDao()
    private val profileDao: ProfileDao = db.profileDao()

    private fun now(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())

    private fun today(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    // ─── PERFIL ───────────────────────────────────────────────

    fun profileFlow(): Flow<Profile?> = profileDao.getProfileFlow()
    suspend fun getProfile(): Profile? = profileDao.getProfile()
    suspend fun updateProfile(profile: Profile) = profileDao.upsert(profile)

    // ─── DIARIO ───────────────────────────────────────────────

    suspend fun addDiaryEntry(
        content: String,
        title: String = "",
        category: String = "personal",
        importance: Int = 3,
        tags: String = "",
    ): Long = diaryDao.insert(
        DiaryEntry(
            date = now(),
            title = title,
            content = content,
            category = category,
            importance = importance,
            tags = tags,
        )
    )

    fun diaryFlow(): Flow<List<DiaryEntry>> = diaryDao.getAllFlow()
    suspend fun getAllDiary(): List<DiaryEntry> = diaryDao.getAll()
    suspend fun getRecentDiary(limit: Int = 8): List<DiaryEntry> = diaryDao.getRecent(limit)
    suspend fun deleteDiaryEntry(id: Long) = diaryDao.deleteById(id)
    suspend fun updateDiaryEntry(entry: DiaryEntry) = diaryDao.update(entry)

    /** Construye contexto del diario para inyectar en el system prompt de Gemini */
    suspend fun buildDiaryContext(): String {
        val entries = diaryDao.getRecent(8)
        if (entries.isEmpty()) return ""
        return entries.joinToString("\n") { entry ->
            val stars = "⭐".repeat(entry.importance.coerceIn(1, 5))
            val cat = entry.category.uppercase()
            val titlePart = if (entry.title.isNotBlank()) "${entry.title} — " else ""
            "[${entry.date}] $stars $cat: $titlePart${entry.content}"
        }
    }

    // ─── TAREAS ───────────────────────────────────────────────

    suspend fun addTask(text: String): Long = taskDao.insert(
        Task(text = text, timestamp = now())
    )

    fun tasksFlow(): Flow<List<Task>> = taskDao.getAllFlow()
    suspend fun getAllTasks(): List<Task> = taskDao.getAll()
    suspend fun getActiveTasks(): List<Task> = taskDao.getActive()
    suspend fun toggleTask(id: Long, completed: Boolean) = taskDao.setCompleted(id, completed)
    suspend fun deleteTask(id: Long) = taskDao.deleteById(id)

    // ─── CONVERSACIONES ───────────────────────────────────────

    suspend fun addMessage(role: String, content: String, sessionId: String = "default"): Long =
        conversationDao.insert(
            Conversation(
                sessionId = sessionId,
                role = role,
                content = content,
                timestamp = now(),
            )
        )

    suspend fun getRecentMessages(limit: Int = 20): List<Conversation> =
        conversationDao.getRecent(limit)

    suspend fun getAllMessages(): List<Conversation> = conversationDao.getAll()

    suspend fun getMessageCount(): Int = conversationDao.count()
    suspend fun trimOldMessages(keep: Int = 5000) = conversationDao.trimOld(keep)

    // ─── CACHÉ PROACTIVO ──────────────────────────────────────

    suspend fun getCachedComment(type: String): ProactiveCache? {
        val cached = cacheDao.get(type) ?: return null
        return if (cached.dateGenerated == today()) cached else null
    }

    suspend fun cacheComment(type: String, title: String, content: String) {
        cacheDao.upsert(
            ProactiveCache(
                type = type,
                title = title,
                content = content,
                dateGenerated = today(),
            )
        )
    }

    // ─── ESTADÍSTICAS ─────────────────────────────────────────

    suspend fun getStats(): Map<String, Int> = mapOf(
        "diaryCount" to (diaryDao.count()),
        "tasksCount" to (taskDao.count()),
        "tasksCompleted" to (taskDao.countCompleted()),
        "messagesCount" to (conversationDao.count()),
    )

    // ─── BACKUP / RESTORE ─────────────────────────────────────

    suspend fun clearAllData() {
        db.clearAllTables()
    }

    suspend fun restoreData(
        profile: Profile?,
        diaryEntries: List<DiaryEntry>,
        tasks: List<Task>
    ) {
        db.withTransaction {
            db.clearAllTables()
            if (profile != null) {
                db.profileDao().upsert(profile)
            }
            for (entry in diaryEntries) {
                db.diaryDao().insert(entry)
            }
            for (task in tasks) {
                db.taskDao().insert(task)
            }
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: VisionRepository? = null

        fun getInstance(context: Context): VisionRepository {
            return INSTANCE ?: synchronized(this) {
                val instance = VisionRepository(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}
