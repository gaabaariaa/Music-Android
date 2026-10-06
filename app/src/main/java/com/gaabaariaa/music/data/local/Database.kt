package com.gaabaariaa.music.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "songs")
data class SongEntity(
    @PrimaryKey val mediaStoreId: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumArtist: String,
    val genre: String,
    val year: Int,
    val track: Int,
    val durationMs: Long,
    val sizeBytes: Long,
    val mimeType: String,
    val path: String
)

data class ArtistRow(val name: String, val songCount: Int, val albumCount: Int)

data class AlbumRow(val name: String, val artist: String, val songCount: Int)

@Dao
interface SongDao {
    @Query("SELECT * FROM songs ORDER BY title COLLATE NOCASE")
    fun observeSongs(): Flow<List<SongEntity>>

    @Query(
        "SELECT artist AS name, COUNT(*) AS songCount, COUNT(DISTINCT album) AS albumCount " +
            "FROM songs GROUP BY artist ORDER BY artist COLLATE NOCASE"
    )
    fun observeArtists(): Flow<List<ArtistRow>>

    @Query(
        "SELECT album AS name, MIN(artist) AS artist, COUNT(*) AS songCount " +
            "FROM songs GROUP BY album ORDER BY album COLLATE NOCASE"
    )
    fun observeAlbums(): Flow<List<AlbumRow>>

    @Query("SELECT mediaStoreId FROM songs")
    suspend fun allIds(): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(songs: List<SongEntity>)

    @Query("DELETE FROM songs WHERE mediaStoreId IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)
}

@Database(entities = [SongEntity::class], version = 1, exportSchema = false)
abstract class MusicDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
}
