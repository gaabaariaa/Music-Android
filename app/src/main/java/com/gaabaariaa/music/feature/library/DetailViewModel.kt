package com.gaabaariaa.music.feature.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gaabaariaa.music.domain.model.AlbumSummary
import com.gaabaariaa.music.domain.model.HealthIssue
import com.gaabaariaa.music.domain.model.Playlist
import com.gaabaariaa.music.domain.model.Song
import com.gaabaariaa.music.domain.repository.HealthRepository
import com.gaabaariaa.music.domain.repository.LibraryRepository
import com.gaabaariaa.music.domain.repository.PlaylistRepository
import com.gaabaariaa.music.feature.tags.TagEditSession
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class DetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repository: LibraryRepository,
    health: HealthRepository,
    private val playlists: PlaylistRepository,
    private val tagSession: TagEditSession
) : ViewModel() {

    val type: DetailType = DetailType.valueOf(savedStateHandle.get<String>("type").orEmpty())
    val value: String = decodeDetailValue(savedStateHandle.get<String>("value").orEmpty())
    private val playlistId: Long = if (type == DetailType.PLAYLIST) value.toLongOrNull() ?: -1L else -1L

    val songs: StateFlow<List<Song>> = when (type) {
        DetailType.ARTIST -> repository.observeSongsByArtist(value)
        DetailType.ALBUM -> repository.observeSongsByAlbum(value)
        DetailType.GENRE -> repository.observeSongsByGenre(value)
        DetailType.FOLDER -> repository.observeSongsByFolder(value)
        DetailType.ISSUE -> health.observeSongs(HealthIssue.valueOf(value))
        DetailType.PLAYLIST -> playlists.observeSongs(playlistId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val albums: StateFlow<List<AlbumSummary>> = when (type) {
        DetailType.ARTIST -> repository.observeAlbumsByArtist(value)
        else -> flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val playlist: StateFlow<Playlist?> = if (type == DetailType.PLAYLIST) {
        playlists.observePlaylist(playlistId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    } else {
        MutableStateFlow(null)
    }

    fun startTagEdit(ids: List<Long>) { tagSession.songIds = ids }

    fun rename(name: String) {
        if (name.isNotBlank()) viewModelScope.launch { playlists.rename(playlistId, name) }
    }

    fun deletePlaylist(onDone: () -> Unit) {
        viewModelScope.launch {
            playlists.delete(playlistId)
            onDone()
        }
    }

    fun removeFromPlaylist(songId: Long) {
        viewModelScope.launch { playlists.removeSong(playlistId, songId) }
    }

    fun moveInPlaylist(index: Int, delta: Int) {
        val ids = songs.value.map { it.id }.toMutableList()
        val target = index + delta
        if (index !in ids.indices || target !in ids.indices) return
        ids.add(target, ids.removeAt(index))
        viewModelScope.launch { playlists.reorder(playlistId, ids) }
    }
}
