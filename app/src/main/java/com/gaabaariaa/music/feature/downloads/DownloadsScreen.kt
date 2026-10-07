@file:OptIn(ExperimentalMaterial3Api::class)

package com.gaabaariaa.music.feature.downloads

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.gaabaariaa.music.core.util.formatBytes
import com.gaabaariaa.music.core.util.formatDuration
import com.gaabaariaa.music.core.util.remainingSeconds
import com.gaabaariaa.music.domain.model.DownloadItem
import com.gaabaariaa.music.domain.model.DownloadState
import com.gaabaariaa.music.domain.model.Song

@Composable
fun providerLabel(id: String): String = when (id) {
    "archive" -> "Internet Archive"
    "openverse" -> "Openverse"
    "commons" -> "Wikimedia Commons"
    "ccmixter" -> "ccMixter"
    "jamendo" -> "Jamendo"
    "direct" -> stringResource(R.string.source_direct)
    "feed" -> stringResource(R.string.source_feed)
    else -> id
}

@Composable
fun DownloadsScreen(
    onOpenSearch: () -> Unit,
    onPlay: (List<Song>, Int) -> Unit,
    viewModel: DownloadsViewModel = hiltViewModel()
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_downloads)) },
                actions = {
                    IconButton(onClick = viewModel::clearFinished) {
                        Icon(Icons.Default.ClearAll, stringResource(R.string.downloads_clear_finished))
                    }
                    IconButton(onClick = onOpenSearch) {
                        Icon(Icons.Default.Add, stringResource(R.string.downloads_add))
                    }
                }
            )
        },
        contentWindowInsets = WindowInsets(0.dp)
    ) { pad ->
        if (items.isEmpty()) {
            Column(Modifier.padding(pad).padding(16.dp)) {
                Text(stringResource(R.string.downloads_empty))
                TextButton(onClick = onOpenSearch) { Text(stringResource(R.string.downloads_add)) }
            }
        } else {
            LazyColumn(Modifier.padding(pad).fillMaxSize()) {
                section(R.string.dl_section_downloading, items.filter { it.state == DownloadState.DOWNLOADING }, viewModel, context, onPlay)
                section(R.string.dl_section_queued, items.filter { it.state == DownloadState.QUEUED }, viewModel, context, onPlay)
                section(R.string.dl_section_paused, items.filter { it.state == DownloadState.PAUSED }, viewModel, context, onPlay)
                section(R.string.dl_section_failed, items.filter { it.state == DownloadState.FAILED }, viewModel, context, onPlay)
                section(R.string.dl_section_done, items.filter { it.state == DownloadState.COMPLETED }, viewModel, context, onPlay)
            }
        }
    }
}

private fun LazyListScope.section(
    titleRes: Int,
    items: List<DownloadItem>,
    viewModel: DownloadsViewModel,
    context: android.content.Context,
    onPlay: (List<Song>, Int) -> Unit
) {
    if (items.isEmpty()) return
    item(key = "header:$titleRes") {
        Text(
            stringResource(titleRes),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
    items(items, key = { "download:" + it.id }) { item ->
        DownloadRow(item, viewModel) { songId ->
            viewModel.loadSong(songId) { song ->
                if (song != null) onPlay(listOf(song), 0)
                else Toast.makeText(context, R.string.dl_library_updating, Toast.LENGTH_SHORT).show()
            }
        }
        HorizontalDivider()
    }
}

@Composable
private fun DownloadRow(item: DownloadItem, viewModel: DownloadsViewModel, onPlaySong: (Long) -> Unit) {
    val unknownArtist = stringResource(R.string.unknown_artist)
    val separator = stringResource(R.string.two_parts)
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(item.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
        Text(
            separator.format(item.artist.ifBlank { unknownArtist }, providerLabel(item.providerId)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        when (item.state) {
            DownloadState.DOWNLOADING, DownloadState.PAUSED -> {
                if (item.totalBytes > 0) {
                    LinearProgressIndicator(
                        progress = { (item.downloadedBytes.toFloat() / item.totalBytes).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                } else if (item.state == DownloadState.DOWNLOADING) {
                    LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
                }
                Text(progressText(item), style = MaterialTheme.typography.labelMedium)
            }
            DownloadState.QUEUED -> Text(stringResource(R.string.dl_waiting), style = MaterialTheme.typography.labelMedium)
            DownloadState.FAILED -> Text(
                errorText(item.error), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error
            )
            DownloadState.COMPLETED -> Text(
                stringResource(R.string.dl_saved) + " • " + item.license,
                style = MaterialTheme.typography.labelMedium
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            when (item.state) {
                DownloadState.DOWNLOADING -> {
                    TextButton(onClick = { viewModel.pause(item.id) }) { Text(stringResource(R.string.dl_pause)) }
                    TextButton(onClick = { viewModel.cancel(item.id) }) { Text(stringResource(R.string.dl_cancel)) }
                }
                DownloadState.QUEUED ->
                    TextButton(onClick = { viewModel.cancel(item.id) }) { Text(stringResource(R.string.dl_cancel)) }
                DownloadState.PAUSED -> {
                    TextButton(onClick = { viewModel.resume(item.id) }) { Text(stringResource(R.string.dl_resume)) }
                    TextButton(onClick = { viewModel.cancel(item.id) }) { Text(stringResource(R.string.dl_cancel)) }
                }
                DownloadState.FAILED -> {
                    TextButton(onClick = { viewModel.retry(item.id) }) { Text(stringResource(R.string.dl_retry)) }
                    TextButton(onClick = { viewModel.cancel(item.id) }) { Text(stringResource(R.string.dl_remove)) }
                }
                DownloadState.COMPLETED -> {
                    if (item.songId > 0) {
                        TextButton(onClick = { onPlaySong(item.songId) }) { Text(stringResource(R.string.dl_play)) }
                    }
                    TextButton(onClick = { viewModel.cancel(item.id) }) { Text(stringResource(R.string.dl_remove)) }
                }
            }
        }
    }
}

@Composable
private fun progressText(item: DownloadItem): String {
    val done = formatBytes(item.downloadedBytes)
    val base = if (item.totalBytes > 0) {
        stringResource(R.string.dl_progress, done, formatBytes(item.totalBytes))
    } else {
        stringResource(R.string.dl_progress_unknown, done)
    }
    if (item.state != DownloadState.DOWNLOADING || item.speedBps <= 0) {
        return if (item.state == DownloadState.PAUSED) base + " • " + stringResource(R.string.dl_section_paused) else base
    }
    val parts = mutableListOf(base, stringResource(R.string.dl_speed, formatBytes(item.speedBps)))
    remainingSeconds(item.downloadedBytes, item.totalBytes, item.speedBps)?.let {
        parts += stringResource(R.string.dl_remaining, formatDuration(it * 1000))
    }
    return parts.joinToString(" • ")
}

@Composable
private fun errorText(code: String): String = when {
    code == "NETWORK" -> stringResource(R.string.dl_error_network)
    code.startsWith("HTTP_") -> stringResource(R.string.dl_error_http, code.removePrefix("HTTP_"))
    code == "NOT_AUDIO" -> stringResource(R.string.dl_error_not_audio)
    code == "TOO_LARGE" -> stringResource(R.string.dl_error_too_large)
    code == "INVALID_FILE" -> stringResource(R.string.dl_error_invalid)
    code == "STORAGE" -> stringResource(R.string.dl_error_storage)
    code == "INSECURE" || code == "TOO_MANY_REDIRECTS" -> stringResource(R.string.dl_error_insecure)
    else -> stringResource(R.string.dl_error_unknown)
}
