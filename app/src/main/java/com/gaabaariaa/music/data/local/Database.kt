package com.gaabaariaa.music.data.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "songs",
    indices = [Index("artist"), Index("album"), Index("genre"), Index("folder")]
)
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
    val path: String,
    @ColumnInfo(defaultValue = "0") val dateAdded: Long = 0L,
    @ColumnInfo(defaultValue = "''") val folder: String = ""
)

data class ArtistRow(val name: String, val songCount: Int, val albumCount: Int)

data class AlbumRow(val name: String, val artist: String, val songCount: Int)

data class GenreRow(val name: String, val songCount: Int)

data class FolderRow(val path: String, val songCount: Int)

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

    @Query("SELECT genre AS name, COUNT(*) AS songCount FROM songs GROUP BY genre ORDER BY genre COLLATE NOCASE")
    fun observeGenres(): Flow<List<GenreRow>>

    @Query("SELECT folder AS path, COUNT(*) AS songCount FROM songs GROUP BY folder ORDER BY folder COLLATE NOCASE")
    fun observeFolders(): Flow<List<FolderRow>>

    @Query("SELECT * FROM songs WHERE artist = :artist ORDER BY album COLLATE NOCASE, track, title COLLATE NOCASE")
    fun observeSongsByArtist(artist: String): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE album = :album ORDER BY track, title COLLATE NOCASE")
    fun observeSongsByAlbum(album: String): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE genre = :genre ORDER BY title COLLATE NOCASE")
    fun observeSongsByGenre(genre: String): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE folder = :folder ORDER BY title COLLATE NOCASE")
    fun observeSongsByFolder(folder: String): Flow<List<SongEntity>>

    @Query(
        "SELECT album AS name, MIN(artist) AS artist, COUNT(*) AS songCount FROM songs " +
            "WHERE artist = :artist GROUP BY album ORDER BY album COLLATE NOCASE"
    )
    fun observeAlbumsByArtist(artist: String): Flow<List<AlbumRow>>

    @Query("SELECT * FROM songs WHERE mediaStoreId = :id")
    suspend fun getById(id: Long): SongEntity?

    @Query("SELECT mediaStoreId FROM songs")
    suspend fun allIds(): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(songs: List<SongEntity>)

    @Query("DELETE FROM songs WHERE mediaStoreId IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)
}

@Entity(tableName = "lyrics")
data class LyricsEntity(
    @PrimaryKey val songId: Long,
    val content: String,
    val synced: Boolean,
    val source: String
)

@Dao
interface LyricsDao {
    @Query("SELECT * FROM lyrics WHERE songId = :songId")
    fun observe(songId: Long): Flow<LyricsEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: LyricsEntity)

    @Query("DELETE FROM lyrics WHERE songId = :songId")
    suspend fun delete(songId: Long)
}

@Database(entities = [SongEntity::class, LyricsEntity::class], version = 3, exportSchema = false)
abstract class MusicDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun lyricsDao(): LyricsDao
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE songs ADD COLUMN dateAdded INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE songs ADD COLUMN folder TEXT NOT NULL DEFAULT ''")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_songs_artist ON songs(artist)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_songs_album ON songs(album)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_songs_genre ON songs(genre)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_songs_folder ON songs(folder)")
        // Force the next scan to repopulate the new columns.
        db.execSQL("DELETE FROM songs")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS lyrics (" +
                "songId INTEGER NOT NULL, content TEXT NOT NULL, synced INTEGER NOT NULL, " +
                "source TEXT NOT NULL, PRIMARY KEY(songId))"
        )
    }
}
