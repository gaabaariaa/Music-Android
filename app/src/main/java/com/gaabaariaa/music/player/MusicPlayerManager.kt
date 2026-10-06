package com.gaabaariaa.music.player

import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.gaabaariaa.music.data.SongEntity
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class MusicPlayerManager(context: Context) {
    private val appContext = context.applicationContext
    private val _controller = MutableStateFlow<MediaController?>(null)
    val controller: StateFlow<MediaController?> = _controller

    private val controllerFuture = MediaController.Builder(
        appContext,
        SessionToken(appContext, ComponentName(appContext, MusicPlaybackService::class.java))
    ).buildAsync()

    init {
        controllerFuture.addListener({
            runCatching { _controller.value = controllerFuture.get() }
        }, MoreExecutors.directExecutor())
    }

    fun setQueue(songs: List<SongEntity>, startIndex: Int = 0) {
        val controller = _controller.value ?: return
        if (songs.isEmpty()) return
        val items = songs.map { it.toMediaItem() }
        controller.setMediaItems(
            items,
            startIndex.coerceIn(0, items.lastIndex),
            0L
        )
        controller.prepare()
        controller.play()
    }

    fun togglePlayPause() {
        _controller.value?.let { if (it.isPlaying) it.pause() else it.play() }
    }

    fun next() {
        _controller.value?.let {
            if (it.hasNextMediaItem()) it.seekToNextMediaItem()
        }
    }

    fun previous() {
        _controller.value?.let {
            if (it.hasPreviousMediaItem()) it.seekToPreviousMediaItem()
            else it.seekTo(0)
        }
    }

    fun seekTo(positionMs: Long) {
        _controller.value?.seekTo(positionMs.coerceAtLeast(0L))
    }

    fun release() {
        _controller.value?.let { MediaController.releaseFuture(controllerFuture) }
        _controller.value = null
    }

    private fun SongEntity.toMediaItem(): MediaItem {
        val uri = ContentUris.withAppendedId(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            mediaStoreId
        )
        return MediaItem.Builder()
            .setMediaId(mediaStoreId.toString())
            .setUri(uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setAlbumTitle(album)
                    .build()
            )
            .build()
    }
}
