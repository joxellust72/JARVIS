package com.visionsoldier.app.data.dao

import androidx.room.*
import com.visionsoldier.app.data.entity.DiaryEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface DiaryDao {

    @Insert
    suspend fun insert(entry: DiaryEntry): Long

    @Update
    suspend fun update(entry: DiaryEntry)

    @Delete
    suspend fun delete(entry: DiaryEntry)

    @Query("DELETE FROM diary WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM diary ORDER BY date DESC")
    fun getAllFlow(): Flow<List<DiaryEntry>>

    @Query("SELECT * FROM diary ORDER BY date DESC")
    suspend fun getAll(): List<DiaryEntry>

    @Query("SELECT * FROM diary ORDER BY importance DESC, date DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 8): List<DiaryEntry>

    @Query("SELECT * FROM diary WHERE id = :id")
    suspend fun getById(id: Long): DiaryEntry?

    @Query("SELECT COUNT(*) FROM diary")
    suspend fun count(): Int
}
