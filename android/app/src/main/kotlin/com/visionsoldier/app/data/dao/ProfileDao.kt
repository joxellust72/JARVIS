package com.visionsoldier.app.data.dao

import androidx.room.*
import com.visionsoldier.app.data.entity.Profile
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {

    @Query("SELECT * FROM profile WHERE id = 1")
    fun getProfileFlow(): Flow<Profile?>

    @Query("SELECT * FROM profile WHERE id = 1")
    suspend fun getProfile(): Profile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: Profile)
}
