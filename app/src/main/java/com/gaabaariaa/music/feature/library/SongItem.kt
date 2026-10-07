package com.gaabaariaa.music.feature.library

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.gaabaariaa.music.R
import com.gaabaariaa.music.core.designsystem.LocalAppSettings
import com.gaabaariaa.music.core.util.formatDuration
import com.gaabaariaa.music.domain.model.Song
import com.gaabaariaa.music.feature.player.SongArtwork

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongItem(
    song: Song,
    onClick: () -> Unit,
    onPlayNext: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onEditTags: (Song) -> Unit,
    onFindArtwork: (Song) -> Unit,
    onFindLyrics: (Song) -> Unit,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onSelect: (() -> Unit)? = null
) {
    var menuOpen by remember { mutableStateOf(false) }
    val separator = stringResource(R.string.two_parts)
    val artist = song.artist.ifBlank { stringResource(R.string.unknown_artist) }
    val album = song.album.ifBlank { stringResource(R.string.unknown_album) }
    val showArtwork = LocalAppSettings.current.showArtwork
    val favorite = song.id in LocalFavorites.current.ids

    Box {
        ListItem(
            headlineContent = { Text(song.title.ifBlank { stringResource(R.string.unknown_title) }, maxLines = 1) },
            supportingContent = { Text(separator.format(artist, album), maxLines = 1) },
            leadingContent = when {
                selectionMode -> {
                    { Checkbox(checked = selected, onCheckedChange = null) }
                }
                showArtwork -> {
                    { SongArtwork(song.id.toString(), Modifier.size(48.dp), sizePx = 128) }
                }
                else -> null
            },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (favorite) {
                        Icon(
                            Icons.Default.Favorite,
                            contentDescription = stringResource(R.string.favorite),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(formatDuration(song.durationMs), Modifier.padding(start = 6.dp))
                    if (!selectionMode) {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Default.MoreVert, stringResource(R.string.more_options))
                        }
                    }
                }
            },
            modifier = Modifier.combinedClickable(
                onClick = onClick,
                onLongClick = { if (!selectionMode) menuOpen = true }
            )
        )
        SongMenu(
            song = song,
            expanded = menuOpen,
            onDismiss = { menuOpen = false },
            onPlayNext = onPlayNext,
            onAddToQueue = onAddToQueue,
            onEditTags = onEditTags,
            onFindArtwork = onFindArtwork,
            onFindLyrics = onFindLyrics,
            onSelect = onSelect
        )
    }
}

@Composable
fun SongMenu(
    song: Song,
    expanded: Boolean,
    onDismiss: () -> Unit,
    onPlayNext: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onEditTags: (Song) -> Unit,
    onFindArtwork: (Song) -> Unit,
    onFindLyrics: (Song) -> Unit,
    onSelect: (() -> Unit)?
) {
    val context = LocalContext.current
    val favorites = LocalFavorites.current
    val isFavorite = song.id in favorites.ids
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(stringResource(if (isFavorite) R.string.menu_unfavorite else R.string.menu_favorite)) },
            onClick = {
                onDismiss()
                favorites.toggle(song.id)
            }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.menu_play_next)) },
            onClick = {
                onDismiss()
                onPlayNext(song)
                Toast.makeText(context, R.string.toast_play_next, Toast.LENGTH_SHORT).show()
            }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.menu_add_to_queue)) },
            onClick = {
                onDismiss()
                onAddToQueue(song)
                Toast.makeText(context, R.string.toast_added_to_queue, Toast.LENGTH_SHORT).show()
            }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.menu_edit_tags)) },
            onClick = {
                onDismiss()
                onEditTags(song)
            }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.menu_find_artwork)) },
            onClick = {
                onDismiss()
                onFindArtwork(song)
            }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.menu_find_lyrics)) },
            onClick = {
                onDismiss()
                onFindLyrics(song)
            }
        )
        if (onSelect != null) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_select)) },
                onClick = {
                    onDismiss()
                    onSelect()
                }
            )
        }
        DropdownMenuItem(
            text = { Text(stringResource(R.string.menu_share)) },
            onClick = {
                onDismiss()
                shareSong(context, song)
            }
        )
    }
}

private fun shareSong(context: Context, song: Song) {
    val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, song.id)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = song.mimeType.ifBlank { "audio/*" }
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, song.title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
