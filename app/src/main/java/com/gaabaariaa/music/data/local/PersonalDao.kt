package com.gaabaariaa.music.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonalDao {
    @Query("SELECT songId FROM favorites")
    fun observeFavoriteIds(): Flow<List<Long>>

    @Query("SELECT COUNT(*) FROM favorites WHERE songId = :songId")
    suspend fun favoriteCount(songId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(entity: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE songId = :songId")
    suspend fun removeFavorite(songId: Long)

    @Insert
    suspend fun addPlay(entity: PlayEntity)

    @Query("SELECT * FROM songs WHERE dateAdded > 0 ORDER BY dateAdded DESC LIMIT :limit")
    fun observeRecentlyAdded(limit: Int): Flow<List<SongEntity>>

    @Query(
        "SELECT s.* FROM songs s JOIN (SELECT songId, MAX(playedAt) AS lastPlayed FROM play_history GROUP BY songId) h " +
            "ON h.songId = s.mediaStoreId ORDER BY h.lastPlayed DESC LIMIT :limit"
    )
    fun observeRecentlyPlayed(limit: Int): Flow<List<SongEntity>>

    @Query(
        "SELECT s.* FROM songs s JOIN (SELECT songId, COUNT(*) AS plays FROM play_history GROUP BY songId) h " +
            "ON h.songId = s.mediaStoreId ORDER BY h.plays DESC, s.title COLLATE NOCASE LIMIT :limit"
    )
    fun observeMostPlayed(limit: Int): Flow<List<SongEntity>>

    @Query(
        "SELECT s.* FROM songs s JOIN favorites f ON f.songId = s.mediaStoreId ORDER BY f.addedAt DESC LIMIT :limit"
    )
    fun observeFavorites(limit: Int): Flow<List<SongEntity>>

    @Query(
        "SELECT s.* FROM songs s JOIN downloads d ON d.songId = s.mediaStoreId " +
            "WHERE d.state = 'COMPLETED' ORDER BY d.createdAt DESC LIMIT :limit"
    )
    fun observeRecentlyDownloaded(limit: Int): Flow<List<SongEntity>>
}
