@file:OptIn(ExperimentalMaterial3Api::class)

package com.gaabaariaa.music.feature.tags

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gaabaariaa.music.R
import com.gaabaariaa.music.domain.model.TagField

@Composable
fun TagEditorScreen(onBack: () -> Unit, viewModel: TagEditorViewModel = hiltViewModel()) {
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
    LaunchedEffect(state.result) {
        val result = state.result ?: return@LaunchedEffect
        val message = when (result) {
            is TagSaveResult.Saved -> context.getString(R.string.tags_saved)
            TagSaveResult.NoChanges -> context.getString(R.string.tags_no_changes)
            TagSaveResult.PermissionDenied -> context.getString(R.string.tags_permission_denied)
            is TagSaveResult.Failed -> context.getString(R.string.tags_failed, result.failed)
        }
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        viewModel.consumeResult()
        if (result is TagSaveResult.Saved) onBack()
    }

    val resources = context.resources
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (state.multi) {
                            resources.getQuantityString(R.plurals.tags_title_multi, state.songCount, state.songCount)
                        } else {
                            stringResource(R.string.tags_title)
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::save, enabled = state.dirty && !state.saving) {
                        Icon(Icons.Default.Check, stringResource(R.string.tags_save))
                    }
                }
            )
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize().imePadding()) {
            when {
                state.loading || state.saving -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.nothingToEdit -> Text(stringResource(R.string.tags_unreadable_all), Modifier.padding(16.dp))
                else -> Column(
                    Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (state.unreadable > 0) {
                        Text(
                            stringResource(R.string.tags_unreadable_some, state.unreadable),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    val fields = if (state.multi) {
                        // Per-track fields make no sense when editing several songs at once.
                        TagField.entries - setOf(TagField.TITLE, TagField.TRACK, TagField.LYRICS)
                    } else {
                        TagField.entries.toList()
                    }
                    fields.forEach { field ->
                        OutlinedTextField(
                            value = state.values[field].orEmpty(),
                            onValueChange = { viewModel.onChange(field, it) },
                            label = { Text(stringResource(field.label())) },
                            placeholder = {
                                if (field in state.mixed) Text(stringResource(R.string.tags_multiple_values))
                            },
                            singleLine = field != TagField.LYRICS && field != TagField.COMMENT,
                            minLines = if (field == TagField.LYRICS) 6 else 1,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = if (field == TagField.YEAR) KeyboardType.Number else KeyboardType.Text
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

private fun TagField.label(): Int = when (this) {
    TagField.TITLE -> R.string.field_title
    TagField.ARTIST -> R.string.field_artist
    TagField.ALBUM -> R.string.field_album
    TagField.ALBUM_ARTIST -> R.string.field_album_artist
    TagField.GENRE -> R.string.field_genre
    TagField.YEAR -> R.string.field_year
    TagField.TRACK -> R.string.field_track
    TagField.DISC -> R.string.field_disc
    TagField.COMPOSER -> R.string.field_composer
    TagField.COMMENT -> R.string.field_comment
    TagField.LYRICS -> R.string.field_lyrics
}
