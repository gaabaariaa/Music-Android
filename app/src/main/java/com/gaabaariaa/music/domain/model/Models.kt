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
    val path: String,
    val dateAdded: Long = 0L,
    val folder: String = ""
)

data class ArtistSummary(val name: String, val songCount: Int, val albumCount: Int)

data class AlbumSummary(val name: String, val artist: String, val songCount: Int)

data class GenreSummary(val name: String, val songCount: Int)

data class FolderSummary(val path: String, val songCount: Int)

data class ScanState(
    val scanning: Boolean = false,
    val lastCount: Int? = null,
    val failed: Boolean = false
)

enum class SongSort { TITLE, ARTIST, ALBUM, DATE_ADDED, DURATION }

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class Accent { PURPLE, BLUE, GREEN, ORANGE, RED }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val amoled: Boolean = false,
    val accent: Accent = Accent.PURPLE
)

enum class TagField { TITLE, ARTIST, ALBUM, ALBUM_ARTIST, GENRE, YEAR, TRACK, DISC, COMPOSER, COMMENT, LYRICS }

typealias TagValues = Map<TagField, String>

data class TagWriteResult(val saved: Int, val failed: Int, val permissionDenied: Boolean = false)
