package com.gaabaariaa.music.data.local

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class LyricsMatchRow(@Embedded val song: SongEntity, val lyricContent: String)

data class HealthRow(
    val total: Int,
    val missingTitle: Int,
    val missingArtist: Int,
    val missingAlbum: Int,
    val missingGenre: Int,
    val unrecognized: Int,
    val lowQuality: Int
)

data class AuditRow(val audited: Int, val missingArtwork: Int, val missingLyrics: Int)

private const val AUDITED_JOIN =
    "FROM songs s JOIN song_audit a ON a.songId = s.mediaStoreId AND a.sizeBytes = s.sizeBytes"

@Dao
interface InsightsDao {
    // ---- global search (callers pass an already escaped "%text%" pattern) ----
    @Query(
        "SELECT * FROM songs WHERE title LIKE :q ESCAPE '\\' OR artist LIKE :q ESCAPE '\\' " +
            "OR album LIKE :q ESCAPE '\\' OR genre LIKE :q ESCAPE '\\' OR folder LIKE :q ESCAPE '\\' " +
            "ORDER BY title COLLATE NOCASE LIMIT :limit"
    )
    suspend fun searchSongs(q: String, limit: Int): List<SongEntity>

    @Query(
        "SELECT artist AS name, COUNT(*) AS songCount, COUNT(DISTINCT album) AS albumCount, MIN(mediaStoreId) AS coverSongId FROM songs " +
            "WHERE artist != '' AND artist LIKE :q ESCAPE '\\' GROUP BY artist ORDER BY artist COLLATE NOCASE LIMIT 30"
    )
    suspend fun searchArtists(q: String): List<ArtistRow>

    @Query(
        "SELECT album AS name, MIN(artist) AS artist, COUNT(*) AS songCount, MIN(mediaStoreId) AS coverSongId FROM songs " +
            "WHERE album != '' AND album LIKE :q ESCAPE '\\' GROUP BY album ORDER BY album COLLATE NOCASE LIMIT 30"
    )
    suspend fun searchAlbums(q: String): List<AlbumRow>

    @Query(
        "SELECT genre AS name, COUNT(*) AS songCount, MIN(mediaStoreId) AS coverSongId FROM songs " +
            "WHERE genre != '' AND genre LIKE :q ESCAPE '\\' GROUP BY genre ORDER BY genre COLLATE NOCASE LIMIT 30"
    )
    suspend fun searchGenres(q: String): List<GenreRow>

    @Query(
        "SELECT folder AS path, COUNT(*) AS songCount, MIN(mediaStoreId) AS coverSongId FROM songs " +
            "WHERE folder != '' AND folder LIKE :q ESCAPE '\\' GROUP BY folder ORDER BY folder COLLATE NOCASE LIMIT 30"
    )
    suspend fun searchFolders(q: String): List<FolderRow>

    @Query(
        "SELECT s.*, l.content AS lyricContent FROM songs s JOIN lyrics l ON l.songId = s.mediaStoreId " +
            "WHERE l.content LIKE :q ESCAPE '\\' ORDER BY s.title COLLATE NOCASE LIMIT 30"
    )
    suspend fun searchLyrics(q: String): List<LyricsMatchRow>

    // ---- library health ----
    @Query(
        "SELECT COUNT(*) AS total, " +
            "COALESCE(SUM(CASE WHEN title = '' THEN 1 ELSE 0 END), 0) AS missingTitle, " +
            "COALESCE(SUM(CASE WHEN artist = '' THEN 1 ELSE 0 END), 0) AS missingArtist, " +
            "COALESCE(SUM(CASE WHEN album = '' THEN 1 ELSE 0 END), 0) AS missingAlbum, " +
            "COALESCE(SUM(CASE WHEN genre = '' THEN 1 ELSE 0 END), 0) AS missingGenre, " +
            "COALESCE(SUM(CASE WHEN artist = '' AND album = '' THEN 1 ELSE 0 END), 0) AS unrecognized, " +
            "COALESCE(SUM(CASE WHEN bitrate > 0 AND bitrate < 128000 THEN 1 ELSE 0 END), 0) AS lowQuality " +
            "FROM songs"
    )
    fun observeHealth(): Flow<HealthRow>

    @Query(
        "SELECT COUNT(*) AS audited, " +
            "COALESCE(SUM(CASE WHEN a.hasArtwork = 0 THEN 1 ELSE 0 END), 0) AS missingArtwork, " +
            "COALESCE(SUM(CASE WHEN a.hasLyrics = 0 AND s.mediaStoreId NOT IN (SELECT songId FROM lyrics) " +
            "THEN 1 ELSE 0 END), 0) AS missingLyrics " + AUDITED_JOIN
    )
    fun observeAudit(): Flow<AuditRow>

    @Query("SELECT * FROM songs WHERE title = '' ORDER BY path")
    fun observeMissingTitle(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE artist = '' ORDER BY title COLLATE NOCASE")
    fun observeMissingArtist(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE album = '' ORDER BY title COLLATE NOCASE")
    fun observeMissingAlbum(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE genre = '' ORDER BY title COLLATE NOCASE")
    fun observeMissingGenre(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE artist = '' AND album = '' ORDER BY title COLLATE NOCASE")
    fun observeUnrecognized(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE bitrate > 0 AND bitrate < 128000 ORDER BY bitrate")
    fun observeLowQuality(): Flow<List<SongEntity>>

    @Query("SELECT s.* " + AUDITED_JOIN + " WHERE a.hasArtwork = 0 ORDER BY s.title COLLATE NOCASE")
    fun observeMissingArtwork(): Flow<List<SongEntity>>

    @Query(
        "SELECT s.* " + AUDITED_JOIN + " WHERE a.hasLyrics = 0 " +
            "AND s.mediaStoreId NOT IN (SELECT songId FROM lyrics) ORDER BY s.title COLLATE NOCASE"
    )
    fun observeMissingLyrics(): Flow<List<SongEntity>>

    // ---- audit ----
    @Query(
        "SELECT s.* FROM songs s LEFT JOIN song_audit a ON a.songId = s.mediaStoreId " +
            "WHERE a.songId IS NULL OR a.sizeBytes != s.sizeBytes"
    )
    suspend fun songsNeedingAudit(): List<SongEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAudits(items: List<AuditEntity>)
}
