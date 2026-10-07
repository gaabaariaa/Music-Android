package com.gaabaariaa.music.feature.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gaabaariaa.music.domain.model.DownloadItem
import com.gaabaariaa.music.domain.model.DownloadableTrack
import com.gaabaariaa.music.domain.model.Song
import com.gaabaariaa.music.domain.model.SourceError
import com.gaabaariaa.music.domain.model.SourceInfo
import com.gaabaariaa.music.domain.repository.DownloadRepository
import com.gaabaariaa.music.domain.repository.LibraryRepository
import com.gaabaariaa.music.domain.repository.MusicSourceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val repository: DownloadRepository,
    private val library: LibraryRepository
) : ViewModel() {

    val items: StateFlow<List<DownloadItem>> = repository.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun pause(id: Long) { viewModelScope.launch { repository.pause(id) } }
    fun resume(id: Long) { viewModelScope.launch { repository.resume(id) } }
    fun cancel(id: Long) { viewModelScope.launch { repository.cancel(id) } }
    fun retry(id: Long) { viewModelScope.launch { repository.retry(id) } }
    fun clearFinished() { viewModelScope.launch { repository.clearFinished() } }

    /** The song may not be in the library database yet right after the download. */
    fun loadSong(songId: Long, onLoaded: (Song?) -> Unit) {
        viewModelScope.launch { onLoaded(library.getSong(songId)) }
    }
}

data class SourceSearchState(
    val source: String = "all",
    val query: String = "",
    val searching: Boolean = false,
    val searched: Boolean = false,
    val tracks: List<DownloadableTrack> = emptyList(),
    val errors: Map<String, SourceError> = emptyMap(),
    val added: Set<String> = emptySet()
)

@HiltViewModel
class SourceSearchViewModel @Inject constructor(
    private val sourcesRepository: MusicSourceRepository,
    private val downloads: DownloadRepository
) : ViewModel() {

    val sources: List<SourceInfo> = sourcesRepository.sources

    private val _state = MutableStateFlow(SourceSearchState())
    val state: StateFlow<SourceSearchState> = _state.asStateFlow()

    fun selectSource(id: String) {
        _state.update { it.copy(source = id, tracks = emptyList(), errors = emptyMap(), searched = false) }
    }

    fun setQuery(value: String) = _state.update { it.copy(query = value) }

    fun search() {
        val current = _state.value
        if (current.searching || current.query.isBlank()) return
        _state.update { it.copy(searching = true, errors = emptyMap(), tracks = emptyList()) }
        viewModelScope.launch {
            val outcome = sourcesRepository.search(current.source, current.query)
            _state.update {
                it.copy(searching = false, searched = true, tracks = outcome.tracks, errors = outcome.errors)
            }
        }
    }

    fun download(track: DownloadableTrack) {
        val key = track.providerId + "|" + track.remoteId
        viewModelScope.launch {
            downloads.enqueue(track)
            _state.update { it.copy(added = it.added + key) }
        }
    }
}
