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

data class ArtistSummary(val name: String, val songCount: Int, val albumCount: Int, val coverSongId: Long = 0)

data class AlbumSummary(val name: String, val artist: String, val songCount: Int, val coverSongId: Long = 0)

data class GenreSummary(val name: String, val songCount: Int, val coverSongId: Long = 0)

data class FolderSummary(val path: String, val songCount: Int, val coverSongId: Long = 0)

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
    val jamendoClientId: String = "",
    val cornerRadiusDp: Int = 16,
    val cardShape: CardShape = CardShape.ROUNDED,
    val textScale: Float = 1f,
    val libraryLayout: LibraryLayout = LibraryLayout.LIST,
    val gridColumns: Int = 2,
    val showArtwork: Boolean = true,
    val animationsEnabled: Boolean = true,
    val miniPlayerEnabled: Boolean = true,
    val keepScreenOn: Boolean = false,
    val lyricsFirst: Boolean = false,
    val libraryTabs: List<LibraryTab> = LibraryTab.entries.toList(),
    val homeSections: List<HomeSectionConfig> = defaultHomeSections(),
    val language: AppLanguage = AppLanguage.SYSTEM
)

enum class AppLanguage { SYSTEM, FA, EN }

/** Language-neutral license placeholders; the UI turns them into translated text. */
object LicenseCodes {
    const val SEE_ITEM_PAGE = "@see_item_page"
    const val SEE_SOURCE_PAGE = "@see_source_page"
    const val FREE_LICENSE = "@free_license"
    const val CREATIVE_COMMONS = "@creative_commons"
    const val FEED_PUBLISHER = "@feed_publisher"
    const val USER_LINK = "@user_link"
}

enum class CardShape { ROUNDED, CUT }

enum class LibraryLayout { LIST, GRID }

enum class LibraryTab { SONGS, ARTISTS, ALBUMS, GENRES, FOLDERS, PLAYLISTS }

enum class HomeSection {
    RECENTLY_PLAYED, RECENTLY_ADDED, MOST_PLAYED, FAVORITES, RECENTLY_DOWNLOADED, PLAYLISTS,
    ARTISTS, ALBUMS, GENRES, MISSING_METADATA, MISSING_ARTWORK, MISSING_LYRICS
}

data class HomeSectionConfig(val section: HomeSection, val enabled: Boolean)

fun defaultHomeSections(): List<HomeSectionConfig> = HomeSection.entries.map {
    HomeSectionConfig(
        it,
        enabled = it !in setOf(
            HomeSection.GENRES, HomeSection.MISSING_METADATA, HomeSection.MISSING_ARTWORK, HomeSection.MISSING_LYRICS
        )
    )
}

/** "SECTION:1,OTHER:0"; unknown names are dropped and sections missing from the text are appended. */
fun encodeHomeSections(list: List<HomeSectionConfig>): String =
    list.joinToString(",") { it.section.name + ":" + (if (it.enabled) "1" else "0") }

fun decodeHomeSections(text: String?): List<HomeSectionConfig> {
    val defaults = defaultHomeSections()
    if (text.isNullOrBlank()) return defaults
    val parsed = text.split(',').mapNotNull { entry ->
        val parts = entry.split(':')
        val section = HomeSection.entries.firstOrNull { it.name == parts.getOrNull(0) } ?: return@mapNotNull null
        HomeSectionConfig(section, parts.getOrNull(1) == "1")
    }.distinctBy { it.section }
    return parsed + defaults.filter { d -> parsed.none { it.section == d.section } }
}

fun encodeLibraryTabs(list: List<LibraryTab>): String = list.joinToString(",") { it.name }

fun decodeLibraryTabs(text: String?): List<LibraryTab> {
    if (text.isNullOrBlank()) return LibraryTab.entries.toList()
    val parsed = text.split(',').mapNotNull { name -> LibraryTab.entries.firstOrNull { it.name == name } }.distinct()
    return parsed + LibraryTab.entries.filter { it !in parsed }
}

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

enum class SmartRuleType {
    RECENTLY_ADDED, MOST_PLAYED, NEVER_PLAYED, FAVORITES, GENRE, PERSIAN, HIGH_QUALITY, LONGER_THAN
}

/** A smart playlist is a rule, not a list: its songs are recalculated whenever the library changes. */
data class SmartRule(val type: SmartRuleType, val param: String = "") {
    fun encode(): String = type.name + ":" + param
}

fun decodeSmartRule(text: String?): SmartRule? {
    if (text.isNullOrBlank()) return null
    val type = SmartRuleType.entries.firstOrNull { it.name == text.substringBefore(':') } ?: return null
    return SmartRule(type, text.substringAfter(':', ""))
}

data class Playlist(
    val id: Long,
    val name: String,
    val smartRule: SmartRule?,
    /** -1 for smart playlists, whose size is only known when they are opened. */
    val songCount: Int,
    val coverSongId: Long
) {
    val isSmart: Boolean get() = smartRule != null
}
