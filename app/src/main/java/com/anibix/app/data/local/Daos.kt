package com.anibix.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CachedTitleDao {
    @Query("SELECT * FROM cached_titles ORDER BY cachedAt DESC")
    suspend fun all(): List<CachedTitleEntity>

    @Query("SELECT * FROM cached_titles WHERE id = :id")
    suspend fun byId(id: String): CachedTitleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<CachedTitleEntity>)

    @Query("DELETE FROM cached_titles")
    suspend fun clear()
}

@Dao
interface WatchHistoryDao {
    @Query("SELECT * FROM watch_history ORDER BY updatedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 20): Flow<List<WatchHistoryEntity>>

    @Query("SELECT * FROM watch_history WHERE titleId = :titleId")
    suspend fun forTitle(titleId: String): WatchHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: WatchHistoryEntity)

    @Query("DELETE FROM watch_history WHERE titleId = :titleId")
    suspend fun remove(titleId: String)
}

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<BookmarkEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM bookmarks WHERE titleId = :titleId)")
    fun observeIsBookmarked(titleId: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(item: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE titleId = :titleId")
    suspend fun remove(titleId: String)
}

@Dao
interface DownloadQueueDao {
    @Query("SELECT * FROM download_queue ORDER BY queuedAt ASC")
    fun observeAll(): Flow<List<DownloadQueueEntity>>

    @Query("SELECT * FROM download_queue")
    suspend fun all(): List<DownloadQueueEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(item: DownloadQueueEntity)

    @Query("DELETE FROM download_queue WHERE titleId = :titleId")
    suspend fun remove(titleId: String)
}
