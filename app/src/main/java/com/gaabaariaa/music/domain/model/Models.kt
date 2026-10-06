package com.gaabaariaa.music.domain.model

data class Song(
    val id: Long,
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

data class ArtistSummary(val name: String, val songCount: Int, val albumCount: Int)

data class AlbumSummary(val name: String, val artist: String, val songCount: Int)

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class Accent { PURPLE, BLUE, GREEN, ORANGE, RED }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val amoled: Boolean = false,
    val accent: Accent = Accent.PURPLE
)
