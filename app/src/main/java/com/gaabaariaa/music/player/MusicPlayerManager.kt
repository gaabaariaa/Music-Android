package com.gaabaariaa.music.player

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.gaabaariaa.music.data.SongEntity

class MusicPlayerManager(context: Context) {
    val player: ExoPlayer = ExoPlayer.Builder(context).build()

    fun setQueue(songs: List<SongEntity>, startIndex: Int = 0) {
        val items = songs.map { it.toMediaItem() }
        player.setMediaItems(items, startIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0)), 0L)
        player.prepare()
        player.play()
    }

    fun play(song: SongEntity) {
        setQueue(listOf(song))
    }

    fun togglePlayPause() {
        if (player.isPlaying) player.pause() else player.play()
    }

    fun next() {
        if (player.hasNextMediaItem()) player.seekToNextMediaItem()
    }

    fun previous() {
        if (player.hasPreviousMediaItem()) player.seekToPreviousMediaItem()
        else player.seekTo(0)
    }

    fun release() = player.release()

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