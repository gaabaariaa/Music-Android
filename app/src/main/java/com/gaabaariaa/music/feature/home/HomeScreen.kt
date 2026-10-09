@file:OptIn(ExperimentalMaterial3Api::class)

package com.gaabaariaa.music.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.gaabaariaa.music.R
import com.gaabaariaa.music.core.designsystem.LocalAppSettings
import com.gaabaariaa.music.domain.model.HealthIssue
import com.gaabaariaa.music.domain.model.HomeSection
import com.gaabaariaa.music.domain.model.LibraryHealth
import com.gaabaariaa.music.domain.model.Song
import com.gaabaariaa.music.domain.repository.HealthRepository
import com.gaabaariaa.music.domain.repository.LibraryRepository
import com.gaabaariaa.music.domain.repository.PersonalRepository
import com.gaabaariaa.music.feature.health.label
import com.gaabaariaa.music.feature.library.DetailType
import com.gaabaariaa.music.feature.player.SongArtwork
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

private const val ROW_LIMIT = 20

fun HomeSection.titleRes(): Int = when (this) {
    HomeSection.RECENTLY_PLAYED -> R.string.home_recently_played
    HomeSection.RECENTLY_ADDED -> R.string.home_recently_added
    HomeSection.MOST_PLAYED -> R.string.home_most_played
    HomeSection.FAVORITES -> R.string.home_favorites
    HomeSection.RECENTLY_DOWNLOADED -> R.string.home_recently_downloaded
    HomeSection.ARTISTS -> R.string.tab_artists
    HomeSection.ALBUMS -> R.string.tab_albums
    HomeSection.GENRES -> R.string.tab_genres
    HomeSection.MISSING_METADATA -> R.string.home_missing_metadata
    HomeSection.MISSING_ARTWORK -> R.string.issue_missing_artwork
    HomeSection.MISSING_LYRICS -> R.string.issue_missing_lyrics
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    personal: PersonalRepository,
    library: LibraryRepository,
    healthRepository: HealthRepository
) : ViewModel() {
    // Each list only runs while its section is on screen.
    private fun <T> Flow<T>.share(initial: T): StateFlow<T> =
        stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    val recentlyPlayed = personal.observeRecentlyPlayed(ROW_LIMIT).share(emptyList())
    val recentlyAdded = personal.observeRecentlyAdded(ROW_LIMIT).share(emptyList())
    val mostPlayed = personal.observeMostPlayed(ROW_LIMIT).share(emptyList())
    val favorites = personal.observeFavorites(ROW_LIMIT).share(emptyList())
    val recentlyDownloaded = personal.observeRecentlyDownloaded(ROW_LIMIT).share(emptyList())
    val artists = library.observeArtists()
        .map { list -> list.filter { it.name.isNotBlank() }.sortedByDescending { it.songCount }.take(ROW_LIMIT) }
        .share(emptyList())
    val albums = library.observeAlbums()
        .map { list -> list.filter { it.name.isNotBlank() }.sortedByDescending { it.songCount }.take(ROW_LIMIT) }
        .share(emptyList())
    val genres = library.observeGenres()
        .map { list -> list.filter { it.name.isNotBlank() }.sortedByDescending { it.songCount }.take(ROW_LIMIT) }
        .share(emptyList())
    val health: StateFlow<LibraryHealth> = healthRepository.observeHealth().share(LibraryHealth())
}

