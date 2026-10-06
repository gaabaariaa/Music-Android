package com.gaabaariaa.music.feature.library

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.gaabaariaa.music.R
import com.gaabaariaa.music.core.util.formatDuration
import com.gaabaariaa.music.domain.model.Song

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongItem(
    song: Song,
    onClick: () -> Unit,
    onPlayNext: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val separator = stringResource(R.string.two_parts)
    val artist = song.artist.ifBlank { stringResource(R.string.unknown_artist) }
    val album = song.album.ifBlank { stringResource(R.string.unknown_album) }

    Box {
        ListItem(
            headlineContent = { Text(song.title.ifBlank { stringResource(R.string.unknown_title) }, maxLines = 1) },
            supportingContent = { Text(separator.format(artist, album), maxLines = 1) },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(formatDuration(song.durationMs))
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, stringResource(R.string.more_options))
                    }
                }
            },
            modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = { menuOpen = true })
        )
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_play_next)) },
                onClick = {
                    menuOpen = false
                    onPlayNext(song)
                    Toast.makeText(context, R.string.toast_play_next, Toast.LENGTH_SHORT).show()
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_add_to_queue)) },
                onClick = {
                    menuOpen = false
                    onAddToQueue(song)
                    Toast.makeText(context, R.string.toast_added_to_queue, Toast.LENGTH_SHORT).show()
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_share)) },
                onClick = {
                    menuOpen = false
                    shareSong(context, song)
                }
            )
        }
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
