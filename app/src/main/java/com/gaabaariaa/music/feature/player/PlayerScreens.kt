@file:OptIn(ExperimentalMaterial3Api::class)

package com.gaabaariaa.music.feature.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.gaabaariaa.music.R
import com.gaabaariaa.music.core.util.formatDuration

@Composable
fun MiniPlayer(
    state: PlayerUiState,
    onOpen: () -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit
) {
    Surface(tonalElevation = 3.dp, modifier = Modifier.clickable(onClick = onOpen)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                Text(
                    state.title.ifBlank { stringResource(R.string.unknown_title) },
                    maxLines = 1,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    state.artist.ifBlank { stringResource(R.string.unknown_artist) },
                    maxLines = 1,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            IconButton(onClick = onPrevious) {
                Icon(Icons.Default.SkipPrevious, stringResource(R.string.previous))
            }
            IconButton(onClick = onPlayPause) {
                Icon(
                    if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    stringResource(if (state.isPlaying) R.string.pause else R.string.play)
                )
            }
            IconButton(onClick = onNext) {
                Icon(Icons.Default.SkipNext, stringResource(R.string.next))
            }
        }
    }
}

@Composable
fun NowPlayingScreen(
    state: PlayerUiState,
    onBack: () -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onSelect: (Int) -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit
) {
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val max = state.durationMs.coerceAtLeast(1L).toFloat()
    val shown = if (dragging) dragValue else state.positionMs.toFloat()

    val unknownTitle = stringResource(R.string.unknown_title)
    val unknownArtist = stringResource(R.string.unknown_artist)
    val unknownAlbum = stringResource(R.string.unknown_album)
    val separator = stringResource(R.string.two_parts)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.now_playing)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                }
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(24.dp))
            Text(
                state.title.ifBlank { unknownTitle },
                style = MaterialTheme.typography.headlineMedium,
                maxLines = 2
            )
            Text(state.artist.ifBlank { unknownArtist }, style = MaterialTheme.typography.titleMedium)
            Text(state.album.ifBlank { unknownAlbum }, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(20.dp))

            Slider(
                value = shown.coerceIn(0f, max),
                onValueChange = { dragging = true; dragValue = it },
                onValueChangeFinished = { onSeek(dragValue.toLong()); dragging = false },
                valueRange = 0f..max
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatDuration(shown.toLong()))
                Text(formatDuration(state.durationMs))
            }

            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onShuffle) {
                    Icon(
                        Icons.Default.Shuffle,
                        stringResource(R.string.shuffle),
                        tint = if (state.shuffle) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onPrevious) {
                    Icon(Icons.Default.SkipPrevious, stringResource(R.string.previous))
                }
                FilledIconButton(
                    onClick = onPlayPause,
                    colors = IconButtonDefaults.filledIconButtonColors()
                ) {
                    Icon(
                        if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        stringResource(if (state.isPlaying) R.string.pause else R.string.play)
                    )
                }
                IconButton(onClick = onNext) {
                    Icon(Icons.Default.SkipNext, stringResource(R.string.next))
                }
                IconButton(onClick = onRepeat) {
                    val active = state.repeatMode != Player.REPEAT_MODE_OFF
                    Icon(
                        if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne
                        else Icons.Default.Repeat,
                        stringResource(
                            when (state.repeatMode) {
                                Player.REPEAT_MODE_ALL -> R.string.repeat_all
                                Player.REPEAT_MODE_ONE -> R.string.repeat_one
                                else -> R.string.repeat_off
                            }
                        ),
                        tint = if (active) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.queue), style = MaterialTheme.typography.titleLarge)
            LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                itemsIndexed(state.queue) { index, item ->
                    ListItem(
                        headlineContent = { Text(item.title.ifBlank { unknownTitle }, maxLines = 1) },
                        supportingContent = {
                            Text(
                                separator.format(
                                    item.artist.ifBlank { unknownArtist },
                                    item.album.ifBlank { unknownAlbum }
                                ),
                                maxLines = 1
                            )
                        },
                        trailingContent = {
                            if (index == state.currentIndex) Text(stringResource(R.string.playing))
                        },
                        modifier = Modifier.clickable { onSelect(index) }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
