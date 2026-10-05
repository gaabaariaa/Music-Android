package com.gaabaariaa.music.data

import android.content.ContentResolver
import android.provider.MediaStore

class MusicRepository(private val resolver: ContentResolver, private val dao: SongDao) {
    suspend fun scan(): Int {
        val songs = mutableListOf<SongEntity>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ARTIST,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.DATA
        )
        resolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            MediaStore.Audio.Media.IS_MUSIC + " != 0",
            null,
            MediaStore.Audio.Media.TITLE + " COLLATE NOCASE ASC"
        )?.use { cursor ->
            val id = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val title = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artist = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val album = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumArtist = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ARTIST)
            val year = cursor.getColumnIndex(MediaStore.Audio.Media.YEAR)
            val track = cursor.getColumnIndex(MediaStore.Audio.Media.TRACK)
            val duration = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val size = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val mime = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
            val data = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)

            while (cursor.moveToNext()) {
                songs += SongEntity(
                    mediaStoreId = cursor.getLong(id),
                    title = cursor.getString(title).orEmpty().ifBlank { "Unknown title" },
                    artist = cursor.getString(artist).orEmpty().ifBlank { "Unknown artist" },
                    album = cursor.getString(album).orEmpty().ifBlank { "Unknown album" },
                    albumArtist = if (albumArtist >= 0) cursor.getString(albumArtist).orEmpty() else "",
                    genre = "",
                    year = if (year >= 0 && !cursor.isNull(year)) cursor.getInt(year) else 0,
                    track = if (track >= 0 && !cursor.isNull(track)) cursor.getInt(track) else 0,
                    durationMs = cursor.getLong(duration),
                    sizeBytes = cursor.getLong(size),
                    mimeType = cursor.getString(mime).orEmpty(),
                    path = if (data >= 0) cursor.getString(data).orEmpty() else ""
                )
            }
        }
        if (songs.isNotEmpty()) {
            dao.upsertAll(songs)
            dao.deleteMissing(songs.map { it.mediaStoreId })
        }
        return songs.size
    }
}
