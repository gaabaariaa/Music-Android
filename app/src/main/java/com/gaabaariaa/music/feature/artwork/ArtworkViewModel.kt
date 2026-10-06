package com.gaabaariaa.music.feature.artwork

import android.content.IntentSender
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gaabaariaa.music.domain.model.ArtworkCandidate
import com.gaabaariaa.music.domain.model.ArtworkError
import com.gaabaariaa.music.domain.model.TagField
import com.gaabaariaa.music.domain.model.TagWriteResult
import com.gaabaariaa.music.domain.repository.ArtworkRepository
import com.gaabaariaa.music.domain.repository.TagRepository
import com.gaabaariaa.music.feature.player.ArtworkRefresh
import com.gaabaariaa.music.feature.tags.TagEditSession
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class ArtworkResult(val candidate: ArtworkCandidate, val thumbnail: ByteArray)

sealed interface ArtworkMessage {
    data class Embedded(val count: Int) : ArtworkMessage
    data class Removed(val count: Int) : ArtworkMessage
    data class Failed(val count: Int) : ArtworkMessage
    data object SavedToPictures : ArtworkMessage
    data object SaveFailed : ArtworkMessage
    data object DownloadFailed : ArtworkMessage
    data object PermissionDenied : ArtworkMessage
}

private sealed interface PendingWrite {
    class Embed(val image: ByteArray) : PendingWrite
    data object Remove : PendingWrite
}

data class ArtworkState(
    val songCount: Int = 0,
    val previewSongId: Long? = null,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val searching: Boolean = false,
    val searched: Boolean = false,
    val error: ArtworkError? = null,
    val results: List<ArtworkResult> = emptyList(),
    val selectedId: String? = null,
    val working: Boolean = false,
    val canSaveToPictures: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q,
    val grantRequest: IntentSender? = null,
    val message: ArtworkMessage? = null
)

@HiltViewModel
class ArtworkViewModel @Inject constructor(
    private val artwork: ArtworkRepository,
    private val tags: TagRepository,
    session: TagEditSession
) : ViewModel() {

    private val ids = session.songIds
    private var pending: PendingWrite? = null

    private val _state = MutableStateFlow(ArtworkState(songCount = ids.size, previewSongId = ids.firstOrNull()))
    val state: StateFlow<ArtworkState> = _state.asStateFlow()

    init {
        ids.firstOrNull()?.let { first ->
            viewModelScope.launch {
                val values = tags.read(listOf(first))[first]
                if (values != null) {
                    _state.update {
                        it.copy(
                            title = values[TagField.TITLE].orEmpty(),
                            artist = values[TagField.ARTIST].orEmpty(),
                            album = values[TagField.ALBUM].orEmpty()
                        )
                    }
                }
            }
        }
    }

    fun onTitle(value: String) = _state.update { it.copy(title = value) }
    fun onArtist(value: String) = _state.update { it.copy(artist = value) }
    fun onAlbum(value: String) = _state.update { it.copy(album = value) }
    fun select(releaseId: String) = _state.update { it.copy(selectedId = releaseId) }
    fun consumeMessage() = _state.update { it.copy(message = null) }
    fun consumeGrantRequest() = _state.update { it.copy(grantRequest = null) }

    fun search() {
        val s = _state.value
        if (s.searching) return
        _state.update { it.copy(searching = true, searched = false, error = null, results = emptyList(), selectedId = null) }
        viewModelScope.launch {
            val found = artwork.search(s.title, s.artist, s.album)
            if (found.error != null) {
                _state.update { it.copy(searching = false, searched = true, error = found.error) }
                return@launch
            }
            // Only releases that actually have a front cover are shown; thumbnails arrive one by one.
            val gate = Semaphore(4)
            found.candidates.map { candidate ->
                async {
                    gate.withPermit {
                        artwork.fetchImage(candidate.releaseId, 250)?.let { bytes ->
                            _state.update { it.copy(results = it.results + ArtworkResult(candidate, bytes)) }
                        }
                    }
                }
            }.awaitAll()
            _state.update { it.copy(searching = false, searched = true) }
        }
    }

    fun embedSelected() {
        val selected = _state.value.selectedId ?: return
        if (_state.value.working) return
        _state.update { it.copy(working = true) }
        viewModelScope.launch {
            val image = artwork.fetchImage(selected, 500)
            if (image == null) {
                _state.update { it.copy(working = false, message = ArtworkMessage.DownloadFailed) }
            } else {
                requestWrite(PendingWrite.Embed(image))
            }
        }
    }

    fun removeEmbedded() {
        if (_state.value.working) return
        _state.update { it.copy(working = true) }
        requestWrite(PendingWrite.Remove)
    }

    fun saveSelectedToPictures() {
        val s = _state.value
        val selected = s.selectedId ?: return
        if (s.working) return
        _state.update { it.copy(working = true) }
        viewModelScope.launch {
            val image = artwork.fetchImage(selected, 500)
            val name = listOf(s.artist, s.album.ifBlank { s.title }).filter { it.isNotBlank() }.joinToString(" - ")
            val ok = image != null && artwork.saveToPictures(image, name)
            _state.update {
                it.copy(
                    working = false,
                    message = if (ok) ArtworkMessage.SavedToPictures
                    else if (image == null) ArtworkMessage.DownloadFailed else ArtworkMessage.SaveFailed
                )
            }
        }
    }

    fun onGrantResult(granted: Boolean) {
        val action = pending
        pending = null
        if (granted && action != null) {
            perform(action)
        } else {
            _state.update { it.copy(working = false, message = ArtworkMessage.PermissionDenied) }
        }
    }

    private fun requestWrite(action: PendingWrite) {
        pending = action
        if (tags.hasWriteAccess(ids)) {
            pending = null
            perform(action)
            return
        }
        val sender = tags.createWriteRequest(ids)
        if (sender == null) {
            pending = null
            perform(action)
        } else {
            _state.update { it.copy(grantRequest = sender) }
        }
    }

    private fun perform(action: PendingWrite) {
        viewModelScope.launch {
            val result: TagWriteResult = when (action) {
                is PendingWrite.Embed -> artwork.embed(ids, action.image)
                PendingWrite.Remove -> artwork.removeEmbedded(ids)
            }
            if (result.saved > 0) ArtworkRefresh.bump()
            val message = when {
                result.permissionDenied -> ArtworkMessage.PermissionDenied
                result.failed > 0 -> ArtworkMessage.Failed(result.failed)
                action is PendingWrite.Embed -> ArtworkMessage.Embedded(result.saved)
                else -> ArtworkMessage.Removed(result.saved)
            }
            _state.update { it.copy(working = false, message = message) }
        }
    }
}
