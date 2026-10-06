@file:OptIn(ExperimentalMaterial3Api::class)

package com.gaabaariaa.music.feature.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gaabaariaa.music.R
import com.gaabaariaa.music.core.util.audioPermission
import com.gaabaariaa.music.core.util.hasAudioPermission
import com.gaabaariaa.music.domain.model.AlbumSummary
import com.gaabaariaa.music.domain.model.ArtistSummary
import com.gaabaariaa.music.domain.model.FolderSummary
import com.gaabaariaa.music.domain.model.GenreSummary
import com.gaabaariaa.music.domain.model.Song
import com.gaabaariaa.music.domain.model.SongSort

private val tabTitles = listOf(
    R.string.tab_songs, R.string.tab_artists, R.string.tab_albums, R.string.tab_genres, R.string.tab_folders
)

@Composable
fun LibraryScreen(
    onPlay: (List<Song>, Int) -> Unit,
    onPlayNext: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onOpenDetail: (DetailType, String) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val songs by viewModel.songs.collectAsStateWithLifecycle()
    val artists by viewModel.artists.collectAsStateWithLifecycle()
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val genres by viewModel.genres.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val scan by viewModel.scanState.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val sort by viewModel.sort.collectAsStateWithLifecycle()

    var granted by remember { mutableStateOf(hasAudioPermission(context)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
    }
    LaunchedEffect(granted) { if (granted) viewModel.scan() }

    var tab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.library_title)) },
                actions = {
                    if (granted) {
                        IconButton(onClick = { viewModel.scan(force = true) }, enabled = !scan.scanning) {
                            Icon(Icons.Default.Refresh, stringResource(R.string.action_rescan))
                        }
                    }
                }
            )
        },
        contentWindowInsets = WindowInsets(0.dp)
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (!granted) {
                Text(stringResource(R.string.permission_message), Modifier.padding(16.dp))
                Button(
                    onClick = { launcher.launch(audioPermission()) },
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) { Text(stringResource(R.string.permission_button)) }
            } else {
                ScrollableTabRow(selectedTabIndex = tab, edgePadding = 0.dp) {
                    tabTitles.forEachIndexed { index, title ->
                        Tab(
                            selected = tab == index,
                            onClick = { tab = index },
                            text = { Text(stringResource(title)) }
                        )
                    }
                }
                if (scan.scanning) LinearProgressIndicator(Modifier.fillMaxWidth())
                when (tab) {
                    0 -> {
                        SearchAndSortBar(
                            query = query,
                            sort = sort,
                            onQuery = viewModel::setQuery,
                            onSort = viewModel::setSort
                        )
                        SongList(songs, query.isNotBlank(), onPlay, onPlayNext, onAddToQueue)
                    }
                    1 -> ArtistList(artists) { onOpenDetail(DetailType.ARTIST, it) }
                    2 -> AlbumList(albums) { onOpenDetail(DetailType.ALBUM, it) }
                    3 -> GenreList(genres) { onOpenDetail(DetailType.GENRE, it) }
                    else -> FolderList(folders) { onOpenDetail(DetailType.FOLDER, it) }
                }
            }
        }
    }
}

@Composable
private fun SearchAndSortBar(
    query: String,
    sort: SongSort,
    onQuery: (String) -> Unit,
    onSort: (SongSort) -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            modifier = Modifier.weight(1f),
            singleLine = true,
            placeholder = { Text(stringResource(R.string.search_hint)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQuery("") }) {
                        Icon(Icons.Default.Clear, stringResource(R.string.clear_search))
                    }
                }
            }
        )
        IconButton(onClick = { menuOpen = true }) {
            Icon(Icons.AutoMirrored.Filled.Sort, stringResource(R.string.sort))
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            SongSort.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(stringResource(option.label())) },
                    leadingIcon = { RadioButton(selected = sort == option, onClick = null) },
                    onClick = {
                        menuOpen = false
                        onSort(option)
                    }
                )
            }
        }
    }
}

private fun SongSort.label(): Int = when (this) {
    SongSort.TITLE -> R.string.sort_title
    SongSort.ARTIST -> R.string.sort_artist
    SongSort.ALBUM -> R.string.sort_album
    SongSort.DATE_ADDED -> R.string.sort_date_added
    SongSort.DURATION -> R.string.sort_duration
}

