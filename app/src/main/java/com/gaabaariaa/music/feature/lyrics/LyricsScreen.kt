@file:OptIn(ExperimentalMaterial3Api::class)

package com.gaabaariaa.music.feature.lyrics

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gaabaariaa.music.R
import com.gaabaariaa.music.core.util.parseLyrics

@Composable
fun LyricsScreen(onBack: () -> Unit, viewModel: LyricsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val grantLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        viewModel.onGrantResult(it.resultCode == Activity.RESULT_OK)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.import(uri.toString())
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
            LyricsMessage.Saved -> context.getString(R.string.lyrics_saved)
            LyricsMessage.Deleted -> context.getString(R.string.lyrics_deleted)
            LyricsMessage.Imported -> context.getString(R.string.lyrics_imported)
            LyricsMessage.ImportFailed -> context.getString(R.string.lyrics_import_failed)
            LyricsMessage.WrittenToFile -> context.getString(R.string.lyrics_written)
            is LyricsMessage.WriteFailed -> context.getString(R.string.tags_failed, message.count)
            LyricsMessage.PermissionDenied -> context.getString(R.string.tags_permission_denied)
        }
        Toast.makeText(context, text, Toast.LENGTH_LONG).show()
        viewModel.consumeMessage()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.lyrics_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::save, enabled = state.dirty) {
                        Icon(Icons.Default.Check, stringResource(R.string.lyrics_save))
                    }
                }
            )
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize().imePadding()) {
            if (state.loading) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else {
                Column(
                    Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        state.title.ifBlank { stringResource(R.string.unknown_title) },
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        state.artist.ifBlank { stringResource(R.string.unknown_artist) },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    state.source?.let {
                        Text(
                            stringResource(R.string.lyrics_source, stringResource(sourceLabel(it))),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    OutlinedTextField(
                        value = state.text,
                        onValueChange = viewModel::onText,
                        label = { Text(stringResource(R.string.lyrics_title)) },
                        supportingText = { Text(stringResource(R.string.lyrics_format_hint)) },
                        minLines = 10,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = viewModel::searchOnline,
                        enabled = state.online !is OnlineState.Searching,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.lyrics_search_online)) }

                    when (val online = state.online) {
                        OnlineState.Searching -> CircularProgressIndicator()
                        is OnlineState.Found -> FoundCard(online, viewModel::applyOnline)
                        OnlineState.NotFound -> StatusText(R.string.lyrics_not_found)
                        OnlineState.Offline -> StatusText(R.string.artwork_offline)
                        OnlineState.Error -> StatusText(R.string.artwork_server_error)
                        OnlineState.Idle -> Unit
                    }

                    OutlinedButton(
                        onClick = { importLauncher.launch(arrayOf("*/*")) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.lyrics_import)) }
                    OutlinedButton(
                        onClick = viewModel::writeToFile,
                        enabled = !state.busy && state.text.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.lyrics_write_file)) }
                    OutlinedButton(
                        onClick = viewModel::deleteSaved,
                        enabled = state.source != null,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.lyrics_delete)) }
                    Text(
                        stringResource(R.string.lyrics_attribution),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (state.busy) CircularProgressIndicator(Modifier.align(Alignment.Center))
        }
    }
}

@Composable
private fun FoundCard(found: OnlineState.Found, onUse: () -> Unit) {
    val preview = remember(found) {
        parseLyrics(found.lyrics.content).lines.map { it.text }.filter { it.isNotBlank() }.take(5)
            .joinToString("\n")
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(if (found.lyrics.synced) R.string.lyrics_found_synced else R.string.lyrics_found_plain),
                style = MaterialTheme.typography.titleSmall
            )
            Text(preview, style = MaterialTheme.typography.bodySmall)
            Button(onClick = onUse) { Text(stringResource(R.string.lyrics_use)) }
        }
    }
}

@Composable
private fun StatusText(res: Int) {
    Text(stringResource(res), color = MaterialTheme.colorScheme.error)
}

private fun sourceLabel(source: String): Int = when (source) {
    "embedded" -> R.string.lyrics_source_embedded
    "online" -> R.string.lyrics_source_online
    "imported" -> R.string.lyrics_source_imported
    else -> R.string.lyrics_source_local
}
