package com.gaabaariaa.music.domain.repository

import com.gaabaariaa.music.domain.model.Accent
import com.gaabaariaa.music.domain.model.AlbumSummary
import com.gaabaariaa.music.domain.model.AppSettings
import com.gaabaariaa.music.domain.model.ArtworkSearchResult
import com.gaabaariaa.music.domain.model.AuditState
import com.gaabaariaa.music.domain.model.DownloadItem
import com.gaabaariaa.music.domain.model.DownloadableTrack
import com.gaabaariaa.music.domain.model.SourceInfo
import com.gaabaariaa.music.domain.model.SourceSearchOutcome
import com.gaabaariaa.music.domain.model.HealthIssue
import com.gaabaariaa.music.domain.model.LibraryHealth
import com.gaabaariaa.music.domain.model.Lyrics
import com.gaabaariaa.music.domain.model.OnlineLyricsResult
import com.gaabaariaa.music.domain.model.SearchResults
import com.gaabaariaa.music.domain.model.ArtistSummary
import com.gaabaariaa.music.domain.model.FolderSummary
import com.gaabaariaa.music.domain.model.GenreSummary
import com.gaabaariaa.music.domain.model.ScanState
import com.gaabaariaa.music.domain.model.Song
import com.gaabaariaa.music.domain.model.ThemeMode
import android.content.IntentSender
import com.gaabaariaa.music.domain.model.TagValues
import com.gaabaariaa.music.domain.model.TagWriteResult
import kotlinx.coroutines.flow.Flow

interface LibraryRepository {
    fun observeSongs(): Flow<List<Song>>
    fun observeArtists(): Flow<List<ArtistSummary>>
    fun observeAlbums(): Flow<List<AlbumSummary>>
    fun observeGenres(): Flow<List<GenreSummary>>
    fun observeFolders(): Flow<List<FolderSummary>>
    suspend fun getSong(id: Long): Song?

    /** Android 11+ system dialog that asks the user to confirm deleting these files; null on older versions. */
    fun createDeleteRequest(songIds: List<Long>): IntentSender?

    /** Deletes files directly (Android 10 and older). Returns how many were deleted. */
    suspend fun deleteDirect(songIds: List<Long>): Int

    /** Removes songs from the library database after their files are gone. */
    suspend fun forget(songIds: List<Long>)
    fun observeSongsByArtist(artist: String): Flow<List<Song>>
    fun observeSongsByAlbum(album: String): Flow<List<Song>>
    fun observeSongsByGenre(genre: String): Flow<List<Song>>
    fun observeSongsByFolder(folder: String): Flow<List<Song>>
    fun observeAlbumsByArtist(artist: String): Flow<List<AlbumSummary>>

    /** Scans MediaStore, updates the database and returns the number of songs found. */
    suspend fun scan(): Int
}

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setDynamicColor(enabled: Boolean)
    suspend fun setAmoled(enabled: Boolean)
    suspend fun setAccent(accent: Accent)
    suspend fun setDownloadWifiOnly(enabled: Boolean)
    suspend fun setJamendoClientId(id: String)
}

/** Runs library scans in the background and reports their progress. */
interface LibraryScanner {
    val state: Flow<ScanState>
    fun scanNow(force: Boolean = false)
    fun schedulePeriodic()
}

interface TagRepository {
    /** Reads tags straight from the files. Songs that cannot be read are missing from the result. */
    suspend fun read(songIds: List<Long>): Map<Long, TagValues>

    fun hasWriteAccess(songIds: List<Long>): Boolean

    /** Android 11+ system dialog asking the user to allow modifying these files; null on older versions. */
    fun createWriteRequest(songIds: List<Long>): IntentSender?

    /**
     * Writes only the given fields (an empty value clears the field). Each file is backed up first,
     * verified after writing, and restored from the backup if anything goes wrong.
     */
    suspend fun write(songIds: List<Long>, changes: TagValues): TagWriteResult
}

interface ArtworkRepository {
    /** Looks up releases on MusicBrainz. Needs a connection; reports OFFLINE otherwise. */
    suspend fun search(title: String, artist: String, album: String): ArtworkSearchResult

    /** Cover Art Archive image (250 or 500 px) for a release, or null if it has none. Cached in memory. */
    suspend fun fetchImage(releaseId: String, sizePx: Int): ByteArray?

    /** Replaces the embedded pictures of the songs with this image (JPEG or PNG). */
    suspend fun embed(songIds: List<Long>, image: ByteArray): TagWriteResult

    suspend fun removeEmbedded(songIds: List<Long>): TagWriteResult

    /** Saves the image as a normal picture in Pictures/Musiq. Android 10+ only. */
    suspend fun saveToPictures(image: ByteArray, name: String): Boolean
}

interface LyricsRepository {
    /** Saved lyrics if there are any, otherwise the lyrics embedded in the file, otherwise null. */
    fun observe(songId: Long): Flow<Lyrics?>

    suspend fun get(songId: Long): Lyrics?

    /** Stores lyrics in the library database (blank content removes them). */
    suspend fun save(songId: Long, content: String, source: String)

    suspend fun delete(songId: Long)

    /** Reads a text file chosen by the user (UTF-8, UTF-16 or Windows-1256). */
    suspend fun readText(uri: String): String?

    suspend fun fetchOnline(title: String, artist: String, album: String, durationMs: Long): OnlineLyricsResult
}

interface SearchRepository {
    suspend fun search(query: String): SearchResults
}

interface HealthRepository {
    fun observeHealth(): Flow<LibraryHealth>
    fun observeSongs(issue: HealthIssue): Flow<List<Song>>
}

/** Inspects the files in the background for embedded artwork and lyrics. */
interface LibraryAuditor {
    val state: Flow<AuditState>
    fun start()
}

/** Legal music sources (free licenses, public domain, or links the user supplies). */
interface MusicSourceRepository {
    val sources: List<SourceInfo>

    /** [sourceId] "all" searches every source that works from a text query. */
    suspend fun search(sourceId: String, query: String): SourceSearchOutcome
}

interface DownloadRepository {
    fun observe(): Flow<List<DownloadItem>>
    suspend fun enqueue(track: DownloadableTrack): Long
    suspend fun pause(id: Long)
    suspend fun resume(id: Long)
    suspend fun cancel(id: Long)
    suspend fun retry(id: Long)
    suspend fun clearFinished()

    /** Re-schedules downloads that were running when the process died. */
    suspend fun reconcile()
}
