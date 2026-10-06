package com.gaabaariaa.music.feature.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gaabaariaa.music.domain.model.AlbumSummary
import com.gaabaariaa.music.domain.model.Song
import com.gaabaariaa.music.domain.repository.LibraryRepository
import com.gaabaariaa.music.feature.tags.TagEditSession
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class DetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repository: LibraryRepository,
    private val tagSession: TagEditSession
) : ViewModel() {

    val type: DetailType = DetailType.valueOf(savedStateHandle.get<String>("type").orEmpty())
    val value: String = decodeDetailValue(savedStateHandle.get<String>("value").orEmpty())

    val songs: StateFlow<List<Song>> = when (type) {
        DetailType.ARTIST -> repository.observeSongsByArtist(value)
        DetailType.ALBUM -> repository.observeSongsByAlbum(value)
        DetailType.GENRE -> repository.observeSongsByGenre(value)
        DetailType.FOLDER -> repository.observeSongsByFolder(value)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val albums: StateFlow<List<AlbumSummary>> = when (type) {
        DetailType.ARTIST -> repository.observeAlbumsByArtist(value)
        else -> flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun startTagEdit(ids: List<Long>) { tagSession.songIds = ids }
}
