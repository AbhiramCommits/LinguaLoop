package com.lingualoop.android.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        LessonEntity::class,
        SessionEntity::class,
        PendingOpEntity::class,
        CacheEntryEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class LinguaLoopDatabase : RoomDatabase() {
    abstract fun lessonDao(): LessonDao
    abstract fun sessionDao(): SessionDao
    abstract fun pendingOpDao(): PendingOpDao
    abstract fun cacheEntryDao(): CacheEntryDao

    companion object {
        fun build(context: Context): LinguaLoopDatabase =
            Room.databaseBuilder(context, LinguaLoopDatabase::class.java, "lingualoop.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
