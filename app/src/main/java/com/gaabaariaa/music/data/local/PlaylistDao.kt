package com.gaabaariaa.music.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class PlaylistStatRow(val playlistId: Long, val songCount: Int, val coverSongId: Long)

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY name COLLATE NOCASE")
    fun observePlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    fun observePlaylist(id: Long): Flow<PlaylistEntity?>

    @Query("SELECT playlistId, COUNT(*) AS songCount, MIN(songId) AS coverSongId FROM playlist_songs GROUP BY playlistId")
    fun observeStats(): Flow<List<PlaylistStatRow>>

    @Query(
        "SELECT s.* FROM songs s JOIN playlist_songs p ON p.songId = s.mediaStoreId " +
            "WHERE p.playlistId = :id ORDER BY p.position"
    )
    fun observeSongs(id: Long): Flow<List<SongEntity>>

    @Query("SELECT songId FROM playlist_songs WHERE playlistId = :id")
    suspend fun songIds(id: Long): List<Long>

    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_songs WHERE playlistId = :id")
    suspend fun maxPosition(id: Long): Int

    @Insert
    suspend fun insertPlaylist(entity: PlaylistEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSongs(items: List<PlaylistSongEntity>)

    @Query("UPDATE playlists SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylist(id: Long)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :id")
    suspend fun deleteAllSongs(id: Long)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :id AND songId = :songId")
    suspend fun removeSong(id: Long, songId: Long)

    @Query("UPDATE playlist_songs SET position = :position WHERE playlistId = :id AND songId = :songId")
    suspend fun setPosition(id: Long, songId: Long, position: Int)
}
