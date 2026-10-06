package com.gaabaariaa.music.data.tags

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.MediaStore
import com.gaabaariaa.music.core.di.IoDispatcher
import com.gaabaariaa.music.core.util.parseTrackNumber
import com.gaabaariaa.music.core.util.parseYear
import com.gaabaariaa.music.data.local.SongDao
import com.gaabaariaa.music.domain.model.TagField
import com.gaabaariaa.music.domain.model.TagValues
import com.gaabaariaa.music.domain.model.TagWriteResult
import com.gaabaariaa.music.domain.repository.TagRepository
import com.kyant.taglib.TagLib
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

private enum class Outcome { OK, FAILED, DENIED }

private val TagField.key: String
    get() = when (this) {
        TagField.TITLE -> "TITLE"
        TagField.ARTIST -> "ARTIST"
        TagField.ALBUM -> "ALBUM"
        TagField.ALBUM_ARTIST -> "ALBUMARTIST"
        TagField.GENRE -> "GENRE"
        TagField.YEAR -> "DATE"
        TagField.TRACK -> "TRACKNUMBER"
        TagField.DISC -> "DISCNUMBER"
        TagField.COMPOSER -> "COMPOSER"
        TagField.COMMENT -> "COMMENT"
        TagField.LYRICS -> "LYRICS"
    }

@Singleton
class TaglibTagRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val resolver: ContentResolver,
    private val dao: SongDao,
    @IoDispatcher private val io: CoroutineDispatcher
) : TagRepository {

    private fun uriOf(id: Long): Uri =
        ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)

    override suspend fun read(songIds: List<Long>): Map<Long, TagValues> = withContext(io) {
        val result = LinkedHashMap<Long, TagValues>()
        for (id in songIds) {
            readProperties(uriOf(id))?.let { props ->
                result[id] = TagField.entries.associateWith { field ->
                    props[field.key]?.joinToString("; ").orEmpty()
                }
            }
        }
        result
    }

    override fun hasWriteAccess(songIds: List<Long>): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return true
        return songIds.all { id ->
            context.checkUriPermission(
                uriOf(id), Process.myPid(), Process.myUid(), Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    override fun createWriteRequest(songIds: List<Long>): IntentSender? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return MediaStore.createWriteRequest(resolver, songIds.map { uriOf(it) }).intentSender
    }

    override suspend fun write(songIds: List<Long>, changes: TagValues): TagWriteResult = withContext(io) {
        var saved = 0
        var failed = 0
        for (id in songIds) {
            when (writeOne(id, changes)) {
                Outcome.OK -> saved++
                Outcome.FAILED -> failed++
                Outcome.DENIED -> return@withContext TagWriteResult(saved, failed + 1, permissionDenied = true)
            }
        }
        TagWriteResult(saved, failed)
    }

    private fun readProperties(uri: Uri): HashMap<String, Array<String>>? = try {
        resolver.openFileDescriptor(uri, "r")?.use { pfd ->
            TagLib.getMetadata(pfd.dup().detachFd(), false)?.propertyMap
        }
    } catch (e: Exception) {
        null
    }

    private suspend fun writeOne(id: Long, changes: TagValues): Outcome {
        val uri = uriOf(id)
        val dir = File(context.cacheDir, "tag-backup").apply { mkdirs() }
        val backup = File(dir, "$id.bak")
        try {
            // 1. Safety copy of the original file.
            val copied = resolver.openInputStream(uri)?.use { input ->
                backup.outputStream().use { input.copyTo(it) }
                true
            } ?: false
            if (!copied) return Outcome.FAILED

            // 2. Write, then read back and compare.
            val ok = try {
                applyChanges(uri, changes)
            } catch (e: SecurityException) {
                return Outcome.DENIED
            } catch (e: Exception) {
                false
            }
            if (!ok) {
                restore(uri, backup)
                return Outcome.FAILED
            }

            // 3. Keep the library in sync and ask MediaStore to re-index the file.
            updateDatabase(id, changes)
            return Outcome.OK
        } finally {
            backup.delete()
        }
    }

    private fun applyChanges(uri: Uri, changes: TagValues): Boolean {
        val current = readProperties(uri) ?: return false
        val merged = HashMap<String, Array<String>>(current)
        for ((field, value) in changes) {
            if (value.isBlank()) merged.remove(field.key) else merged[field.key] = arrayOf(value.trim())
        }
        val saved = resolver.openFileDescriptor(uri, "rw")?.use { pfd ->
            TagLib.savePropertyMap(pfd.dup().detachFd(), merged)
        } ?: return false
        if (!saved) return false

        val after = readProperties(uri) ?: return false
        return changes.all { (field, value) ->
            val stored = after[field.key]?.firstOrNull()
            when {
                value.isBlank() -> stored.isNullOrEmpty()
                // Lyrics and comments may be normalised by the tag format; only require them to exist.
                field == TagField.LYRICS || field == TagField.COMMENT -> !stored.isNullOrEmpty()
                else -> stored == value.trim()
            }
        }
    }

    private fun restore(uri: Uri, backup: File) {
        try {
            resolver.openOutputStream(uri, "wt")?.use { out ->
                backup.inputStream().use { it.copyTo(out) }
            }
        } catch (e: Exception) {
            // Nothing more can be done; the caller reports the failure.
        }
    }

    private suspend fun updateDatabase(id: Long, changes: TagValues) {
        val entity = dao.getById(id) ?: return
        var updated = entity
        for ((field, raw) in changes) {
            val value = raw.trim()
            updated = when (field) {
                TagField.TITLE -> updated.copy(title = value)
                TagField.ARTIST -> updated.copy(artist = value)
                TagField.ALBUM -> updated.copy(album = value)
                TagField.ALBUM_ARTIST -> updated.copy(albumArtist = value)
                TagField.GENRE -> updated.copy(genre = value)
                TagField.YEAR -> updated.copy(year = parseYear(value))
                TagField.TRACK -> updated.copy(track = parseTrackNumber(value))
                else -> updated
            }
        }
        dao.upsertAll(listOf(updated))
        if (entity.path.isNotBlank()) {
            MediaScannerConnection.scanFile(context, arrayOf(entity.path), null, null)
        }
    }
}
