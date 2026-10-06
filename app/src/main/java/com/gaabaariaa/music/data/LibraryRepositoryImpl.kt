package com.gaabaariaa.music.data

import android.content.ContentResolver
import android.os.Build
import android.provider.MediaStore
import com.gaabaariaa.music.core.di.IoDispatcher
import com.gaabaariaa.music.data.local.SongDao
import com.gaabaariaa.music.data.local.SongEntity
import com.gaabaariaa.music.domain.model.AlbumSummary
import com.gaabaariaa.music.domain.model.ArtistSummary
import com.gaabaariaa.music.domain.model.Song
import com.gaabaariaa.music.domain.repository.LibraryRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Singleton
class LibraryRepositoryImpl @Inject constructor(
    private val resolver: ContentResolver,
    private val dao: SongDao,
    @IoDispatcher private val io: CoroutineDispatcher
) : LibraryRepository {

    override fun observeSongs(): Flow<List<Song>> =
        dao.observeSongs().map { list -> list.map { it.toDomain() } }

    override fun observeArtists(): Flow<List<ArtistSummary>> =
        dao.observeArtists().map { list -> list.map { ArtistSummary(it.name, it.songCount, it.albumCount) } }

    override fun observeAlbums(): Flow<List<AlbumSummary>> =
        dao.observeAlbums().map { list -> list.map { AlbumSummary(it.name, it.artist, it.songCount) } }

    override suspend fun scan(): Int = withContext(io) {
        val found = querySongs()
        val existingIds = dao.allIds()
        dao.upsertAll(found)
        // Chunked: SQLite limits the number of bound variables per statement.
        val foundIds = found.mapTo(HashSet()) { it.mediaStoreId }
        existingIds.filterNot { it in foundIds }.chunked(500).forEach { dao.deleteByIds(it) }
        found.size
    }

    @Suppress("DEPRECATION")
    private fun querySongs(): List<SongEntity> {
        val audio = MediaStore.Audio.Media
        val projection = mutableListOf(
            audio._ID, audio.TITLE, audio.ARTIST, audio.ALBUM, audio.YEAR, audio.TRACK,
            audio.DURATION, audio.SIZE, audio.MIME_TYPE, audio.DATA
        )
        // These columns only exist on API 30+; requesting them earlier throws.
        val hasExtraColumns = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        if (hasExtraColumns) {
            projection += audio.ALBUM_ARTIST
            projection += audio.GENRE
        }

        val songs = ArrayList<SongEntity>()
        resolver.query(
            audio.EXTERNAL_CONTENT_URI,
            projection.toTypedArray(),
            audio.IS_MUSIC + " != 0",
            null,
            audio.TITLE + " COLLATE NOCASE ASC"
        )?.use { c ->
            val iId = c.getColumnIndexOrThrow(audio._ID)
            val iTitle = c.getColumnIndexOrThrow(audio.TITLE)
            val iArtist = c.getColumnIndexOrThrow(audio.ARTIST)
            val iAlbum = c.getColumnIndexOrThrow(audio.ALBUM)
            val iYear = c.getColumnIndexOrThrow(audio.YEAR)
            val iTrack = c.getColumnIndexOrThrow(audio.TRACK)
            val iDuration = c.getColumnIndexOrThrow(audio.DURATION)
            val iSize = c.getColumnIndexOrThrow(audio.SIZE)
            val iMime = c.getColumnIndexOrThrow(audio.MIME_TYPE)
            val iData = c.getColumnIndexOrThrow(audio.DATA)
            val iAlbumArtist = if (hasExtraColumns) c.getColumnIndex(audio.ALBUM_ARTIST) else -1
            val iGenre = if (hasExtraColumns) c.getColumnIndex(audio.GENRE) else -1

            while (c.moveToNext()) {
                songs += SongEntity(
                    mediaStoreId = c.getLong(iId),
                    title = c.getString(iTitle).clean(),
                    artist = c.getString(iArtist).clean(),
                    album = c.getString(iAlbum).clean(),
                    albumArtist = if (iAlbumArtist >= 0) c.getString(iAlbumArtist).clean() else "",
                    genre = if (iGenre >= 0) c.getString(iGenre).clean() else "",
                    year = if (c.isNull(iYear)) 0 else c.getInt(iYear),
                    track = if (c.isNull(iTrack)) 0 else c.getInt(iTrack),
                    durationMs = c.getLong(iDuration),
                    sizeBytes = c.getLong(iSize),
                    mimeType = c.getString(iMime).orEmpty(),
                    path = c.getString(iData).orEmpty()
                )
            }
        }
        return songs
    }

    private fun String?.clean(): String =
        this?.trim()?.takeUnless { it == "<unknown>" }.orEmpty()
}

internal fun SongEntity.toDomain() = Song(
    id = mediaStoreId,
    title = title,
    artist = artist,
    album = album,
    albumArtist = albumArtist,
    genre = genre,
    year = year,
    track = track,
    durationMs = durationMs,
    sizeBytes = sizeBytes,
    mimeType = mimeType,
    path = path
)
