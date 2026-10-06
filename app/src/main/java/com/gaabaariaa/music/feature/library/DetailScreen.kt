@file:OptIn(ExperimentalMaterial3Api::class)

package com.gaabaariaa.music.feature.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gaabaariaa.music.R
import com.gaabaariaa.music.domain.model.Song

@Composable
fun DetailScreen(
    onBack: () -> Unit,
    onPlay: (List<Song>, Int) -> Unit,
    onPlayNext: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onOpenDetail: (DetailType, String) -> Unit,
    onEditTags: () -> Unit,
    onFindArtwork: () -> Unit,
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
        }
    )
    val title = viewModel.value.ifBlank { unknown }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
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
            itemsIndexed(songs, key = { _, song -> "song:" + song.id }) { index, song ->
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
                    }
                )
                HorizontalDivider()
            }
        }
    }
}
