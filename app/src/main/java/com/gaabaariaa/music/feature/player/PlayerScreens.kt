@file:OptIn(ExperimentalMaterial3Api::class)

package com.gaabaariaa.music.feature.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.gaabaariaa.music.R
import com.gaabaariaa.music.core.util.formatDuration
import com.gaabaariaa.music.feature.lyrics.LyricsPane

private val speedOptions = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
private val sleepOptions = listOf(15, 30, 45, 60)

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
            SongArtwork(state.mediaId, Modifier.size(44.dp), sizePx = 128)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
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
    onRepeat: () -> Unit,
    onSpeed: (Float) -> Unit,
    onSleepTimer: (Int) -> Unit,
    onCancelSleepTimer: () -> Unit,
    onOpenLyrics: (Long) -> Unit
) {
    // Two panes on tablets and phones in landscape.
    val wide = LocalConfiguration.current.screenWidthDp >= 600

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
        if (wide) {
            Row(Modifier.padding(pad).fillMaxSize().padding(horizontal = 20.dp)) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    PlayerPane(
                        state, 200.dp, onPrevious, onPlayPause, onNext, onSeek, onShuffle, onRepeat,
                        onSpeed, onSleepTimer, onCancelSleepTimer
                    )
                }
                Spacer(Modifier.size(20.dp))
                Column(Modifier.weight(1f)) { QueueAndLyricsPane(state, onSelect, onSeek, onOpenLyrics) }
            }
        } else {
            Column(Modifier.padding(pad).fillMaxSize().padding(horizontal = 20.dp)) {
                PlayerPane(
                    state, 220.dp, onPrevious, onPlayPause, onNext, onSeek, onShuffle, onRepeat,
                    onSpeed, onSleepTimer, onCancelSleepTimer
                )
                QueueAndLyricsPane(state, onSelect, onSeek, onOpenLyrics)
            }
        }
    }
}

@Composable
private fun ColumnScope.PlayerPane(
    state: PlayerUiState,
    artworkSize: androidx.compose.ui.unit.Dp,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onSpeed: (Float) -> Unit,
    onSleepTimer: (Int) -> Unit,
    onCancelSleepTimer: () -> Unit
) {
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val max = state.durationMs.coerceAtLeast(1L).toFloat()
    val shown = if (dragging) dragValue else state.positionMs.toFloat()

    Spacer(Modifier.height(8.dp))
    SongArtwork(state.mediaId, Modifier.size(artworkSize).align(Alignment.CenterHorizontally))
    Spacer(Modifier.height(16.dp))
    Text(
        state.title.ifBlank { stringResource(R.string.unknown_title) },
        style = MaterialTheme.typography.headlineSmall,
        maxLines = 2
    )
    Text(state.artist.ifBlank { stringResource(R.string.unknown_artist) }, style = MaterialTheme.typography.titleMedium)
    Text(state.album.ifBlank { stringResource(R.string.unknown_album) }, style = MaterialTheme.typography.bodyMedium)

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
        FilledIconButton(onClick = onPlayPause) {
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
                if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                stringResource(
                    when (state.repeatMode) {
                        Player.REPEAT_MODE_ALL -> R.string.repeat_all
                        Player.REPEAT_MODE_ONE -> R.string.repeat_one
                        else -> R.string.repeat_off
                    }
                ),
                tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        SpeedButton(state.speed, onSpeed)
        SleepTimerButton(state.sleepRemainingMs, onSleepTimer, onCancelSleepTimer)
    }
}

@Composable
private fun SpeedButton(speed: Float, onSpeed: (Float) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) {
            Icon(Icons.Default.Speed, contentDescription = stringResource(R.string.playback_speed))
            Text("${speed}×", Modifier.padding(start = 8.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            speedOptions.forEach { option ->
                DropdownMenuItem(
                    text = { Text("${option}×") },
                    onClick = {
                        open = false
                        onSpeed(option)
                    }
                )
            }
        }
    }
}

@Composable
private fun SleepTimerButton(remainingMs: Long, onStart: (Int) -> Unit, onCancel: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) {
            Icon(Icons.Default.Timer, contentDescription = stringResource(R.string.sleep_timer))
            Text(
                if (remainingMs > 0) stringResource(R.string.sleep_remaining, formatDuration(remainingMs))
                else stringResource(R.string.sleep_timer),
                Modifier.padding(start = 8.dp)
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            sleepOptions.forEach { minutes ->
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.sleep_minutes, minutes)) },
                    onClick = {
                        open = false
                        onStart(minutes)
                    }
                )
            }
            if (remainingMs > 0) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.sleep_cancel)) },
                    onClick = {
                        open = false
                        onCancel()
                    }
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.QueueAndLyricsPane(
    state: PlayerUiState,
    onSelect: (Int) -> Unit,
    onSeek: (Long) -> Unit,
    onOpenLyrics: (Long) -> Unit
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    TabRow(selectedTabIndex = tab) {
        Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.queue)) })
        Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.lyrics_title)) })
    }
    if (tab == 0) {
        QueuePane(state, onSelect)
    } else {
        LyricsPane(
            mediaId = state.mediaId,
            positionMs = state.positionMs,
            onSeek = onSeek,
            onOpenEditor = onOpenLyrics,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ColumnScope.QueuePane(state: PlayerUiState, onSelect: (Int) -> Unit) {
    val unknownTitle = stringResource(R.string.unknown_title)
    val unknownArtist = stringResource(R.string.unknown_artist)
    val unknownAlbum = stringResource(R.string.unknown_album)
    val separator = stringResource(R.string.two_parts)

    LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
        itemsIndexed(state.queue) { index, item ->
            ListItem(
                headlineContent = { Text(item.title.ifBlank { unknownTitle }, maxLines = 1) },
                supportingContent = {
                    Text(
                        separator.format(item.artist.ifBlank { unknownArtist }, item.album.ifBlank { unknownAlbum }),
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
