@file:OptIn(ExperimentalMaterial3Api::class)

package com.gaabaariaa.music.feature.downloads

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gaabaariaa.music.R
import com.gaabaariaa.music.core.util.formatDuration
import com.gaabaariaa.music.domain.model.SourceError
import com.gaabaariaa.music.domain.model.SourceInput

@Composable
fun SourceSearchScreen(onBack: () -> Unit, viewModel: SourceSearchViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val selected = viewModel.sources.firstOrNull { it.id == state.source }
    val isUrlSource = selected?.input == SourceInput.URL
    val unknownArtist = stringResource(R.string.unknown_artist)
    val separator = stringResource(R.string.two_parts)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.source_search_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                }
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Card(Modifier.fillMaxWidth().padding(12.dp)) {
                Text(
                    stringResource(R.string.source_legal_notice),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(12.dp)
                )
            }
            LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = state.source == "all",
                        onClick = { viewModel.selectSource("all") },
                        label = { Text(stringResource(R.string.source_all)) }
                    )
                }
                items(viewModel.sources, key = { it.id }) { source ->
                    FilterChip(
                        selected = state.source == source.id,
                        onClick = { viewModel.selectSource(source.id) },
                        label = { Text(providerLabel(source.id)) }
                    )
                }
            }
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                singleLine = true,
                placeholder = {
                    Text(stringResource(if (isUrlSource) R.string.source_hint_url else R.string.source_hint_search))
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { viewModel.search() }),
                modifier = Modifier.fillMaxWidth().padding(12.dp)
            )
            if (state.searching) LinearProgressIndicator(Modifier.fillMaxWidth())

            state.errors.forEach { (id, error) ->
                Text(
                    providerLabel(id) + ": " + stringResource(error.message()),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                )
            }
            if (state.searched && state.tracks.isEmpty() && state.errors.isEmpty()) {
                Text(stringResource(R.string.source_no_results), Modifier.padding(16.dp))
            }

            LazyColumn(Modifier.fillMaxSize()) {
                items(state.tracks, key = { it.providerId + "|" + it.remoteId }) { track ->
                    val added = (track.providerId + "|" + track.remoteId) in state.added
                    ListItem(
                        headlineContent = { Text(track.title, maxLines = 2) },
                        supportingContent = {
                            Column {
                                Text(
                                    separator.format(
                                        track.artist.ifBlank { unknownArtist },
                                        track.album.ifBlank { track.durationSec?.let { formatDuration(it * 1000L) }.orEmpty() }
                                    ).trimEnd(' ', '•'),
                                    maxLines = 1
                                )
                                Text(
                                    stringResource(R.string.license_label, track.license) + " • " +
                                        providerLabel(track.providerId),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1
                                )
                            }
                        },
                        trailingContent = {
                            IconButton(onClick = { viewModel.download(track) }, enabled = !added) {
                                Icon(
                                    if (added) Icons.Default.Check else Icons.Default.Download,
                                    stringResource(if (added) R.string.dl_added else R.string.dl_download)
                                )
                            }
                        },
                        modifier = Modifier.clickable(enabled = track.pageUrl.startsWith("https://")) {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(track.pageUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

private fun SourceError.message(): Int = when (this) {
    SourceError.OFFLINE -> R.string.artwork_offline
    SourceError.SERVER -> R.string.artwork_server_error
    SourceError.NOT_CONFIGURED -> R.string.source_not_configured
    SourceError.INVALID_INPUT -> R.string.source_invalid_input
}
