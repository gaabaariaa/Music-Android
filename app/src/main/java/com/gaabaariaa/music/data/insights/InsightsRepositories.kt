package com.gaabaariaa.music.data.insights

import android.os.Build
import com.gaabaariaa.music.core.di.IoDispatcher
import com.gaabaariaa.music.core.util.escapeLike
import com.gaabaariaa.music.core.util.findDuplicateGroups
import com.gaabaariaa.music.core.util.lyricSnippet
import com.gaabaariaa.music.data.local.InsightsDao
import com.gaabaariaa.music.data.local.SongDao
import com.gaabaariaa.music.data.toDomain
import com.gaabaariaa.music.domain.model.AlbumSummary
import com.gaabaariaa.music.domain.model.ArtistSummary
import com.gaabaariaa.music.domain.model.FolderSummary
import com.gaabaariaa.music.domain.model.GenreSummary
import com.gaabaariaa.music.domain.model.HealthIssue
import com.gaabaariaa.music.domain.model.LibraryHealth
import com.gaabaariaa.music.domain.model.LyricsMatch
import com.gaabaariaa.music.domain.model.SearchResults
import com.gaabaariaa.music.domain.model.Song
import com.gaabaariaa.music.domain.repository.HealthRepository
import com.gaabaariaa.music.domain.repository.SearchRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Singleton
class SearchRepositoryImpl @Inject constructor(
    private val dao: InsightsDao,
    @IoDispatcher private val io: CoroutineDispatcher
) : SearchRepository {

    override suspend fun search(query: String): SearchResults = withContext(io) {
        val text = query.trim()
        if (text.isEmpty()) return@withContext SearchResults()
        val like = "%" + escapeLike(text) + "%"
        SearchResults(
            songs = dao.searchSongs(like, 100).map { it.toDomain() },
            artists = dao.searchArtists(like).map { ArtistSummary(it.name, it.songCount, it.albumCount) },
            albums = dao.searchAlbums(like).map { AlbumSummary(it.name, it.artist, it.songCount) },
            genres = dao.searchGenres(like).map { GenreSummary(it.name, it.songCount) },
            folders = dao.searchFolders(like).map { FolderSummary(it.path, it.songCount) },
            lyricMatches = dao.searchLyrics(like).map {
                LyricsMatch(it.song.toDomain(), lyricSnippet(it.lyricContent, text))
            }
        )
    }
}

@Singleton
class HealthRepositoryImpl @Inject constructor(
    private val insights: InsightsDao,
    private val songs: SongDao
) : HealthRepository {

    override fun observeHealth(): Flow<LibraryHealth> {
        val duplicateGroups = songs.observeSongs()
            .map { list -> findDuplicateGroups(list.map { it.toDomain() }).size }
            .flowOn(Dispatchers.Default)
        return combine(insights.observeHealth(), insights.observeAudit(), duplicateGroups) { h, a, d ->
            LibraryHealth(
                total = h.total,
                missingTitle = h.missingTitle,
                missingArtist = h.missingArtist,
                missingAlbum = h.missingAlbum,
                missingGenre = h.missingGenre,
                unrecognized = h.unrecognized,
                lowQuality = h.lowQuality,
                audited = a.audited,
                missingArtwork = a.missingArtwork,
                missingLyrics = a.missingLyrics,
                duplicateGroups = d,
                extendedInfoSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
            )
        }
    }

    override fun observeSongs(issue: HealthIssue): Flow<List<Song>> = when (issue) {
        HealthIssue.MISSING_TITLE -> insights.observeMissingTitle()
        HealthIssue.MISSING_ARTIST -> insights.observeMissingArtist()
        HealthIssue.MISSING_ALBUM -> insights.observeMissingAlbum()
        HealthIssue.MISSING_GENRE -> insights.observeMissingGenre()
        HealthIssue.UNRECOGNIZED -> insights.observeUnrecognized()
        HealthIssue.LOW_QUALITY -> insights.observeLowQuality()
        HealthIssue.MISSING_ARTWORK -> insights.observeMissingArtwork()
        HealthIssue.MISSING_LYRICS -> insights.observeMissingLyrics()
    }.map { list -> list.map { it.toDomain() } }
}
