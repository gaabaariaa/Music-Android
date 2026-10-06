package com.gaabaariaa.music.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gaabaariaa.music.domain.model.AlbumSummary
import com.gaabaariaa.music.domain.model.ArtistSummary
import com.gaabaariaa.music.domain.model.FolderSummary
import com.gaabaariaa.music.domain.model.GenreSummary
import com.gaabaariaa.music.domain.model.ScanState
import com.gaabaariaa.music.domain.model.Song
import com.gaabaariaa.music.domain.model.SongSort
import com.gaabaariaa.music.domain.repository.LibraryRepository
import com.gaabaariaa.music.domain.repository.LibraryScanner
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class LibraryViewModel @Inject constructor(
    repository: LibraryRepository,
    private val scanner: LibraryScanner
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _sort = MutableStateFlow(SongSort.TITLE)
    val sort: StateFlow<SongSort> = _sort.asStateFlow()

    /** Filtered and sorted off the main thread so typing stays smooth on large libraries. */
    val songs: StateFlow<List<Song>> =
        combine(repository.observeSongs(), _query, _sort) { all, query, sort ->
            filterAndSort(all, query, sort)
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val artists: StateFlow<List<ArtistSummary>> = repository.observeArtists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val albums: StateFlow<List<AlbumSummary>> = repository.observeAlbums()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val genres: StateFlow<List<GenreSummary>> = repository.observeGenres()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val folders: StateFlow<List<FolderSummary>> = repository.observeFolders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val scanState: StateFlow<ScanState> = scanner.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScanState())

    fun setQuery(value: String) { _query.value = value }
    fun setSort(value: SongSort) { _sort.value = value }

    /** Runs in the background (WorkManager); [force] restarts a scan that is already queued. */
    fun scan(force: Boolean = false) = scanner.scanNow(force)
}

internal fun filterAndSort(all: List<Song>, query: String, sort: SongSort): List<Song> {
    val q = query.trim()
    val filtered = if (q.isEmpty()) all else all.filter {
        it.title.contains(q, ignoreCase = true) ||
            it.artist.contains(q, ignoreCase = true) ||
            it.album.contains(q, ignoreCase = true) ||
            it.genre.contains(q, ignoreCase = true)
    }
    val text = String.CASE_INSENSITIVE_ORDER
    return when (sort) {
        SongSort.TITLE -> filtered.sortedWith(compareBy(text) { it.title })
        SongSort.ARTIST -> filtered.sortedWith(compareBy<Song, String>(text) { it.artist }.thenBy(text) { it.title })
        SongSort.ALBUM -> filtered.sortedWith(compareBy<Song, String>(text) { it.album }.thenBy { it.track })
        SongSort.DATE_ADDED -> filtered.sortedByDescending { it.dateAdded }
        SongSort.DURATION -> filtered.sortedByDescending { it.durationMs }
    }
}
