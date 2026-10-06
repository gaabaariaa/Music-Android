package com.gaabaariaa.music.feature.lyrics

import android.content.IntentSender
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gaabaariaa.music.domain.model.Lyrics
import com.gaabaariaa.music.domain.model.LyricsSource
import com.gaabaariaa.music.domain.model.OnlineLyricsResult
import com.gaabaariaa.music.domain.model.TagField
import com.gaabaariaa.music.domain.repository.LibraryRepository
import com.gaabaariaa.music.domain.repository.LyricsRepository
import com.gaabaariaa.music.domain.repository.TagRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface OnlineState {
    data object Idle : OnlineState
    data object Searching : OnlineState
    data class Found(val lyrics: Lyrics) : OnlineState
    data object NotFound : OnlineState
    data object Offline : OnlineState
    data object Error : OnlineState
}

sealed interface LyricsMessage {
    data object Saved : LyricsMessage
    data object Deleted : LyricsMessage
    data object Imported : LyricsMessage
    data object ImportFailed : LyricsMessage
    data object WrittenToFile : LyricsMessage
    data class WriteFailed(val count: Int) : LyricsMessage
    data object PermissionDenied : LyricsMessage
}

data class LyricsUiState(
    val loading: Boolean = true,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val durationMs: Long = 0L,
    val text: String = "",
    val source: String? = null,
    val dirty: Boolean = false,
    val online: OnlineState = OnlineState.Idle,
    val busy: Boolean = false,
    val grantRequest: IntentSender? = null,
    val message: LyricsMessage? = null
)

@HiltViewModel
class LyricsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val lyrics: LyricsRepository,
    private val library: LibraryRepository,
    private val tags: TagRepository
) : ViewModel() {

    private val songId: Long = savedStateHandle.get<Long>("songId") ?: -1L
    private var saveSource = LyricsSource.LOCAL

    private val _state = MutableStateFlow(LyricsUiState())
    val state: StateFlow<LyricsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val song = library.getSong(songId)
            val existing = lyrics.get(songId)
            _state.update {
                it.copy(
                    loading = false,
                    title = song?.title.orEmpty(),
                    artist = song?.artist.orEmpty(),
                    album = song?.album.orEmpty(),
                    durationMs = song?.durationMs ?: 0L,
                    text = existing?.content.orEmpty(),
                    source = existing?.source
                )
            }
        }
    }

    fun onText(value: String) {
        saveSource = LyricsSource.LOCAL
        _state.update { it.copy(text = value, dirty = true) }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }
    fun consumeGrantRequest() = _state.update { it.copy(grantRequest = null) }

    fun save() {
        val text = _state.value.text
        viewModelScope.launch {
            lyrics.save(songId, text, saveSource)
            _state.update {
                it.copy(dirty = false, source = if (text.isBlank()) null else saveSource, message = LyricsMessage.Saved)
            }
        }
    }

    fun deleteSaved() {
        viewModelScope.launch {
            lyrics.delete(songId)
            _state.update { it.copy(text = "", source = null, dirty = false, message = LyricsMessage.Deleted) }
        }
    }

    fun searchOnline() {
        val s = _state.value
        if (s.online is OnlineState.Searching) return
        _state.update { it.copy(online = OnlineState.Searching) }
        viewModelScope.launch {
            val result = lyrics.fetchOnline(s.title, s.artist, s.album, s.durationMs)
            _state.update {
                it.copy(
                    online = when (result) {
                        is OnlineLyricsResult.Found -> OnlineState.Found(result.lyrics)
                        OnlineLyricsResult.NotFound -> OnlineState.NotFound
                        OnlineLyricsResult.Offline -> OnlineState.Offline
                        OnlineLyricsResult.Error -> OnlineState.Error
                    }
                )
            }
        }
    }

    fun applyOnline() {
        val found = (_state.value.online as? OnlineState.Found)?.lyrics ?: return
        saveSource = LyricsSource.ONLINE
        _state.update { it.copy(text = found.content, dirty = true, online = OnlineState.Idle) }
    }

    fun import(uri: String) {
        viewModelScope.launch {
            val text = lyrics.readText(uri)
            if (text == null) {
                _state.update { it.copy(message = LyricsMessage.ImportFailed) }
            } else {
                saveSource = LyricsSource.IMPORTED
                _state.update { it.copy(text = text, dirty = true, message = LyricsMessage.Imported) }
            }
        }
    }

    fun writeToFile() {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        if (tags.hasWriteAccess(listOf(songId))) {
            performWrite()
        } else {
            val sender = tags.createWriteRequest(listOf(songId))
            if (sender == null) performWrite() else _state.update { it.copy(grantRequest = sender) }
        }
    }

    fun onGrantResult(granted: Boolean) {
        if (granted) performWrite() else _state.update { it.copy(busy = false, message = LyricsMessage.PermissionDenied) }
    }

    private fun performWrite() {
        val text = _state.value.text
        viewModelScope.launch {
            val result = tags.write(listOf(songId), mapOf(TagField.LYRICS to text))
            _state.update {
                it.copy(
                    busy = false,
                    message = when {
                        result.permissionDenied -> LyricsMessage.PermissionDenied
                        result.failed > 0 -> LyricsMessage.WriteFailed(result.failed)
                        else -> LyricsMessage.WrittenToFile
                    }
                )
            }
        }
    }
}
