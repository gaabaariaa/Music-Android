@file:OptIn(ExperimentalMaterial3Api::class)

package com.gaabaariaa.music.feature.artwork

import android.app.Activity
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gaabaariaa.music.R
import com.gaabaariaa.music.domain.model.ArtworkError
import com.gaabaariaa.music.feature.player.SongArtwork

@Composable
fun ArtworkScreen(onBack: () -> Unit, viewModel: ArtworkViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val grantLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        viewModel.onGrantResult(it.resultCode == Activity.RESULT_OK)
    }
    LaunchedEffect(state.grantRequest) {
        state.grantRequest?.let {
            grantLauncher.launch(IntentSenderRequest.Builder(it).build())
            viewModel.consumeGrantRequest()
        }
    }
    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        val text = when (message) {
            is ArtworkMessage.Embedded -> context.getString(R.string.artwork_embedded)
            is ArtworkMessage.Removed -> context.getString(R.string.artwork_removed)
            is ArtworkMessage.Failed -> context.getString(R.string.tags_failed, message.count)
            ArtworkMessage.SavedToPictures -> context.getString(R.string.artwork_saved_pictures)
            ArtworkMessage.SaveFailed -> context.getString(R.string.artwork_save_failed)
            ArtworkMessage.DownloadFailed -> context.getString(R.string.artwork_download_failed)
            ArtworkMessage.PermissionDenied -> context.getString(R.string.tags_permission_denied)
        }
        Toast.makeText(context, text, Toast.LENGTH_LONG).show()
        viewModel.consumeMessage()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.artwork_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = viewModel::embedSelected,
                            enabled = state.selectedId != null && !state.working,
                            modifier = Modifier.weight(1f)
                        ) { Text(stringResource(R.string.artwork_embed)) }
                        OutlinedButton(
                            onClick = viewModel::saveSelectedToPictures,
                            enabled = state.selectedId != null && !state.working && state.canSaveToPictures,
                            modifier = Modifier.weight(1f)
                        ) { Text(stringResource(R.string.artwork_save_pictures)) }
                    }
                    OutlinedButton(
                        onClick = viewModel::removeEmbedded,
                        enabled = !state.working,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.artwork_remove)) }
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(140.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            state.previewSongId?.let {
                                SongArtwork(it.toString(), Modifier.size(88.dp), sizePx = 256)
                            }
                            Text(
                                LocalContext.current.resources.getQuantityString(
                                    R.plurals.songs_count, state.songCount, state.songCount
                                ),
                                modifier = Modifier.padding(start = 16.dp),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        OutlinedTextField(
                            value = state.title, onValueChange = viewModel::onTitle, singleLine = true,
                            label = { Text(stringResource(R.string.field_title)) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = state.artist, onValueChange = viewModel::onArtist, singleLine = true,
                            label = { Text(stringResource(R.string.field_artist)) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = state.album, onValueChange = viewModel::onAlbum, singleLine = true,
                            label = { Text(stringResource(R.string.field_album)) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(onClick = viewModel::search, enabled = !state.searching, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Search, contentDescription = null)
                            Text(stringResource(R.string.artwork_search), Modifier.padding(start = 8.dp))
                        }
                        if (state.searching) LinearProgressIndicator(Modifier.fillMaxWidth())
                        val status = when {
                            state.error == ArtworkError.OFFLINE -> R.string.artwork_offline
                            state.error == ArtworkError.SERVER -> R.string.artwork_server_error
                            state.searched && state.results.isEmpty() -> R.string.artwork_no_results
                            else -> null
                        }
                        if (status != null) {
                            Text(stringResource(status), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                items(state.results, key = { it.candidate.releaseId }) { result ->
                    val bitmap = remember(result) {
                        BitmapFactory.decodeByteArray(result.thumbnail, 0, result.thumbnail.size)?.asImageBitmap()
                    }
                    val selected = state.selectedId == result.candidate.releaseId
                    Column(Modifier.clickable { viewModel.select(result.candidate.releaseId) }) {
                        val shape = RoundedCornerShape(12.dp)
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(shape)
                                .then(
                                    if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, shape)
                                    else Modifier
                                )
                        ) {
                            if (bitmap != null) {
                                Image(
                                    bitmap, contentDescription = result.candidate.title,
                                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                        Text(
                            listOf(result.candidate.title, result.candidate.year)
                                .filter { it.isNotBlank() }.joinToString(" • "),
                            style = MaterialTheme.typography.bodySmall, maxLines = 2,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        Text(
                            result.candidate.artist, style = MaterialTheme.typography.labelSmall, maxLines = 1,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        stringResource(R.string.artwork_attribution),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (state.working) CircularProgressIndicator(Modifier.align(Alignment.Center))
        }
    }
}
