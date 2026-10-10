package com.gaabaariaa.music.feature.playlists

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gaabaariaa.music.R
import com.gaabaariaa.music.core.util.formatNumber
import com.gaabaariaa.music.domain.model.Playlist
import com.gaabaariaa.music.domain.model.SmartRule
import com.gaabaariaa.music.domain.model.SmartRuleType
import com.gaabaariaa.music.domain.repository.PlaylistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What menus and tabs need to add songs to playlists and create them, without extra plumbing. */
class PlaylistActions(
    val playlists: List<Playlist>,
    val addTo: (Long, List<Long>) -> Unit,
    val createAndAdd: (String, List<Long>) -> Unit,
    val create: (String, SmartRule?) -> Unit
)

val LocalPlaylistActions = staticCompositionLocalOf {
    PlaylistActions(emptyList(), { _, _ -> }, { _, _ -> }, { _, _ -> })
}

@HiltViewModel
class PlaylistsViewModel @Inject constructor(
    private val repository: PlaylistRepository
) : ViewModel() {
    val playlists: StateFlow<List<Playlist>> = repository.observePlaylists()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun addTo(playlistId: Long, songIds: List<Long>) {
        viewModelScope.launch { repository.addSongs(playlistId, songIds) }
    }

    fun createAndAdd(name: String, songIds: List<Long>) {
        viewModelScope.launch {
            val id = repository.createManual(name)
            repository.addSongs(id, songIds)
        }
    }

    fun create(name: String, rule: SmartRule?) {
        viewModelScope.launch {
            if (rule == null) repository.createManual(name) else repository.createSmart(name, rule)
        }
    }
}

@Composable
fun SmartRule.label(): String = when (type) {
    SmartRuleType.RECENTLY_ADDED -> stringResource(R.string.smart_recently_added)
    SmartRuleType.MOST_PLAYED -> stringResource(R.string.smart_most_played)
    SmartRuleType.NEVER_PLAYED -> stringResource(R.string.smart_never_played)
    SmartRuleType.FAVORITES -> stringResource(R.string.smart_favorites)
    SmartRuleType.GENRE -> stringResource(R.string.smart_genre, param)
    SmartRuleType.PERSIAN -> stringResource(R.string.smart_persian)
    SmartRuleType.HIGH_QUALITY -> stringResource(R.string.smart_high_quality)
    SmartRuleType.LONGER_THAN -> stringResource(R.string.smart_longer_than, formatNumber(param.toIntOrNull() ?: 5))
}

@Composable
private fun SmartRuleType.choiceLabel(): String = when (this) {
    SmartRuleType.GENRE -> stringResource(R.string.smart_type_genre)
    SmartRuleType.LONGER_THAN -> stringResource(R.string.smart_type_longer_than)
    else -> SmartRule(this).label()
}

/** Pick a normal playlist for these songs, or create a new one on the spot. */
@Composable
fun PlaylistPickerDialog(songIds: List<Long>, onDismiss: () -> Unit) {
    val actions = LocalPlaylistActions.current
    val context = LocalContext.current
    var creating by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    val manual = actions.playlists.filter { !it.isSmart }
    val resources = context.resources
    val added = stringResource(R.string.playlist_added)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.menu_add_to_playlist)) },
        text = {
            if (creating) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.playlist_name)) },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (manual.isEmpty()) Text(stringResource(R.string.playlist_none_manual))
                    manual.forEach { playlist ->
                        ListItem(
                            headlineContent = { Text(playlist.name, maxLines = 1) },
                            supportingContent = {
                                Text(resources.getQuantityString(R.plurals.songs_count, playlist.songCount, playlist.songCount))
                            },
                            modifier = Modifier.clickable {
                                actions.addTo(playlist.id, songIds)
                                Toast.makeText(context, added, Toast.LENGTH_SHORT).show()
                                onDismiss()
                            }
                        )
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {
            if (creating) {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = {
                        actions.createAndAdd(name, songIds)
                        Toast.makeText(context, added, Toast.LENGTH_SHORT).show()
                        onDismiss()
                    }
                ) { Text(stringResource(R.string.action_create)) }
            } else {
                TextButton(onClick = { creating = true }) { Text(stringResource(R.string.playlist_new)) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

@Composable
fun CreatePlaylistDialog(onDismiss: () -> Unit, onCreate: (String, SmartRule?) -> Unit) {
    var name by remember { mutableStateOf("") }
    var smart by remember { mutableStateOf(false) }
    var type by remember { mutableStateOf(SmartRuleType.RECENTLY_ADDED) }
    var param by remember { mutableStateOf("") }
    val needsParam = smart && (type == SmartRuleType.GENRE || type == SmartRuleType.LONGER_THAN)
    val valid = name.isNotBlank() && (!needsParam || param.isNotBlank() || type == SmartRuleType.LONGER_THAN)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.playlist_new)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.playlist_name)) },
                    modifier = Modifier.fillMaxWidth()
                )
                ChoiceRow(stringResource(R.string.playlist_type_manual), !smart) { smart = false }
                ChoiceRow(stringResource(R.string.playlist_type_smart), smart) { smart = true }
                if (smart) {
                    SmartRuleType.entries.forEach { option ->
                        ChoiceRow(option.choiceLabel(), type == option) {
                            type = option
                            param = if (option == SmartRuleType.LONGER_THAN) "5" else ""
                        }
                    }
                    if (needsParam) {
                        OutlinedTextField(
                            value = param,
                            onValueChange = { param = it },
                            singleLine = true,
                            label = {
                                Text(
                                    stringResource(
                                        if (type == SmartRuleType.GENRE) R.string.smart_param_genre
                                        else R.string.smart_param_minutes
                                    )
                                )
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = if (type == SmartRuleType.LONGER_THAN) KeyboardType.Number else KeyboardType.Text
                            ),
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = { onCreate(name, if (smart) SmartRule(type, param.trim()) else null) }
            ) { Text(stringResource(R.string.action_create)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, Modifier.padding(start = 12.dp))
    }
}

/** The playlists tab of the library. */
@Composable
fun PlaylistsTab(onOpen: (Long) -> Unit) {
    val actions = LocalPlaylistActions.current
    val resources = LocalContext.current.resources
    var creating by remember { mutableStateOf(false) }
    if (creating) {
        CreatePlaylistDialog(
            onDismiss = { creating = false },
            onCreate = { name, rule ->
                actions.create(name, rule)
                creating = false
            }
        )
    }
    Column(Modifier.fillMaxSize()) {
        Button(onClick = { creating = true }, modifier = Modifier.padding(12.dp)) {
            Icon(Icons.Default.Add, contentDescription = null)
            Text(stringResource(R.string.playlist_new), Modifier.padding(start = 8.dp))
        }
        if (actions.playlists.isEmpty()) {
            Text(stringResource(R.string.playlist_none), Modifier.padding(16.dp))
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(actions.playlists, key = { it.id }) { playlist ->
                    ListItem(
                        headlineContent = { Text(playlist.name, maxLines = 1) },
                        supportingContent = {
                            val rule = playlist.smartRule
                            Text(
                                if (rule != null) rule.label()
                                else resources.getQuantityString(R.plurals.songs_count, playlist.songCount, playlist.songCount)
                            )
                        },
                        leadingContent = {
                            Icon(
                                if (playlist.isSmart) Icons.Default.AutoAwesome else Icons.AutoMirrored.Filled.QueueMusic,
                                contentDescription = null
                            )
                        },
                        modifier = Modifier.clickable { onOpen(playlist.id) }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
