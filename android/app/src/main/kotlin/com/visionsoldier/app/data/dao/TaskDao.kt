package com.visionsoldier.app.data.dao

import androidx.room.*
import com.visionsoldier.app.data.entity.Task
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {

    @Insert
    suspend fun insert(task: Task): Long

    @Update
    suspend fun update(task: Task)

    @Delete
    suspend fun delete(task: Task)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM tasks ORDER BY completed ASC, timestamp DESC")
    fun getAllFlow(): Flow<List<Task>>

    @Query("SELECT * FROM tasks ORDER BY completed ASC, timestamp DESC")
    suspend fun getAll(): List<Task>

    @Query("SELECT * FROM tasks WHERE completed = 0 ORDER BY timestamp DESC")
    suspend fun getActive(): List<Task>

    @Query("UPDATE tasks SET completed = :completed WHERE id = :id")
    suspend fun setCompleted(id: Long, completed: Boolean)

    @Query("SELECT COUNT(*) FROM tasks")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM tasks WHERE completed = 1")
    suspend fun countCompleted(): Int
}
