@file:OptIn(ExperimentalFoundationApi::class)

package com.gaabaariaa.music.feature.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gaabaariaa.music.R
import com.gaabaariaa.music.core.designsystem.LocalAppSettings
import com.gaabaariaa.music.domain.model.LibraryLayout
import com.gaabaariaa.music.domain.model.Song
import com.gaabaariaa.music.feature.player.SongArtwork

/** One row/tile of the artists, albums, genres and folders tabs. */
data class SummaryUi(
    val key: String,
    val title: String,
    val subtitle: String,
    val coverSongId: Long,
    val value: String
)

@Composable
fun SummaryCollection(items: List<SummaryUi>, onOpen: (String) -> Unit) {
    if (items.isEmpty()) {
        Text(stringResource(R.string.empty_generic), Modifier.padding(16.dp))
        return
    }
    val settings = LocalAppSettings.current
    if (settings.libraryLayout == LibraryLayout.GRID) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(settings.gridColumns),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(items, key = { it.key }) { item ->
                Card(Modifier.fillMaxWidth().clickable { onOpen(item.value) }) {
                    if (settings.showArtwork) {
                        SongArtwork(item.coverSongId.toString(), Modifier.fillMaxWidth().aspectRatio(1f))
                    }
                    Column(Modifier.padding(10.dp)) {
                        Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                        Text(
                            item.subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    } else {
        LazyColumn(Modifier.fillMaxSize()) {
            items(items, key = { it.key }) { item ->
                ListItem(
                    headlineContent = { Text(item.title, maxLines = 2) },
                    supportingContent = { Text(item.subtitle, maxLines = 1) },
                    leadingContent = if (settings.showArtwork) {
                        { SongArtwork(item.coverSongId.toString(), Modifier.size(48.dp), sizePx = 128) }
                    } else {
                        null
                    },
                    modifier = Modifier.clickable { onOpen(item.value) }
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
fun SongGrid(
    songs: List<Song>,
    columns: Int,
    selection: Set<Long>,
    onPlay: (List<Song>, Int) -> Unit,
    onPlayNext: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onEditTags: (Song) -> Unit,
    onFindArtwork: (Song) -> Unit,
    onFindLyrics: (Song) -> Unit,
    onToggleSelect: (Long) -> Unit
) {
    val showArtwork = LocalAppSettings.current.showArtwork
    val unknownTitle = stringResource(R.string.unknown_title)
    val unknownArtist = stringResource(R.string.unknown_artist)
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(songs, key = { it.id }) { song ->
            var menuOpen by remember { mutableStateOf(false) }
            val selectionMode = selection.isNotEmpty()
            val selected = song.id in selection
            Box {
                Card(
                    Modifier.fillMaxWidth().semantics { this.selected = selected }.combinedClickable(
                        onClick = {
                            if (selectionMode) onToggleSelect(song.id) else onPlay(songs, songs.indexOf(song))
                        },
                        onLongClick = { if (selectionMode) onToggleSelect(song.id) else menuOpen = true }
                    )
                ) {
                    if (showArtwork) SongArtwork(song.id.toString(), Modifier.fillMaxWidth().aspectRatio(1f))
                    Column(Modifier.padding(10.dp)) {
                        Text(
                            (if (selected) "✓ " else "") + song.title.ifBlank { unknownTitle },
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            song.artist.ifBlank { unknownArtist }, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                SongMenu(
                    song = song,
                    expanded = menuOpen,
                    onDismiss = { menuOpen = false },
                    onPlayNext = onPlayNext,
                    onAddToQueue = onAddToQueue,
                    onEditTags = onEditTags,
                    onFindArtwork = onFindArtwork,
                    onFindLyrics = onFindLyrics,
                    onSelect = { onToggleSelect(song.id) }
                )
            }
        }
    }
}
