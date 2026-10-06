package com.gaabaariaa.music.domain.repository

import com.gaabaariaa.music.domain.model.Accent
import com.gaabaariaa.music.domain.model.AlbumSummary
import com.gaabaariaa.music.domain.model.AppSettings
import com.gaabaariaa.music.domain.model.ArtworkSearchResult
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
