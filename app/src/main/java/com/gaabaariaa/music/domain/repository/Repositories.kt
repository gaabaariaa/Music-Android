package com.gaabaariaa.music.domain.repository

import com.gaabaariaa.music.domain.model.Accent
import com.gaabaariaa.music.domain.model.AlbumSummary
import com.gaabaariaa.music.domain.model.AppSettings
import com.gaabaariaa.music.domain.model.ArtistSummary
import com.gaabaariaa.music.domain.model.FolderSummary
import com.gaabaariaa.music.domain.model.GenreSummary
import com.gaabaariaa.music.domain.model.ScanState
import com.gaabaariaa.music.domain.model.Song
import com.gaabaariaa.music.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow

interface LibraryRepository {
    fun observeSongs(): Flow<List<Song>>
    fun observeArtists(): Flow<List<ArtistSummary>>
    fun observeAlbums(): Flow<List<AlbumSummary>>
    fun observeGenres(): Flow<List<GenreSummary>>
    fun observeFolders(): Flow<List<FolderSummary>>

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
