package com.visionsoldier.app.data.dao

import androidx.room.*
import com.visionsoldier.app.data.entity.Conversation

@Dao
interface ConversationDao {

    @Insert
    suspend fun insert(msg: Conversation): Long

    @Query("SELECT * FROM conversations ORDER BY timestamp ASC")
    suspend fun getAll(): List<Conversation>

    @Query("SELECT * FROM conversations ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 20): List<Conversation>

    @Query("SELECT COUNT(*) FROM conversations")
    suspend fun count(): Int

    /** Mantener solo las últimas [keep] conversaciones, borrar el resto */
    @Query("DELETE FROM conversations WHERE id NOT IN (SELECT id FROM conversations ORDER BY timestamp DESC LIMIT :keep)")
    suspend fun trimOld(keep: Int = 100)

    @Query("DELETE FROM conversations")
    suspend fun deleteAll()
}
