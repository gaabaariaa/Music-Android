package com.gaabaariaa.music.domain.repository

import com.gaabaariaa.music.domain.model.Accent
import com.gaabaariaa.music.domain.model.AlbumSummary
import com.gaabaariaa.music.domain.model.AppSettings
import com.gaabaariaa.music.domain.model.ArtistSummary
import com.gaabaariaa.music.domain.model.Song
import com.gaabaariaa.music.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow

interface LibraryRepository {
    fun observeSongs(): Flow<List<Song>>
    fun observeArtists(): Flow<List<ArtistSummary>>
    fun observeAlbums(): Flow<List<AlbumSummary>>

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
