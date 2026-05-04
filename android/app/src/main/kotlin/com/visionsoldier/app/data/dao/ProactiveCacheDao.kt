package com.visionsoldier.app.data.dao

import androidx.room.*
import com.visionsoldier.app.data.entity.ProactiveCache

@Dao
interface ProactiveCacheDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(cache: ProactiveCache)

    @Query("SELECT * FROM proactive_cache WHERE type = :type")
    suspend fun get(type: String): ProactiveCache?

    @Query("DELETE FROM proactive_cache")
    suspend fun clearAll()
}
