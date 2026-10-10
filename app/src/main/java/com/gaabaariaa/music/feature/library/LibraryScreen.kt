@file:OptIn(ExperimentalMaterial3Api::class)

package com.gaabaariaa.music.feature.library

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.SelectAll
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
import com.gaabaariaa.music.core.designsystem.LocalAppSettings
import com.gaabaariaa.music.domain.model.LibraryLayout
import com.gaabaariaa.music.domain.model.LibraryTab
import com.gaabaariaa.music.feature.playlists.PlaylistPickerDialog
import com.gaabaariaa.music.feature.playlists.PlaylistsTab
import com.gaabaariaa.music.core.util.audioPermission
import com.gaabaariaa.music.core.util.hasAudioPermission
import com.gaabaariaa.music.domain.model.AlbumSummary
import com.gaabaariaa.music.domain.model.ArtistSummary
import com.gaabaariaa.music.domain.model.FolderSummary
import com.gaabaariaa.music.domain.model.GenreSummary
import com.gaabaariaa.music.domain.model.Song
import com.gaabaariaa.music.domain.model.SongSort

private fun LibraryTab.titleRes(): Int = when (this) {
    LibraryTab.SONGS -> R.string.tab_songs
    LibraryTab.ARTISTS -> R.string.tab_artists
    LibraryTab.ALBUMS -> R.string.tab_albums
    LibraryTab.GENRES -> R.string.tab_genres
    LibraryTab.FOLDERS -> R.string.tab_folders
    LibraryTab.PLAYLISTS -> R.string.tab_playlists
}

