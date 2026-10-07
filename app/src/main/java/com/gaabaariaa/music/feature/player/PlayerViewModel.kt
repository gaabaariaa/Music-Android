package com.gaabaariaa.music.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import com.gaabaariaa.music.domain.model.Song
import com.gaabaariaa.music.domain.repository.PersonalRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class QueueItem(val mediaId: String, val title: String, val artist: String, val album: String)

data class PlayerUiState(
    val hasMedia: Boolean = false,
    val isPlaying: Boolean = false,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val currentIndex: Int = 0,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val mediaId: String = "",
    val speed: Float = 1f,
    val sleepRemainingMs: Long = 0L,
    val queue: List<QueueItem> = emptyList()
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val manager: MusicPlayerManager,
    private val personal: PersonalRepository
) : ViewModel() {

    /** Media id that already counted as a play in the current pass; reset when the song restarts. */
    private var countedMediaId: String? = null

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private var attached: MediaController? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            refresh(player, events.contains(Player.EVENT_TIMELINE_CHANGED))
        }
    }

    init {
        viewModelScope.launch {
            manager.controller.collect { controller ->
                attached?.removeListener(listener)
                attached = controller
                if (controller != null) {
                    controller.addListener(listener)
                    refresh(controller, rebuildQueue = true)
                } else {
                    _state.value = PlayerUiState()
                }
            }
        }
        viewModelScope.launch {
            manager.sleepRemainingMs.collect { left -> _state.update { it.copy(sleepRemainingMs = left) } }
        }
        viewModelScope.launch {
            while (true) {
                val controller = attached
                if (controller != null && controller.isPlaying) {
                    val position = controller.currentPosition.coerceAtLeast(0L)
                    val duration = controller.duration.coerceAtLeast(0L)
                    _state.update { it.copy(positionMs = position, durationMs = duration) }
                    countPlayIfNeeded(controller.currentMediaItem?.mediaId, position, duration)
                }
                delay(500)
            }
        }
    }

    /** A song counts as played after 30 seconds, or half of a shorter song. */
    private fun countPlayIfNeeded(mediaId: String?, position: Long, duration: Long) {
        val id = mediaId?.toLongOrNull() ?: return
        if (countedMediaId == mediaId) {
            if (position < 3_000L) countedMediaId = null // repeated or restarted
            return
        }
        val threshold = if (duration > 0) minOf(30_000L, duration / 2) else 30_000L
        if (position >= threshold) {
            countedMediaId = mediaId
            viewModelScope.launch { personal.recordPlay(id) }
        }
    }

    private fun refresh(player: Player, rebuildQueue: Boolean) {
        val item = player.currentMediaItem
        _state.update { old ->
            old.copy(
                hasMedia = item != null,
                isPlaying = player.isPlaying,
                title = item?.mediaMetadata?.title?.toString().orEmpty(),
                artist = item?.mediaMetadata?.artist?.toString().orEmpty(),
                album = item?.mediaMetadata?.albumTitle?.toString().orEmpty(),
                positionMs = player.currentPosition.coerceAtLeast(0L),
                durationMs = player.duration.coerceAtLeast(0L),
                currentIndex = player.currentMediaItemIndex,
                shuffle = player.shuffleModeEnabled,
                repeatMode = player.repeatMode,
                mediaId = item?.mediaId.orEmpty(),
                speed = player.playbackParameters.speed,
                queue = if (rebuildQueue) buildQueue(player) else old.queue
            )
        }
    }

    private fun buildQueue(player: Player): List<QueueItem> = List(player.mediaItemCount) { i ->
        val media = player.getMediaItemAt(i)
        QueueItem(
            mediaId = media.mediaId,
            title = media.mediaMetadata.title?.toString().orEmpty(),
            artist = media.mediaMetadata.artist?.toString().orEmpty(),
            album = media.mediaMetadata.albumTitle?.toString().orEmpty()
        )
    }

    fun playQueue(songs: List<Song>, startIndex: Int) {
        viewModelScope.launch { manager.setQueue(songs, startIndex) }
    }

    fun addToQueue(song: Song) {
        viewModelScope.launch { manager.addToQueue(song) }
    }

    fun playNext(song: Song) {
        viewModelScope.launch { manager.playNext(song) }
    }

    fun startSleepTimer(minutes: Int) = manager.startSleepTimer(minutes)
    fun cancelSleepTimer() = manager.cancelSleepTimer()
    fun setSpeed(speed: Float) = manager.setSpeed(speed)
    fun togglePlayPause() = manager.togglePlayPause()
    fun next() = manager.next()
    fun previous() = manager.previous()
    fun seekTo(positionMs: Long) = manager.seekTo(positionMs)
    fun playQueueIndex(index: Int) = manager.seekToIndex(index)
    fun toggleShuffle() = manager.toggleShuffle()
    fun cycleRepeat() = manager.cycleRepeat()

    override fun onCleared() {
        attached?.removeListener(listener)
        super.onCleared()
    }
}
