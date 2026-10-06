@file:OptIn(ExperimentalMaterial3Api::class)

package com.gaabaariaa.music.feature.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.ScrollableTabRow
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gaabaariaa.music.R
import com.gaabaariaa.music.core.util.audioPermission
import com.gaabaariaa.music.core.util.formatDuration
import com.gaabaariaa.music.core.util.hasAudioPermission
import com.gaabaariaa.music.domain.model.Song

private val tabTitles = listOf(
    R.string.tab_songs, R.string.tab_artists, R.string.tab_albums, R.string.tab_genres, R.string.tab_folders
)

@Composable
fun LibraryScreen(
    onPlay: (List<Song>, Int) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val songs by viewModel.songs.collectAsStateWithLifecycle()
    val artists by viewModel.artists.collectAsStateWithLifecycle()
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val genres by viewModel.genres.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val scan by viewModel.scanState.collectAsStateWithLifecycle()

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
                        IconButton(
                            onClick = { viewModel.scan(force = true) },
                            enabled = !scan.scanning
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.action_rescan)
                            )
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
                if (scan.scanning) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                when (tab) {
                    0 -> SongList(songs, onPlay)
                    1 -> ArtistList(artists)
                    2 -> AlbumList(albums)
                    3 -> GenreList(genres)
                    else -> FolderList(folders)
                }
            }
        }
    }
}

@Composable
private fun SongList(songs: List<Song>, onPlay: (List<Song>, Int) -> Unit) {
    if (songs.isEmpty()) {
        Text(stringResource(R.string.empty_songs), Modifier.padding(16.dp))
        return
    }
    val unknownTitle = stringResource(R.string.unknown_title)
    val unknownArtist = stringResource(R.string.unknown_artist)
    val unknownAlbum = stringResource(R.string.unknown_album)
    val separator = stringResource(R.string.two_parts)
    LazyColumn {
        itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
            ListItem(
                headlineContent = { Text(song.title.ifBlank { unknownTitle }, maxLines = 1) },
                supportingContent = {
                    Text(
                        separator.format(
                            song.artist.ifBlank { unknownArtist },
                            song.album.ifBlank { unknownAlbum }
                        ),
                        maxLines = 1
                    )
                },
                trailingContent = { Text(formatDuration(song.durationMs)) },
                modifier = Modifier.clickable { onPlay(songs, index) }
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun ArtistList(artists: List<com.gaabaariaa.music.domain.model.ArtistSummary>) {
    if (artists.isEmpty()) {
        Text(stringResource(R.string.empty_generic), Modifier.padding(16.dp))
        return
    }
    val unknownArtist = stringResource(R.string.unknown_artist)
    val separator = stringResource(R.string.two_parts)
    val resources = LocalContext.current.resources
    LazyColumn {
        itemsIndexed(artists, key = { _, a -> a.name }) { _, artist ->
            ListItem(
                headlineContent = { Text(artist.name.ifBlank { unknownArtist }, maxLines = 1) },
                supportingContent = {
                    Text(
                        separator.format(
                            resources.getQuantityString(R.plurals.songs_count, artist.songCount, artist.songCount),
                            resources.getQuantityString(R.plurals.albums_count, artist.albumCount, artist.albumCount)
                        )
                    )
                }
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun AlbumList(albums: List<com.gaabaariaa.music.domain.model.AlbumSummary>) {
    if (albums.isEmpty()) {
        Text(stringResource(R.string.empty_generic), Modifier.padding(16.dp))
        return
    }
    val unknownAlbum = stringResource(R.string.unknown_album)
    val unknownArtist = stringResource(R.string.unknown_artist)
    val separator = stringResource(R.string.two_parts)
    val resources = LocalContext.current.resources
    LazyColumn {
        itemsIndexed(albums, key = { _, a -> a.name }) { _, album ->
            ListItem(
                headlineContent = { Text(album.name.ifBlank { unknownAlbum }, maxLines = 1) },
                supportingContent = {
                    Text(
                        separator.format(
                            album.artist.ifBlank { unknownArtist },
                            resources.getQuantityString(R.plurals.songs_count, album.songCount, album.songCount)
                        )
                    )
                }
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun GenreList(genres: List<com.gaabaariaa.music.domain.model.GenreSummary>) {
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
                }
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun FolderList(folders: List<com.gaabaariaa.music.domain.model.FolderSummary>) {
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
                }
            )
            HorizontalDivider()
        }
    }
}
