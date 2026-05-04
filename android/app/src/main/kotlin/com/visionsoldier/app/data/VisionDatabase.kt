package com.visionsoldier.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.visionsoldier.app.data.dao.*
import com.visionsoldier.app.data.entity.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        DiaryEntry::class,
        Task::class,
        Conversation::class,
        ProactiveCache::class,
        Profile::class,
    ],
    version = 4,
    exportSchema = false
)
abstract class VisionDatabase : RoomDatabase() {

    abstract fun diaryDao(): DiaryDao
    abstract fun taskDao(): TaskDao
    abstract fun conversationDao(): ConversationDao
    abstract fun proactiveCacheDao(): ProactiveCacheDao
    abstract fun profileDao(): ProfileDao

    companion object {
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE profile ADD COLUMN apiKey TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE profile ADD COLUMN voiceEngine TEXT NOT NULL DEFAULT 'native'")
                db.execSQL("ALTER TABLE profile ADD COLUMN elevenLabsApiKey TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE profile ADD COLUMN elevenLabsVoiceId TEXT NOT NULL DEFAULT ''")
            }
        }

        @Volatile
        private var INSTANCE: VisionDatabase? = null

        fun getInstance(context: Context): VisionDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    VisionDatabase::class.java,
                    "vision_soldier.db"
                )
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
