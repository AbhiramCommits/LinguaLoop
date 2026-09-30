package com.lingualoop.android.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface LessonDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(lesson: LessonEntity)

    @Query("SELECT * FROM lesson_cache WHERE id = :id")
    suspend fun get(id: Long): LessonEntity?

    @Query("DELETE FROM lesson_cache WHERE id != :keepId")
    suspend fun evictAllExcept(keepId: Long)
}

@Dao
interface SessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(session: SessionEntity)

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun get(id: Long): SessionEntity?

    @Query("UPDATE sessions SET active = 0 WHERE id = :id")
    suspend fun markInactive(id: Long)
}

@Dao
interface PendingOpDao {
    @Insert
    suspend fun insert(op: PendingOpEntity): Long

    @Query("SELECT * FROM pending_ops ORDER BY id ASC")
    suspend fun getAllOrdered(): List<PendingOpEntity>

    @Query("SELECT COUNT(*) FROM pending_ops")
    suspend fun count(): Int

    @Query("DELETE FROM pending_ops WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface CacheEntryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: CacheEntryEntity)

    @Query("SELECT * FROM cache_entries WHERE key = :key")
    suspend fun get(key: String): CacheEntryEntity?
}