@Composable
fun SongList(
    songs: List<Song>,
    isSearching: Boolean,
    onPlay: (List<Song>, Int) -> Unit,
    onPlayNext: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit
) {
    if (songs.isEmpty()) {
        Text(
            stringResource(if (isSearching) R.string.no_results else R.string.empty_songs),
            Modifier.padding(16.dp)
        )
        return
    }
    LazyColumn {
        itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
            SongItem(song, onClick = { onPlay(songs, index) }, onPlayNext = onPlayNext, onAddToQueue = onAddToQueue)
            HorizontalDivider()
        }
    }
}

@Composable
private fun ArtistList(artists: List<ArtistSummary>, onOpen: (String) -> Unit) {
    if (artists.isEmpty()) {
        Text(stringResource(R.string.empty_generic), Modifier.padding(16.dp))
        return
    }
    val unknown = stringResource(R.string.unknown_artist)
    val separator = stringResource(R.string.two_parts)
    val resources = LocalContext.current.resources
    LazyColumn {
        itemsIndexed(artists, key = { _, a -> a.name }) { _, artist ->
            ListItem(
                headlineContent = { Text(artist.name.ifBlank { unknown }, maxLines = 1) },
                supportingContent = {
                    Text(
                        separator.format(
                            resources.getQuantityString(R.plurals.songs_count, artist.songCount, artist.songCount),
                            resources.getQuantityString(R.plurals.albums_count, artist.albumCount, artist.albumCount)
                        )
                    )
                },
                modifier = Modifier.clickable { onOpen(artist.name) }
            )
            HorizontalDivider()
        }
    }
}

@Composable
fun AlbumList(albums: List<AlbumSummary>, onOpen: (String) -> Unit) {
    if (albums.isEmpty()) {
        Text(stringResource(R.string.empty_generic), Modifier.padding(16.dp))
        return
    }
    LazyColumn { albumItems(albums, onOpen) }
}

fun androidx.compose.foundation.lazy.LazyListScope.albumItems(
    albums: List<AlbumSummary>,
    onOpen: (String) -> Unit
) {
    itemsIndexed(albums, key = { _, a -> "album:" + a.name }) { _, album ->
        val unknownAlbum = stringResource(R.string.unknown_album)
        val unknownArtist = stringResource(R.string.unknown_artist)
        val separator = stringResource(R.string.two_parts)
        val resources = LocalContext.current.resources
        ListItem(
            headlineContent = { Text(album.name.ifBlank { unknownAlbum }, maxLines = 1) },
            supportingContent = {
                Text(
                    separator.format(
                        album.artist.ifBlank { unknownArtist },
                        resources.getQuantityString(R.plurals.songs_count, album.songCount, album.songCount)
                    )
                )
            },
            modifier = Modifier.clickable { onOpen(album.name) }
        )
        HorizontalDivider()
    }
}

@Composable
private fun GenreList(genres: List<GenreSummary>, onOpen: (String) -> Unit) {
    if (genres.isEmpty()) {
        Text(stringResource(R.string.empty_generic), Modifier.padding(16.dp))
        return
    }
    val unknown = stringResource(R.string.unknown_genre)
    val resources = LocalContext.current.resources
    LazyColumn {
        itemsIndexed(genres, key = { _, g -> g.name }) { _, genre ->
            ListItem(
                headlineContent = { Text(genre.name.ifBlank { unknown }, maxLines = 1) },
                supportingContent = {
                    Text(resources.getQuantityString(R.plurals.songs_count, genre.songCount, genre.songCount))
                },
                modifier = Modifier.clickable { onOpen(genre.name) }
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun FolderList(folders: List<FolderSummary>, onOpen: (String) -> Unit) {
    if (folders.isEmpty()) {
        Text(stringResource(R.string.empty_generic), Modifier.padding(16.dp))
        return
    }
    val unknown = stringResource(R.string.unknown_folder)
    val resources = LocalContext.current.resources
    LazyColumn {
        itemsIndexed(folders, key = { _, f -> f.path }) { _, folder ->
            ListItem(
                headlineContent = { Text(folder.path.ifBlank { unknown }, maxLines = 2) },
                supportingContent = {
                    Text(resources.getQuantityString(R.plurals.songs_count, folder.songCount, folder.songCount))
                },
                modifier = Modifier.clickable { onOpen(folder.path) }
            )
            HorizontalDivider()
        }
    }
}
