package com.gaabaariaa.music.data

import android.content.ContentResolver
import android.os.Build
import android.provider.MediaStore
import com.gaabaariaa.music.core.di.IoDispatcher
import com.gaabaariaa.music.data.local.SongDao
import com.gaabaariaa.music.data.local.SongEntity
import com.gaabaariaa.music.domain.model.AlbumSummary
import com.gaabaariaa.music.domain.model.ArtistSummary
import com.gaabaariaa.music.domain.model.FolderSummary
import com.gaabaariaa.music.domain.model.GenreSummary
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

    override fun observeGenres(): Flow<List<GenreSummary>> =
        dao.observeGenres().map { list -> list.map { GenreSummary(it.name, it.songCount) } }

    override fun observeFolders(): Flow<List<FolderSummary>> =
        dao.observeFolders().map { list -> list.map { FolderSummary(it.path, it.songCount) } }

    override fun observeSongsByArtist(artist: String): Flow<List<Song>> =
        dao.observeSongsByArtist(artist).map { list -> list.map { it.toDomain() } }

    override fun observeSongsByAlbum(album: String): Flow<List<Song>> =
        dao.observeSongsByAlbum(album).map { list -> list.map { it.toDomain() } }

    override fun observeSongsByGenre(genre: String): Flow<List<Song>> =
        dao.observeSongsByGenre(genre).map { list -> list.map { it.toDomain() } }

    override fun observeSongsByFolder(folder: String): Flow<List<Song>> =
        dao.observeSongsByFolder(folder).map { list -> list.map { it.toDomain() } }

    override fun observeAlbumsByArtist(artist: String): Flow<List<AlbumSummary>> =
        dao.observeAlbumsByArtist(artist).map { list -> list.map { AlbumSummary(it.name, it.artist, it.songCount) } }

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
        val projection = mutableListOf(
            MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.YEAR, MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.SIZE, MediaStore.Audio.Media.MIME_TYPE, MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DATE_ADDED
        )
        // These columns only exist on API 30+; requesting them earlier throws.
        val hasExtraColumns = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        if (hasExtraColumns) {
            projection += MediaStore.Audio.Media.ALBUM_ARTIST
            projection += MediaStore.Audio.Media.GENRE
        }

        val songs = ArrayList<SongEntity>()
        resolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection.toTypedArray(),
            MediaStore.Audio.Media.IS_MUSIC + " != 0",
            null,
            MediaStore.Audio.Media.TITLE + " COLLATE NOCASE ASC"
        )?.use { c ->
            val iId = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val iTitle = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val iArtist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val iAlbum = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val iYear = c.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
            val iTrack = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            val iDuration = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val iSize = c.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val iMime = c.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
            val iData = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val iDateAdded = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val iAlbumArtist = if (hasExtraColumns) c.getColumnIndex(MediaStore.Audio.Media.ALBUM_ARTIST) else -1
            val iGenre = if (hasExtraColumns) c.getColumnIndex(MediaStore.Audio.Media.GENRE) else -1

            while (c.moveToNext()) {
                val path = c.getString(iData).orEmpty()
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
                    path = path,
                    dateAdded = c.getLong(iDateAdded),
                    folder = path.substringBeforeLast('/', "")
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
    path = path,
    dateAdded = dateAdded,
    folder = folder
)
