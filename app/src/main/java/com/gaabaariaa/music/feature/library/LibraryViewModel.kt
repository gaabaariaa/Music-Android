package com.gaabaariaa.music.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gaabaariaa.music.domain.model.AlbumSummary
import com.gaabaariaa.music.domain.model.ArtistSummary
import com.gaabaariaa.music.domain.model.Song
import com.gaabaariaa.music.domain.repository.LibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ScanState(
    val scanning: Boolean = false,
    val lastCount: Int? = null,
    val failed: Boolean = false
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: LibraryRepository
) : ViewModel() {

    val songs: StateFlow<List<Song>> = repository.observeSongs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val artists: StateFlow<List<ArtistSummary>> = repository.observeArtists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val albums: StateFlow<List<AlbumSummary>> = repository.observeAlbums()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _scanState = MutableStateFlow(ScanState())
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    /** Scans once per process unless [force] is set (e.g. the user taps rescan). */
    fun scan(force: Boolean = false) {
        val current = _scanState.value
        if (current.scanning) return
        if (!force && current.lastCount != null) return
        viewModelScope.launch {
            _scanState.update { it.copy(scanning = true, failed = false) }
            try {
                val count = repository.scan()
                _scanState.value = ScanState(lastCount = count)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _scanState.value = ScanState(failed = true)
            }
        }
    }
}
