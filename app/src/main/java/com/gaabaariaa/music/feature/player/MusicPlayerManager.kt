package com.gaabaariaa.music.feature.player

import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.gaabaariaa.music.domain.model.Song
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

/** Thin wrapper around the Media3 [MediaController] that talks to [MusicPlaybackService]. */
@Singleton
class MusicPlayerManager @Inject constructor(@ApplicationContext context: Context) {
    private val _controller = MutableStateFlow<MediaController?>(null)
    val controller: StateFlow<MediaController?> = _controller.asStateFlow()

    // Lives as long as the process, so the sleep timer still fires when the UI is gone.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var sleepJob: Job? = null
    private val _sleepRemainingMs = MutableStateFlow(0L)
    val sleepRemainingMs: StateFlow<Long> = _sleepRemainingMs.asStateFlow()

    init {
        val future = MediaController.Builder(
            context,
            SessionToken(context, ComponentName(context, MusicPlaybackService::class.java))
        ).buildAsync()
        future.addListener(
            { runCatching { _controller.value = future.get() } },
            MoreExecutors.directExecutor()
        )
    }

    suspend fun setQueue(songs: List<Song>, startIndex: Int) {
        if (songs.isEmpty()) return
        val c = controller.filterNotNull().first()
        c.setMediaItems(songs.map { it.toMediaItem() }, startIndex.coerceIn(0, songs.lastIndex), 0L)
        c.prepare()
        c.play()
    }

    suspend fun addToQueue(song: Song) {
        val c = controller.filterNotNull().first()
        if (c.mediaItemCount == 0) {
            setQueue(listOf(song), 0)
        } else {
            c.addMediaItem(song.toMediaItem())
        }
    }

    suspend fun playNext(song: Song) {
        val c = controller.filterNotNull().first()
        if (c.mediaItemCount == 0) {
            setQueue(listOf(song), 0)
        } else {
            c.addMediaItem(c.currentMediaItemIndex + 1, song.toMediaItem())
        }
    }

    fun startSleepTimer(minutes: Int) {
        sleepJob?.cancel()
        sleepJob = scope.launch {
            var left = minutes * 60_000L
            while (left > 0) {
                _sleepRemainingMs.value = left
                delay(1_000)
                left -= 1_000
            }
            controller.value?.pause()
            _sleepRemainingMs.value = 0L
        }
    }

    fun cancelSleepTimer() {
        sleepJob?.cancel()
        sleepJob = null
        _sleepRemainingMs.value = 0L
    }

    fun setSpeed(speed: Float) {
        controller.value?.setPlaybackSpeed(speed)
    }

    fun togglePlayPause() {
        controller.value?.let { if (it.isPlaying) it.pause() else it.play() }
    }

    fun next() {
        controller.value?.let { if (it.hasNextMediaItem()) it.seekToNextMediaItem() }
    }

    fun previous() {
        controller.value?.let {
            if (it.hasPreviousMediaItem()) it.seekToPreviousMediaItem() else it.seekTo(0)
        }
    }

    fun seekTo(positionMs: Long) {
        controller.value?.seekTo(positionMs.coerceAtLeast(0L))
    }

    fun seekToIndex(index: Int) {
        controller.value?.let {
            if (index in 0 until it.mediaItemCount) {
                it.seekToDefaultPosition(index)
                it.play()
            }
        }
    }

    fun toggleShuffle() {
        controller.value?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }
    }

    fun cycleRepeat() {
        controller.value?.let {
            it.repeatMode = when (it.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }
    }

    private fun Song.toMediaItem(): MediaItem = MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id))
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .build()
        )
        .build()
}
