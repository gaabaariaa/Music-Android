package com.gaabaariaa.music.player
import android.content.*
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.gaabaariaa.music.data.SongEntity
class MusicPlayerManager(context:Context){
 val player=ExoPlayer.Builder(context).build()
 fun play(song:SongEntity){val uri=ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,song.mediaStoreId);player.setMediaItem(MediaItem.fromUri(uri));player.prepare();player.play()}
 fun release(){player.release()}
}