@Composable
fun LibraryScreen(
    onPlay: (List<Song>, Int) -> Unit,
    onPlayNext: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onOpenDetail: (DetailType, String) -> Unit,
    onEditTags: () -> Unit,
    onFindArtwork: () -> Unit,
    onOpenLyrics: (Long) -> Unit,
    onOpenHealth: () -> Unit,
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
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    var pickerIds by remember { mutableStateOf<List<Long>?>(null) }
    pickerIds?.let { ids ->
        PlaylistPickerDialog(ids) {
            pickerIds = null
            viewModel.clearSelection()
        }
    }
    BackHandler(enabled = selection.isNotEmpty()) { viewModel.clearSelection() }

    var granted by remember { mutableStateOf(hasAudioPermission(context)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
    }
    LaunchedEffect(granted) { if (granted) viewModel.scan() }

    var tab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            if (selection.isNotEmpty()) {
                TopAppBar(
                    title = {
                        Text(LocalContext.current.resources.getQuantityString(
                            R.plurals.selected_count, selection.size, selection.size
                        ))
                    },
                    navigationIcon = {
                        IconButton(onClick = viewModel::clearSelection) {
                            Icon(Icons.Default.Close, stringResource(R.string.clear_selection))
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.selectAll(songs.map { it.id }) }) {
                            Icon(Icons.Default.SelectAll, stringResource(R.string.select_all))
                        }
                        IconButton(onClick = { pickerIds = selection.toList() }) {
                            Icon(Icons.AutoMirrored.Filled.PlaylistAdd, stringResource(R.string.menu_add_to_playlist))
                        }
                        IconButton(onClick = {
                            viewModel.startTagEdit(selection.toList())
                            onFindArtwork()
                            viewModel.clearSelection()
                        }) {
                            Icon(Icons.Default.Image, stringResource(R.string.menu_find_artwork))
                        }
                        IconButton(onClick = {
                            viewModel.startTagEdit(selection.toList())
                            onEditTags()
                            viewModel.clearSelection()
                        }) {
                            Icon(Icons.Default.Edit, stringResource(R.string.menu_edit_tags))
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = { Text(stringResource(R.string.library_title)) },
                    actions = {
                        if (granted) {
                            IconButton(onClick = onOpenHealth) {
                                Icon(Icons.Default.HealthAndSafety, stringResource(R.string.health_title))
                            }
                            IconButton(onClick = { viewModel.scan(force = true) }, enabled = !scan.scanning) {
                                Icon(Icons.Default.Refresh, stringResource(R.string.action_rescan))
                            }
                        }
                    }
                )
            }
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
                val appSettings = LocalAppSettings.current
                val tabs = appSettings.libraryTabs
                val tabIndex = tab.coerceIn(0, tabs.lastIndex)
                ScrollableTabRow(selectedTabIndex = tabIndex, edgePadding = 0.dp) {
                    tabs.forEachIndexed { index, item ->
                        Tab(
                            selected = tabIndex == index,
                            onClick = { tab = index },
                            text = { Text(stringResource(item.titleRes())) }
                        )
                    }
                }
                if (scan.scanning) LinearProgressIndicator(Modifier.fillMaxWidth())
                when (tabs[tabIndex]) {
                    LibraryTab.SONGS -> {
                        SearchAndSortBar(
                            query = query,
                            sort = sort,
                            onQuery = viewModel::setQuery,
                            onSort = viewModel::setSort
                        )
                        SongList(
                            songs = songs,
                            isSearching = query.isNotBlank(),
                            selection = selection,
                            onPlay = onPlay,
                            onPlayNext = onPlayNext,
                            onAddToQueue = onAddToQueue,
                            onEditTags = {
                                viewModel.startTagEdit(listOf(it.id))
                                onEditTags()
                            },
                            onFindArtwork = {
                                viewModel.startTagEdit(listOf(it.id))
                                onFindArtwork()
                            },
                            onFindLyrics = { onOpenLyrics(it.id) },
                            onToggleSelect = viewModel::toggleSelection
                        )
                    }
                    LibraryTab.ARTISTS -> SummaryCollection(artistItems(artists)) { onOpenDetail(DetailType.ARTIST, it) }
                    LibraryTab.ALBUMS -> SummaryCollection(albumItemsUi(albums)) { onOpenDetail(DetailType.ALBUM, it) }
                    LibraryTab.GENRES -> SummaryCollection(genreItems(genres)) { onOpenDetail(DetailType.GENRE, it) }
                    LibraryTab.FOLDERS -> SummaryCollection(folderItems(folders)) { onOpenDetail(DetailType.FOLDER, it) }
                    LibraryTab.PLAYLISTS -> PlaylistsTab { onOpenDetail(DetailType.PLAYLIST, it.toString()) }
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
    selection: Set<Long>,
    onPlay: (List<Song>, Int) -> Unit,
    onPlayNext: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onEditTags: (Song) -> Unit,
    onFindArtwork: (Song) -> Unit,
    onFindLyrics: (Song) -> Unit,
    onToggleSelect: (Long) -> Unit
) {
    if (songs.isEmpty()) {
        Text(
            stringResource(if (isSearching) R.string.no_results else R.string.empty_songs),
            Modifier.padding(16.dp)
        )
        return
    }
    val settings = LocalAppSettings.current
    if (settings.libraryLayout == LibraryLayout.GRID) {
        SongGrid(
            songs = songs,
            columns = settings.gridColumns,
            selection = selection,
            onPlay = onPlay,
            onPlayNext = onPlayNext,
            onAddToQueue = onAddToQueue,
            onEditTags = onEditTags,
            onFindArtwork = onFindArtwork,
            onFindLyrics = onFindLyrics,
            onToggleSelect = onToggleSelect
        )
        return
    }
    LazyColumn {
        itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
            val selectionMode = selection.isNotEmpty()
            SongItem(
                song = song,
                onClick = { if (selectionMode) onToggleSelect(song.id) else onPlay(songs, index) },
                onPlayNext = onPlayNext,
                onAddToQueue = onAddToQueue,
                onEditTags = onEditTags,
                onFindArtwork = onFindArtwork,
                onFindLyrics = onFindLyrics,
                selectionMode = selectionMode,
                selected = song.id in selection,
                onSelect = { onToggleSelect(song.id) }
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun artistItems(artists: List<ArtistSummary>): List<SummaryUi> {
    val unknown = stringResource(R.string.unknown_artist)
    val separator = stringResource(R.string.two_parts)
    val resources = LocalContext.current.resources
    return artists.map {
        SummaryUi(
            key = "artist:" + it.name,
            title = it.name.ifBlank { unknown },
            subtitle = separator.format(
                resources.getQuantityString(R.plurals.songs_count, it.songCount, it.songCount),
                resources.getQuantityString(R.plurals.albums_count, it.albumCount, it.albumCount)
            ),
            coverSongId = it.coverSongId,
            value = it.name
        )
    }
}

@Composable
private fun albumItemsUi(albums: List<AlbumSummary>): List<SummaryUi> {
    val unknownAlbum = stringResource(R.string.unknown_album)
    val unknownArtist = stringResource(R.string.unknown_artist)
    val separator = stringResource(R.string.two_parts)
    val resources = LocalContext.current.resources
    return albums.map {
        SummaryUi(
            key = "album:" + it.name,
            title = it.name.ifBlank { unknownAlbum },
            subtitle = separator.format(
                it.artist.ifBlank { unknownArtist },
                resources.getQuantityString(R.plurals.songs_count, it.songCount, it.songCount)
            ),
            coverSongId = it.coverSongId,
            value = it.name
        )
    }
}

@Composable
private fun genreItems(genres: List<GenreSummary>): List<SummaryUi> {
    val unknown = stringResource(R.string.unknown_genre)
    val resources = LocalContext.current.resources
    return genres.map {
        SummaryUi(
            key = "genre:" + it.name,
            title = it.name.ifBlank { unknown },
            subtitle = resources.getQuantityString(R.plurals.songs_count, it.songCount, it.songCount),
            coverSongId = it.coverSongId,
            value = it.name
        )
    }
}

@Composable
private fun folderItems(folders: List<FolderSummary>): List<SummaryUi> {
    val unknown = stringResource(R.string.unknown_folder)
    val resources = LocalContext.current.resources
    return folders.map {
        SummaryUi(
            key = "folder:" + it.path,
            title = it.path.ifBlank { unknown },
            subtitle = resources.getQuantityString(R.plurals.songs_count, it.songCount, it.songCount),
            coverSongId = it.coverSongId,
            value = it.path
        )
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
