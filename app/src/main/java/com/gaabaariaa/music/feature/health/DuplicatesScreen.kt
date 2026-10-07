@file:OptIn(ExperimentalMaterial3Api::class)

package com.gaabaariaa.music.feature.health

import android.app.Activity
import android.content.IntentSender
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.gaabaariaa.music.R
import com.gaabaariaa.music.core.util.findDuplicateGroups
import com.gaabaariaa.music.core.util.formatDuration
import com.gaabaariaa.music.domain.model.Song
import com.gaabaariaa.music.domain.repository.LibraryRepository
import com.gaabaariaa.music.domain.repository.LibraryScanner
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface DuplicatesMessage {
    data object KeepOne : DuplicatesMessage
    data class Deleted(val count: Int) : DuplicatesMessage
    data object Failed : DuplicatesMessage
}

data class DuplicatesState(
    val groups: List<List<Song>> = emptyList(),
    val selected: Set<Long> = emptySet(),
    val grantRequest: IntentSender? = null,
    val confirmLegacy: Boolean = false,
    val busy: Boolean = false,
    val message: DuplicatesMessage? = null
)

@HiltViewModel
class DuplicatesViewModel @Inject constructor(
    repository: LibraryRepository,
    private val library: LibraryRepository,
    private val scanner: LibraryScanner
) : ViewModel() {

    private val _state = MutableStateFlow(DuplicatesState())
    val state: StateFlow<DuplicatesState> = _state.asStateFlow()
    private var pendingIds: List<Long> = emptyList()

    init {
        viewModelScope.launch {
            repository.observeSongs()
                .map { findDuplicateGroups(it) }
                .flowOn(Dispatchers.Default)
                .collectLatest { groups ->
                    val existing = groups.flatten().map { it.id }.toSet()
                    _state.update { it.copy(groups = groups, selected = it.selected.intersect(existing)) }
                }
        }
    }

    /** Never lets the user select every copy of a song. */
    fun toggle(song: Song, group: List<Song>) {
        val current = _state.value.selected
        val next = if (song.id in current) current - song.id else current + song.id
        if (group.all { it.id in next }) {
            _state.update { it.copy(message = DuplicatesMessage.KeepOne) }
        } else {
            _state.update { it.copy(selected = next) }
        }
    }

    fun requestDelete() {
        val ids = _state.value.selected.toList()
        if (ids.isEmpty()) return
        pendingIds = ids
        val sender = library.createDeleteRequest(ids)
        if (sender != null) _state.update { it.copy(grantRequest = sender) }
        else _state.update { it.copy(confirmLegacy = true) }
    }

    fun consumeGrantRequest() = _state.update { it.copy(grantRequest = null) }
    fun consumeMessage() = _state.update { it.copy(message = null) }
    fun dismissLegacy() = _state.update { it.copy(confirmLegacy = false) }

    fun onGrantResult(granted: Boolean) {
        if (!granted) return
        viewModelScope.launch {
            library.forget(pendingIds)
            scanner.scanNow(force = true)
            _state.update { it.copy(selected = emptySet(), message = DuplicatesMessage.Deleted(pendingIds.size)) }
        }
    }

    fun confirmLegacyDelete() {
        _state.update { it.copy(confirmLegacy = false, busy = true) }
        viewModelScope.launch {
            val deleted = library.deleteDirect(pendingIds)
            _state.update {
                it.copy(
                    busy = false,
                    selected = emptySet(),
                    message = if (deleted > 0) DuplicatesMessage.Deleted(deleted) else DuplicatesMessage.Failed
                )
            }
        }
    }
}

@Composable
fun DuplicatesScreen(onBack: () -> Unit, viewModel: DuplicatesViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        viewModel.onGrantResult(it.resultCode == Activity.RESULT_OK)
    }
    LaunchedEffect(state.grantRequest) {
        state.grantRequest?.let {
            launcher.launch(IntentSenderRequest.Builder(it).build())
            viewModel.consumeGrantRequest()
        }
    }
    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        val text = when (message) {
            DuplicatesMessage.KeepOne -> context.getString(R.string.duplicates_keep_one)
            is DuplicatesMessage.Deleted -> context.getString(R.string.duplicates_deleted, message.count)
            DuplicatesMessage.Failed -> context.getString(R.string.duplicates_delete_failed)
        }
        Toast.makeText(context, text, Toast.LENGTH_LONG).show()
        viewModel.consumeMessage()
    }

    if (state.confirmLegacy) {
        AlertDialog(
            onDismissRequest = viewModel::dismissLegacy,
            title = { Text(stringResource(R.string.duplicates_confirm_title)) },
            text = { Text(stringResource(R.string.duplicates_confirm_text, state.selected.size)) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmLegacyDelete) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissLegacy) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.duplicates_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Button(
                    onClick = viewModel::requestDelete,
                    enabled = state.selected.isNotEmpty() && !state.busy,
                    modifier = Modifier.fillMaxWidth().padding(12.dp)
                ) { Text(stringResource(R.string.duplicates_delete, state.selected.size)) }
            }
        }
    ) { pad ->
        if (state.groups.isEmpty()) {
            Text(stringResource(R.string.duplicates_none), Modifier.padding(pad).padding(16.dp))
        } else {
            LazyColumn(Modifier.padding(pad).fillMaxSize()) {
                items(state.groups, key = { group -> group.first().id }) { group ->
                    Column {
                        Text(
                            group.first().title.ifBlank { stringResource(R.string.unknown_title) },
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp)
                        )
                        group.forEach { song ->
                            ListItem(
                                leadingContent = {
                                    Checkbox(checked = song.id in state.selected, onCheckedChange = null)
                                },
                                headlineContent = { Text(song.path.ifBlank { song.title }, maxLines = 2) },
                                supportingContent = {
                                    Text(
                                        formatDuration(song.durationMs) + " • " +
                                            String.format("%.1f MB", song.sizeBytes / 1_048_576.0)
                                    )
                                },
                                modifier = Modifier.clickable { viewModel.toggle(song, group) }
                            )
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
