package com.gaabaariaa.music.data.downloads

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentUris
import android.content.ContentValues
import android.content.ContentResolver
import android.content.Context
import android.content.pm.ServiceInfo
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.gaabaariaa.music.data.local.DownloadDao
import com.gaabaariaa.music.data.local.DownloadEntity
import com.gaabaariaa.music.data.net.USER_AGENT
import com.gaabaariaa.music.data.net.httpGet
import com.gaabaariaa.music.domain.model.DownloadState
import com.gaabaariaa.music.domain.model.TagField
import com.gaabaariaa.music.domain.repository.ArtworkRepository
import com.gaabaariaa.music.domain.repository.LibraryScanner
import com.gaabaariaa.music.domain.repository.TagRepository
import com.kyant.taglib.TagLib
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive

private const val CHANNEL_ID = "downloads"
private const val MAX_BYTES = 600L * 1024 * 1024
private const val MAX_ATTEMPTS = 3

/** Thrown for failures that retrying will not fix; [code] is shown to the user. */
private class DownloadFailure(val code: String) : Exception(code)

@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted params: WorkerParameters,
    private val dao: DownloadDao,
    private val resolver: ContentResolver,
    private val tags: TagRepository,
    private val artwork: ArtworkRepository,
    private val scanner: LibraryScanner
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val id = inputData.getLong(KEY_DOWNLOAD_ID, -1L)
        val row = dao.get(id) ?: return Result.success()
        if (row.state == DownloadState.PAUSED.name || row.state == DownloadState.COMPLETED.name) {
            return Result.success()
        }
        dao.updateState(id, DownloadState.DOWNLOADING.name, "")
        try {
            setForeground(foregroundInfo(id, row.title))
        } catch (e: Exception) {
            // Without notification permission the download still runs, just without a notification.
        }

        val part = partFile(appContext, id)
        return try {
            download(row, part)
            val songId = finalizeToLibrary(row, part)
            dao.markCompleted(id, songId)
            scanner.scanNow(force = true)
            Result.success()
        } catch (e: CancellationException) {
            throw e // pause or cancel: the repository already stored the new state
        } catch (e: DownloadFailure) {
            part.delete()
            dao.updateState(id, DownloadState.FAILED.name, e.code)
            Result.failure()
        } catch (e: IOException) {
            // Keep the partial file so a retry resumes where it stopped.
            if (runAttemptCount + 1 >= MAX_ATTEMPTS) {
                dao.updateState(id, DownloadState.FAILED.name, "NETWORK")
                Result.failure()
            } else {
                dao.updateState(id, DownloadState.QUEUED.name, "")
                Result.retry()
            }
        } catch (e: Exception) {
            part.delete()
            dao.updateState(id, DownloadState.FAILED.name, "UNKNOWN")
            Result.failure()
        }
    }

    // ------------------------------------------------------------------ transfer

    private suspend fun download(row: DownloadEntity, part: File) {
        var resumeFrom = if (part.exists()) part.length() else 0L
        var connection = open(row.url, resumeFrom)
        if (resumeFrom > 0 && connection.responseCode == 416) {
            connection.disconnect()
            part.delete()
            resumeFrom = 0L
            connection = open(row.url, 0L)
        }
        try {
            val code = connection.responseCode
            if (code != 200 && code != 206) throw DownloadFailure("HTTP_$code")
            val type = connection.contentType.orEmpty().lowercase()
            val audioLike = type.startsWith("audio/") || type == "application/ogg" ||
                type.contains("octet-stream") || type.isEmpty()
            if (!audioLike) throw DownloadFailure("NOT_AUDIO")

            val append = code == 206 && resumeFrom > 0
            if (!append) part.delete()
            val start = if (append) resumeFrom else 0L
            val total = when {
                code == 206 -> connection.getHeaderField("Content-Range")?.substringAfterLast('/')?.toLongOrNull()
                    ?: -1L
                else -> connection.contentLengthLong
            }
            if (total > MAX_BYTES) throw DownloadFailure("TOO_LARGE")

            var downloaded = start
            var lastReport = System.currentTimeMillis()
            var lastBytes = downloaded
            var speed = 0L
            dao.updateProgress(row.id, downloaded, total, 0L)

            RandomAccessFile(part, "rw").use { file ->
                file.seek(start)
                connection.inputStream.use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        file.write(buffer, 0, read)
                        downloaded += read
                        if (downloaded > MAX_BYTES) throw DownloadFailure("TOO_LARGE")
                        val now = System.currentTimeMillis()
                        if (now - lastReport >= 500) {
                            val instant = (downloaded - lastBytes) * 1000 / (now - lastReport)
                            speed = if (speed == 0L) instant else (speed * 6 + instant * 4) / 10
                            dao.updateProgress(row.id, downloaded, total, speed)
                            lastReport = now
                            lastBytes = downloaded
                        }
                    }
                }
            }
            if (total > 0 && downloaded != total) throw IOException("Connection closed early")
            dao.updateProgress(row.id, downloaded, if (total > 0) total else downloaded, 0L)
        } finally {
            connection.disconnect()
        }
    }

    /** Follows redirects by hand so that every hop can be required to be https. */
    private fun open(startUrl: String, rangeStart: Long): HttpURLConnection {
        var current = startUrl
        repeat(6) {
            if (!current.startsWith("https://")) throw DownloadFailure("INSECURE")
            val connection = URL(current).openConnection() as HttpURLConnection
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("User-Agent", USER_AGENT)
            if (rangeStart > 0) connection.setRequestProperty("Range", "bytes=$rangeStart-")
            val code = connection.responseCode
            if (code in listOf(301, 302, 303, 307, 308)) {
                val location = connection.getHeaderField("Location")
                connection.disconnect()
                if (location.isNullOrBlank()) throw DownloadFailure("HTTP_$code")
                current = URL(URL(current), location).toString()
            } else {
                return connection
            }
        }
        throw DownloadFailure("TOO_MANY_REDIRECTS")
    }

    // ------------------------------------------------------------------ saving

    /** Verifies the file is real audio, stores it in the music library and fills in missing tags. */
    private suspend fun finalizeToLibrary(row: DownloadEntity, part: File): Long {
        val valid = try {
            ParcelFileDescriptor.open(part, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                TagLib.getMetadata(pfd.dup().detachFd(), false) != null
            }
        } catch (e: Exception) {
            false
        }
        if (!valid) throw DownloadFailure("INVALID_FILE")

        val baseName = listOf(row.artist, row.title).filter { it.isNotBlank() }.joinToString(" - ")
            .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim().take(120).ifBlank { "download-${row.id}" }
        val fileName = "$baseName.${row.extension}"

        val songId = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) saveWithMediaStore(fileName, row.extension, part)
            else saveToAppMusicFolder(fileName, part)
        } catch (e: IOException) {
            throw DownloadFailure("STORAGE")
        }
        part.delete()

        if (songId > 0) {
            // Tags from the source fill only what the file itself lacks.
            val existing = tags.read(listOf(songId))[songId].orEmpty()
            val wanted = mapOf(TagField.TITLE to row.title, TagField.ARTIST to row.artist, TagField.ALBUM to row.album)
            val changes = wanted.filter { (field, value) -> value.isNotBlank() && existing[field].isNullOrBlank() }
            if (changes.isNotEmpty()) tags.write(listOf(songId), changes)
            embedCover(row, songId)
        }
        return songId
    }

    private suspend fun embedCover(row: DownloadEntity, songId: Long) {
        if (!row.coverUrl.startsWith("https://")) return
        try {
            val body = httpGet(row.coverUrl, null, 3 * 1024 * 1024).body ?: return
            artwork.embed(listOf(songId), body)
        } catch (e: Exception) {
            // The cover is a bonus; the song is already saved.
        }
    }

    private fun saveWithMediaStore(fileName: String, extension: String, part: File): Long {
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Audio.Media.MIME_TYPE, mimeFor(extension))
            put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/Musiq")
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values) ?: throw IOException("insert failed")
        try {
            resolver.openOutputStream(uri)?.use { out -> part.inputStream().use { it.copyTo(out) } }
                ?: throw IOException("no output stream")
            resolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw if (e is IOException) e else IOException(e)
        }
        return ContentUris.parseId(uri)
    }

    /** Android 9 and older: app music folder, indexed by the media scanner. */
    private fun saveToAppMusicFolder(fileName: String, part: File): Long {
        val dir = File(appContext.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "Musiq").apply { mkdirs() }
        val target = File(dir, fileName)
        part.copyTo(target, overwrite = true)
        MediaScannerConnection.scanFile(appContext, arrayOf(target.absolutePath), null, null)
        return 0L
    }

    private fun mimeFor(extension: String) = when (extension.lowercase()) {
        "mp3" -> "audio/mpeg"
        "flac" -> "audio/flac"
        "ogg", "oga", "opus" -> "audio/ogg"
        "m4a" -> "audio/mp4"
        "aac" -> "audio/aac"
        "wav" -> "audio/x-wav"
        else -> "audio/mpeg"
    }

    private fun foregroundInfo(id: Long, title: String): ForegroundInfo {
        val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Downloads", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setContentTitle(title.ifBlank { "Download" })
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        val notificationId = 2000 + (id % 1000).toInt()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }
}
