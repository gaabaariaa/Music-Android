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
    val folder: String = "",
    val bitrate: Int = 0
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
    val accent: Accent = Accent.PURPLE,
    val downloadWifiOnly: Boolean = false,
    val jamendoClientId: String = ""
)

enum class TagField { TITLE, ARTIST, ALBUM, ALBUM_ARTIST, GENRE, YEAR, TRACK, DISC, COMPOSER, COMMENT, LYRICS }

typealias TagValues = Map<TagField, String>

data class TagWriteResult(val saved: Int, val failed: Int, val permissionDenied: Boolean = false)

data class ArtworkCandidate(val releaseId: String, val title: String, val artist: String, val year: String)

enum class ArtworkError { OFFLINE, SERVER }

data class ArtworkSearchResult(val candidates: List<ArtworkCandidate>, val error: ArtworkError? = null)

data class Lyrics(val content: String, val synced: Boolean, val source: String)

sealed interface OnlineLyricsResult {
    data class Found(val lyrics: Lyrics) : OnlineLyricsResult
    data object NotFound : OnlineLyricsResult
    data object Offline : OnlineLyricsResult
    data object Error : OnlineLyricsResult
}

object LyricsSource {
    const val LOCAL = "local"
    const val EMBEDDED = "embedded"
    const val ONLINE = "online"
    const val IMPORTED = "imported"
}

data class LyricsMatch(val song: Song, val snippet: String)

data class SearchResults(
    val songs: List<Song> = emptyList(),
    val artists: List<ArtistSummary> = emptyList(),
    val albums: List<AlbumSummary> = emptyList(),
    val genres: List<GenreSummary> = emptyList(),
    val folders: List<FolderSummary> = emptyList(),
    val lyricMatches: List<LyricsMatch> = emptyList()
) {
    val isEmpty: Boolean
        get() = songs.isEmpty() && artists.isEmpty() && albums.isEmpty() &&
            genres.isEmpty() && folders.isEmpty() && lyricMatches.isEmpty()
}

enum class HealthIssue {
    MISSING_TITLE, MISSING_ARTIST, MISSING_ALBUM, MISSING_GENRE, UNRECOGNIZED,
    LOW_QUALITY, MISSING_ARTWORK, MISSING_LYRICS
}

data class LibraryHealth(
    val total: Int = 0,
    val missingTitle: Int = 0,
    val missingArtist: Int = 0,
    val missingAlbum: Int = 0,
    val missingGenre: Int = 0,
    val unrecognized: Int = 0,
    val lowQuality: Int = 0,
    val audited: Int = 0,
    val missingArtwork: Int = 0,
    val missingLyrics: Int = 0,
    val duplicateGroups: Int = 0,
    /** Genre and bitrate come from MediaStore columns that only exist on Android 11+. */
    val extendedInfoSupported: Boolean = true
) {
    /**
     * Share of passed checks: title, artist and album for every song (plus genre when available),
     * and artwork and lyrics for the songs that have been inspected.
     */
    val score: Int
        get() {
            val perSong = if (extendedInfoSupported) 4 else 3
            val checks = total * perSong + audited * 2
            if (checks == 0) return 100
            val failed = missingTitle + missingArtist + missingAlbum +
                (if (extendedInfoSupported) missingGenre else 0) + missingArtwork + missingLyrics
            return ((checks - failed).coerceAtLeast(0) * 100) / checks
        }
}

data class AuditState(val running: Boolean = false, val done: Int = 0, val total: Int = 0)

enum class DownloadState { QUEUED, DOWNLOADING, PAUSED, COMPLETED, FAILED }

data class DownloadItem(
    val id: Long,
    val providerId: String,
    val title: String,
    val artist: String,
    val album: String,
    val license: String,
    val pageUrl: String,
    val state: DownloadState,
    val totalBytes: Long,
    val downloadedBytes: Long,
    val speedBps: Long,
    val error: String,
    val songId: Long,
    val createdAt: Long
)

/** One file offered by a legal source, with the license it is published under. */
data class DownloadableTrack(
    val providerId: String,
    val remoteId: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationSec: Int?,
    val license: String,
    val pageUrl: String,
    val downloadUrl: String,
    val coverUrl: String,
    val extension: String
)

enum class SourceInput { SEARCH, URL }

data class SourceInfo(val id: String, val name: String, val input: SourceInput)

enum class SourceError { OFFLINE, SERVER, NOT_CONFIGURED, INVALID_INPUT }

data class SourceSearchOutcome(
    val tracks: List<DownloadableTrack> = emptyList(),
    val errors: Map<String, SourceError> = emptyMap()
)
