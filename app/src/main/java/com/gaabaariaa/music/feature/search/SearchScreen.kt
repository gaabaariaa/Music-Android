@file:OptIn(ExperimentalMaterial3Api::class, FlowPreview::class, ExperimentalCoroutinesApi::class)

package com.gaabaariaa.music.feature.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.gaabaariaa.music.R
import com.gaabaariaa.music.domain.model.SearchResults
import com.gaabaariaa.music.domain.model.Song
import com.gaabaariaa.music.domain.repository.SearchRepository
import com.gaabaariaa.music.feature.library.DetailType
import com.gaabaariaa.music.feature.library.SongItem
import com.gaabaariaa.music.feature.tags.TagEditSession
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class SearchViewModel @Inject constructor(
    repository: SearchRepository,
    private val tagSession: TagEditSession
) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val results: StateFlow<SearchResults> = _query
        .debounce(250)
        .mapLatest { repository.search(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchResults())

    fun setQuery(value: String) { _query.value = value }
    fun startTagEdit(ids: List<Long>) { tagSession.songIds = ids }
}

@Composable
fun SearchScreen(
    onPlay: (List<Song>, Int) -> Unit,
    onPlayNext: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onOpenDetail: (DetailType, String) -> Unit,
    onEditTags: () -> Unit,
    onFindArtwork: () -> Unit,
    onOpenLyrics: (Long) -> Unit,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (query.isBlank()) focus.requestFocus() }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_search)) }) },
        contentWindowInsets = WindowInsets(0.dp)
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::setQuery,
                singleLine = true,
                placeholder = { Text(stringResource(R.string.search_global_hint)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setQuery("") }) {
                            Icon(Icons.Default.Clear, stringResource(R.string.clear_search))
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).focusRequester(focus)
            )
            when {
                query.isBlank() -> Text(stringResource(R.string.search_start), Modifier.padding(16.dp))
                results.isEmpty -> Text(stringResource(R.string.search_no_results), Modifier.padding(16.dp))
                else -> ResultList(
                    results, onPlay, onPlayNext, onAddToQueue, onOpenDetail,
                    onEditTags = { viewModel.startTagEdit(listOf(it.id)); onEditTags() },
                    onFindArtwork = { viewModel.startTagEdit(listOf(it.id)); onFindArtwork() },
                    onOpenLyrics = onOpenLyrics
                )
            }
        }
    }
}

@Composable
private fun ResultList(
    results: SearchResults,
    onPlay: (List<Song>, Int) -> Unit,
    onPlayNext: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onOpenDetail: (DetailType, String) -> Unit,
    onEditTags: (Song) -> Unit,
    onFindArtwork: (Song) -> Unit,
    onOpenLyrics: (Long) -> Unit
) {
    val resources = LocalContext.current.resources
    val unknownArtist = stringResource(R.string.unknown_artist)
    val separator = stringResource(R.string.two_parts)
    LazyColumn(Modifier.fillMaxSize()) {
        if (results.songs.isNotEmpty()) {
            header(R.string.tab_songs)
            itemsIndexed(results.songs, key = { _, s -> "song:" + s.id }) { index, song ->
                SongItem(
                    song = song,
                    onClick = { onPlay(results.songs, index) },
                    onPlayNext = onPlayNext,
                    onAddToQueue = onAddToQueue,
                    onEditTags = onEditTags,
                    onFindArtwork = onFindArtwork,
                    onFindLyrics = { onOpenLyrics(it.id) }
                )
                HorizontalDivider()
            }
        }
        if (results.artists.isNotEmpty()) {
            header(R.string.tab_artists)
            itemsIndexed(results.artists, key = { _, a -> "artist:" + a.name }) { _, artist ->
                ListItem(
                    headlineContent = { Text(artist.name, maxLines = 1) },
                    supportingContent = {
                        Text(resources.getQuantityString(R.plurals.songs_count, artist.songCount, artist.songCount))
                    },
                    modifier = Modifier.clickable { onOpenDetail(DetailType.ARTIST, artist.name) }
                )
            }
        }
        if (results.albums.isNotEmpty()) {
            header(R.string.tab_albums)
            itemsIndexed(results.albums, key = { _, a -> "album:" + a.name }) { _, album ->
                ListItem(
                    headlineContent = { Text(album.name, maxLines = 1) },
                    supportingContent = { Text(album.artist.ifBlank { unknownArtist }, maxLines = 1) },
                    modifier = Modifier.clickable { onOpenDetail(DetailType.ALBUM, album.name) }
                )
            }
        }
        if (results.genres.isNotEmpty()) {
            header(R.string.tab_genres)
            itemsIndexed(results.genres, key = { _, g -> "genre:" + g.name }) { _, genre ->
                ListItem(
                    headlineContent = { Text(genre.name, maxLines = 1) },
                    supportingContent = {
                        Text(resources.getQuantityString(R.plurals.songs_count, genre.songCount, genre.songCount))
                    },
                    modifier = Modifier.clickable { onOpenDetail(DetailType.GENRE, genre.name) }
                )
            }
        }
        if (results.folders.isNotEmpty()) {
            header(R.string.tab_folders)
            itemsIndexed(results.folders, key = { _, f -> "folder:" + f.path }) { _, folder ->
                ListItem(
                    headlineContent = { Text(folder.path, maxLines = 2) },
                    supportingContent = {
                        Text(resources.getQuantityString(R.plurals.songs_count, folder.songCount, folder.songCount))
                    },
                    modifier = Modifier.clickable { onOpenDetail(DetailType.FOLDER, folder.path) }
                )
            }
        }
        if (results.lyricMatches.isNotEmpty()) {
            header(R.string.search_in_lyrics)
            itemsIndexed(results.lyricMatches, key = { _, m -> "lyrics:" + m.song.id }) { _, match ->
                ListItem(
                    headlineContent = { Text(match.song.title.ifBlank { stringResource(R.string.unknown_title) }, maxLines = 1) },
                    supportingContent = {
                        Text(
                            separator.format(match.song.artist.ifBlank { unknownArtist }, match.snippet),
                            maxLines = 2
                        )
                    },
                    modifier = Modifier.clickable { onOpenLyrics(match.song.id) }
                )
            }
        }
    }
}

private fun LazyListScope.header(titleRes: Int) {
    item(key = "header:$titleRes") {
        Text(
            stringResource(titleRes),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}
