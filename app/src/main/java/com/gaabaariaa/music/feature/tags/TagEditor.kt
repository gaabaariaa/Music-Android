package com.gaabaariaa.music.feature.tags

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gaabaariaa.music.domain.model.TagField
import com.gaabaariaa.music.domain.model.TagValues
import com.gaabaariaa.music.domain.repository.TagRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Hands the selected song ids to the editor without putting thousands of ids into a route. */
@Singleton
class TagEditSession @Inject constructor() {
    @Volatile
    var songIds: List<Long> = emptyList()
}

sealed interface TagSaveResult {
    data class Saved(val count: Int) : TagSaveResult
    data object NoChanges : TagSaveResult
    data object PermissionDenied : TagSaveResult
    data class Failed(val failed: Int) : TagSaveResult
}

data class TagEditorState(
    val loading: Boolean = true,
    val songCount: Int = 0,
    val unreadable: Int = 0,
    val nothingToEdit: Boolean = false,
    val values: Map<TagField, String> = emptyMap(),
    /** Fields whose value differs between the selected songs. */
    val mixed: Set<TagField> = emptySet(),
    val dirty: Boolean = false,
    val saving: Boolean = false,
    val grantRequest: android.content.IntentSender? = null,
    val result: TagSaveResult? = null
) {
    val multi: Boolean get() = songCount > 1
}

@HiltViewModel
class TagEditorViewModel @Inject constructor(
    private val repository: TagRepository,
    session: TagEditSession
) : ViewModel() {

    private val requestedIds = session.songIds
    private var writableIds: List<Long> = emptyList()
    private val edited = linkedSetOf<TagField>()
    private var pendingChanges: TagValues = emptyMap()

    private val _state = MutableStateFlow(TagEditorState(songCount = requestedIds.size))
    val state: StateFlow<TagEditorState> = _state.asStateFlow()

    init {
        if (requestedIds.isEmpty()) {
            _state.update { it.copy(loading = false, nothingToEdit = true) }
        } else {
            viewModelScope.launch { load() }
        }
    }

    private suspend fun load() {
        val tags = repository.read(requestedIds)
        writableIds = tags.keys.toList()
        val values = HashMap<TagField, String>()
        val mixed = mutableSetOf<TagField>()
        for (field in TagField.entries) {
            val distinct = tags.values.map { it[field].orEmpty() }.distinct()
            if (distinct.size > 1) mixed += field
            values[field] = distinct.singleOrNull().orEmpty()
        }
        _state.update {
            it.copy(
                loading = false,
                songCount = writableIds.size,
                unreadable = requestedIds.size - writableIds.size,
                nothingToEdit = writableIds.isEmpty(),
                values = values,
                mixed = mixed
            )
        }
    }

    fun onChange(field: TagField, text: String) {
        edited += field
        _state.update { it.copy(values = it.values + (field to text), dirty = true) }
    }

    fun save() {
        if (_state.value.saving) return
        val changes = edited.associateWith { _state.value.values[it].orEmpty() }
        if (changes.isEmpty()) {
            _state.update { it.copy(result = TagSaveResult.NoChanges) }
            return
        }
        pendingChanges = changes
        if (repository.hasWriteAccess(writableIds)) {
            performWrite()
        } else {
            val sender = repository.createWriteRequest(writableIds)
            if (sender == null) performWrite() else _state.update { it.copy(grantRequest = sender) }
        }
    }

    fun consumeGrantRequest() {
        _state.update { it.copy(grantRequest = null) }
    }

    fun onGrantResult(granted: Boolean) {
        if (granted) performWrite() else _state.update { it.copy(result = TagSaveResult.PermissionDenied) }
    }

    fun consumeResult() {
        _state.update { it.copy(result = null) }
    }

    private fun performWrite() {
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val outcome = repository.write(writableIds, pendingChanges)
            val result = when {
                outcome.permissionDenied -> TagSaveResult.PermissionDenied
                outcome.failed > 0 -> TagSaveResult.Failed(outcome.failed)
                else -> TagSaveResult.Saved(outcome.saved)
            }
            _state.update { it.copy(saving = false, result = result) }
        }
    }
}
