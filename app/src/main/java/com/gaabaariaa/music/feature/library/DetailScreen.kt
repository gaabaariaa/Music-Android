@file:OptIn(ExperimentalMaterial3Api::class)

package com.gaabaariaa.music.feature.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gaabaariaa.music.R
import com.gaabaariaa.music.domain.model.HealthIssue
import com.gaabaariaa.music.domain.model.Song
import com.gaabaariaa.music.feature.health.label

@Composable
fun DetailScreen(
    onBack: () -> Unit,
    onPlay: (List<Song>, Int) -> Unit,
    onPlayNext: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onOpenDetail: (DetailType, String) -> Unit,
    onEditTags: () -> Unit,
    onFindArtwork: () -> Unit,
    onOpenLyrics: (Long) -> Unit,
    viewModel: DetailViewModel = hiltViewModel()
) {
    val songs by viewModel.songs.collectAsStateWithLifecycle()
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val resources = LocalContext.current.resources

    val unknown = stringResource(
        when (viewModel.type) {
            DetailType.ARTIST -> R.string.unknown_artist
            DetailType.ALBUM -> R.string.unknown_album
            DetailType.GENRE -> R.string.unknown_genre
            DetailType.FOLDER -> R.string.unknown_folder
            DetailType.ISSUE -> R.string.unknown_title
            DetailType.PLAYLIST -> R.string.unknown_title
        }
    )
    val playlist by viewModel.playlist.collectAsStateWithLifecycle()
    val isManualPlaylist = viewModel.type == DetailType.PLAYLIST && playlist?.isSmart == false
    var editMode by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    val title = when (viewModel.type) {
        DetailType.ISSUE -> stringResource(HealthIssue.valueOf(viewModel.value).label())
        DetailType.PLAYLIST -> playlist?.name.orEmpty()
        else -> viewModel.value.ifBlank { unknown }
    }

    if (showRename) {
        var newName by remember { mutableStateOf(playlist?.name.orEmpty()) }
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text(stringResource(R.string.playlist_rename)) },
            text = {
                OutlinedTextField(
                    value = newName, onValueChange = { newName = it }, singleLine = true,
                    label = { Text(stringResource(R.string.playlist_name)) }
                )
            },
            confirmButton = {
                TextButton(enabled = newName.isNotBlank(), onClick = {
                    viewModel.rename(newName)
                    showRename = false
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = { TextButton(onClick = { showRename = false }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }
    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text(stringResource(R.string.playlist_delete)) },
            text = { Text(stringResource(R.string.playlist_delete_confirm, title)) },
            confirmButton = {
                TextButton(onClick = {
                    showDelete = false
                    viewModel.deletePlaylist(onBack)
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    if (viewModel.type == DetailType.PLAYLIST && playlist != null) {
                        if (isManualPlaylist) {
                            IconButton(onClick = { editMode = !editMode }) {
                                Icon(Icons.Default.SwapVert, stringResource(R.string.playlist_edit_order))
                            }
                        }
                        IconButton(onClick = { showRename = true }) {
                            Icon(Icons.Default.Edit, stringResource(R.string.playlist_rename))
                        }
                        IconButton(onClick = { showDelete = true }) {
                            Icon(Icons.Default.Delete, stringResource(R.string.playlist_delete))
                        }
                    }
                }
            )
        },
        contentWindowInsets = WindowInsets(0.dp)
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize()) {
            item {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        resources.getQuantityString(R.plurals.songs_count, songs.size, songs.size),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Button(
                        onClick = { onPlay(songs, 0) },
                        enabled = songs.isNotEmpty(),
                        modifier = Modifier.padding(top = 12.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Text(stringResource(R.string.play_all), Modifier.padding(start = 8.dp))
                    }
                }
            }
            if (albums.isNotEmpty()) {
                item {
                    Text(
                        stringResource(R.string.tab_albums),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                albumItems(albums) { onOpenDetail(DetailType.ALBUM, it) }
                item {
                    Text(
                        stringResource(R.string.tab_songs),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
            if (viewModel.type == DetailType.PLAYLIST && songs.isEmpty()) {
                item {
                    Text(
                        stringResource(if (playlist?.isSmart == true) R.string.playlist_empty_smart else R.string.playlist_empty),
                        Modifier.padding(16.dp)
                    )
                }
            }
            itemsIndexed(songs, key = { _, song -> "song:" + song.id }) { index, song ->
                if (editMode && isManualPlaylist) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            song.title.ifBlank { stringResource(R.string.unknown_title) },
                            modifier = Modifier.weight(1f), maxLines = 1
                        )
                        IconButton(onClick = { viewModel.moveInPlaylist(index, -1) }, enabled = index > 0) {
                            Icon(Icons.Default.ArrowUpward, stringResource(R.string.move_up))
                        }
                        IconButton(onClick = { viewModel.moveInPlaylist(index, 1) }, enabled = index < songs.lastIndex) {
                            Icon(Icons.Default.ArrowDownward, stringResource(R.string.move_down))
                        }
                        IconButton(onClick = { viewModel.removeFromPlaylist(song.id) }) {
                            Icon(Icons.Default.Close, stringResource(R.string.playlist_remove))
                        }
                    }
                    HorizontalDivider()
                    return@itemsIndexed
                }
                SongItem(
                    song,
                    onClick = { onPlay(songs, index) },
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
                    onFindLyrics = { onOpenLyrics(it.id) }
                )
                HorizontalDivider()
            }
        }
    }
}