@Composable
fun HomeScreen(
    onPlay: (List<Song>, Int) -> Unit,
    onOpenDetail: (DetailType, String) -> Unit,
    onOpenHealth: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val sections = LocalAppSettings.current.homeSections.filter { it.enabled }.map { it.section }
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_home)) }) },
        contentWindowInsets = WindowInsets(0.dp)
    ) { pad ->
        if (sections.isEmpty()) {
            Text(stringResource(R.string.home_no_sections), Modifier.padding(pad).padding(16.dp))
        } else {
            LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                items(sections, key = { it.name }) { section ->
                    when (section) {
                        HomeSection.RECENTLY_PLAYED -> SongRow(section, viewModel.recentlyPlayed, onPlay)
                        HomeSection.RECENTLY_ADDED -> SongRow(section, viewModel.recentlyAdded, onPlay)
                        HomeSection.MOST_PLAYED -> SongRow(section, viewModel.mostPlayed, onPlay)
                        HomeSection.FAVORITES -> SongRow(section, viewModel.favorites, onPlay)
                        HomeSection.RECENTLY_DOWNLOADED -> SongRow(section, viewModel.recentlyDownloaded, onPlay)
                        HomeSection.ARTISTS -> SummaryRow(section, viewModel.artists, { it.coverSongId }, { it.name },
                            { stringResource(R.string.unknown_artist) }) { onOpenDetail(DetailType.ARTIST, it.name) }
                        HomeSection.ALBUMS -> SummaryRow(section, viewModel.albums, { it.coverSongId }, { it.name },
                            { stringResource(R.string.unknown_album) }) { onOpenDetail(DetailType.ALBUM, it.name) }
                        HomeSection.GENRES -> SummaryRow(section, viewModel.genres, { it.coverSongId }, { it.name },
                            { stringResource(R.string.unknown_genre) }) { onOpenDetail(DetailType.GENRE, it.name) }
                        HomeSection.MISSING_METADATA -> MissingMetadata(viewModel.health, onOpenDetail)
                        HomeSection.MISSING_ARTWORK -> MissingAudited(
                            section, viewModel.health, HealthIssue.MISSING_ARTWORK, onOpenDetail, onOpenHealth
                        )
                        HomeSection.MISSING_LYRICS -> MissingAudited(
                            section, viewModel.health, HealthIssue.MISSING_LYRICS, onOpenDetail, onOpenHealth
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(section: HomeSection) {
    Text(
        stringResource(section.titleRes()),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
    )
}

@Composable
private fun SongRow(section: HomeSection, flow: StateFlow<List<Song>>, onPlay: (List<Song>, Int) -> Unit) {
    val songs by flow.collectAsStateWithLifecycle()
    if (songs.isEmpty()) return
    val unknownTitle = stringResource(R.string.unknown_title)
    val unknownArtist = stringResource(R.string.unknown_artist)
    Column {
        SectionHeader(section)
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            itemsIndexed(songs, key = { _, s -> s.id }) { index, song ->
                HomeCard(
                    coverSongId = song.id,
                    title = song.title.ifBlank { unknownTitle },
                    subtitle = song.artist.ifBlank { unknownArtist },
                    onClick = { onPlay(songs, index) }
                )
            }
        }
    }
}

@Composable
private fun <T> SummaryRow(
    section: HomeSection,
    flow: StateFlow<List<T>>,
    cover: (T) -> Long,
    name: (T) -> String,
    unknown: @Composable () -> String,
    onClick: (T) -> Unit
) {
    val list by flow.collectAsStateWithLifecycle()
    if (list.isEmpty()) return
    val unknownLabel = unknown()
    Column {
        SectionHeader(section)
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(list, key = { name(it) + cover(it) }) { item ->
                HomeCard(
                    coverSongId = cover(item),
                    title = name(item).ifBlank { unknownLabel },
                    subtitle = "",
                    onClick = { onClick(item) }
                )
            }
        }
    }
}

@Composable
private fun HomeCard(coverSongId: Long, title: String, subtitle: String, onClick: () -> Unit) {
    val showArtwork = LocalAppSettings.current.showArtwork
    Column(Modifier.width(128.dp).clickable(onClick = onClick)) {
        if (showArtwork) SongArtwork(coverSongId.toString(), Modifier.size(128.dp), sizePx = 256)
        Text(
            title, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp)
        )
        if (subtitle.isNotBlank()) {
            Text(
                subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MissingMetadata(flow: StateFlow<LibraryHealth>, onOpenDetail: (DetailType, String) -> Unit) {
    val health by flow.collectAsStateWithLifecycle()
    val issues = listOf(
        HealthIssue.MISSING_TITLE to health.missingTitle,
        HealthIssue.MISSING_ARTIST to health.missingArtist,
        HealthIssue.MISSING_ALBUM to health.missingAlbum
    ).filter { it.second > 0 }
    if (issues.isEmpty()) return
    Column {
        SectionHeader(HomeSection.MISSING_METADATA)
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(issues, key = { it.first.name }) { (issue, count) ->
                AssistChip(
                    onClick = { onOpenDetail(DetailType.ISSUE, issue.name) },
                    label = { Text(stringResource(issueLabel(issue)) + ": " + count) }
                )
            }
        }
    }
}

@Composable
private fun MissingAudited(
    section: HomeSection,
    flow: StateFlow<LibraryHealth>,
    issue: HealthIssue,
    onOpenDetail: (DetailType, String) -> Unit,
    onOpenHealth: () -> Unit
) {
    val health by flow.collectAsStateWithLifecycle()
    val count = if (issue == HealthIssue.MISSING_ARTWORK) health.missingArtwork else health.missingLyrics
    Column {
        SectionHeader(section)
        Card(Modifier.padding(horizontal = 16.dp).clickable {
            if (health.audited == 0) onOpenHealth() else if (count > 0) onOpenDetail(DetailType.ISSUE, issue.name)
        }) {
            Text(
                when {
                    health.audited == 0 -> stringResource(R.string.home_run_audit)
                    count == 0 -> stringResource(R.string.home_all_good)
                    else -> LocalContext.current.resources.getQuantityString(R.plurals.songs_count, count, count)
                },
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

private fun issueLabel(issue: HealthIssue): Int = issue.label()
