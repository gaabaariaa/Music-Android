package com.gaabaariaa.music.data.artwork

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import com.gaabaariaa.music.core.di.IoDispatcher
import com.gaabaariaa.music.data.files.SafeFileWriter
import com.gaabaariaa.music.data.files.WriteOutcome
import com.gaabaariaa.music.data.local.SongDao
import com.gaabaariaa.music.domain.model.ArtworkError
import com.gaabaariaa.music.domain.model.ArtworkSearchResult
import com.gaabaariaa.music.domain.model.TagWriteResult
import com.gaabaariaa.music.domain.repository.ArtworkRepository
import com.kyant.taglib.Picture
import com.kyant.taglib.TagLib
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

private const val USER_AGENT = "Musiq/0.8.0 ( https://github.com/gaabaariaa/Music-Android )"
private const val MAX_IMAGE_BYTES = 8 * 1024 * 1024

@Singleton
class ArtworkRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val resolver: ContentResolver,
    private val dao: SongDao,
    private val writer: SafeFileWriter,
    @IoDispatcher private val io: CoroutineDispatcher
) : ArtworkRepository {

    private val imageCache = object : LruCache<String, ByteArray>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ByteArray) = value.size
    }

    override suspend fun search(title: String, artist: String, album: String): ArtworkSearchResult =
        withContext(io) {
            val query = buildQuery(title, artist, album)
                ?: return@withContext ArtworkSearchResult(emptyList())
            val url = "https://musicbrainz.org/ws/2/release/?fmt=json&limit=25&query=" +
                URLEncoder.encode(query, "UTF-8")
            try {
                val body = httpGet(url, "application/json")
                    ?: return@withContext ArtworkSearchResult(emptyList(), ArtworkError.SERVER)
                ArtworkSearchResult(parseReleases(String(body, Charsets.UTF_8)))
            } catch (e: IOException) {
                ArtworkSearchResult(emptyList(), ArtworkError.OFFLINE)
            } catch (e: Exception) {
                ArtworkSearchResult(emptyList(), ArtworkError.SERVER)
            }
        }

    override suspend fun fetchImage(releaseId: String, sizePx: Int): ByteArray? = withContext(io) {
        val key = "$releaseId@$sizePx"
        imageCache.get(key)?.let { return@withContext it }
        try {
            val bytes = httpGet("https://coverartarchive.org/release/$releaseId/front-$sizePx", null)
            if (bytes != null && imageMimeType(bytes) != null) {
                imageCache.put(key, bytes)
                bytes
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun embed(songIds: List<Long>, image: ByteArray): TagWriteResult = withContext(io) {
        val mime = imageMimeType(image) ?: return@withContext TagWriteResult(0, songIds.size)
        val picture = Picture(image, "", "Front Cover", mime)
        modifyPictures(songIds) { uri ->
            val saved = resolver.openFileDescriptor(uri, "rw")?.use { pfd ->
                TagLib.savePictures(pfd.dup().detachFd(), arrayOf(picture))
            } ?: false
            saved && resolver.openFileDescriptor(uri, "r")?.use { pfd ->
                TagLib.getFrontCover(pfd.dup().detachFd())?.data?.size == image.size
            } == true
        }
    }

    override suspend fun removeEmbedded(songIds: List<Long>): TagWriteResult = withContext(io) {
        modifyPictures(songIds) { uri ->
            val saved = resolver.openFileDescriptor(uri, "rw")?.use { pfd ->
                TagLib.savePictures(pfd.dup().detachFd(), emptyArray())
            } ?: false
            saved && resolver.openFileDescriptor(uri, "r")?.use { pfd ->
                TagLib.getPictures(pfd.dup().detachFd()).isEmpty()
            } == true
        }
    }

    override suspend fun saveToPictures(image: ByteArray, name: String): Boolean = withContext(io) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return@withContext false
        val mime = imageMimeType(image) ?: return@withContext false
        val extension = if (mime == "image/png") "png" else "jpg"
        val safeName = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "cover" }
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$safeName.$extension")
            put(MediaStore.Images.Media.MIME_TYPE, mime)
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Musiq")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values) ?: return@withContext false
        try {
            resolver.openOutputStream(uri)?.use { it.write(image) } ?: error("no stream")
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            true
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            false
        }
    }

    private suspend fun modifyPictures(
        songIds: List<Long>,
        mutate: (android.net.Uri) -> Boolean
    ): TagWriteResult {
        var saved = 0
        var failed = 0
        for (id in songIds) {
            val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
            when (writer.write(uri, id) { mutate(uri) }) {
                WriteOutcome.OK -> {
                    saved++
                    dao.getById(id)?.path?.takeIf { it.isNotBlank() }?.let {
                        MediaScannerConnection.scanFile(context, arrayOf(it), null, null)
                    }
                }
                WriteOutcome.FAILED -> failed++
                WriteOutcome.DENIED -> return TagWriteResult(saved, failed + 1, permissionDenied = true)
            }
        }
        return TagWriteResult(saved, failed)
    }

    /** Returns the body, or null for a non-200 answer. Throws IOException when offline. */
    private fun httpGet(url: String, accept: String?): ByteArray? {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", USER_AGENT)
            if (accept != null) connection.setRequestProperty("Accept", accept)
            if (connection.responseCode != 200) return null
            val length = connection.contentLength
            if (length > MAX_IMAGE_BYTES) return null
            return connection.inputStream.use { it.readBytes() }.takeIf { it.size <= MAX_IMAGE_BYTES }
        } finally {
            connection.disconnect()
        }
    }
}
