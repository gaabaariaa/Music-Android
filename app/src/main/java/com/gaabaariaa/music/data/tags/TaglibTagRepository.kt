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
import com.gaabaariaa.music.data.files.SafeFileWriter
import com.gaabaariaa.music.data.files.WriteOutcome
import com.gaabaariaa.music.data.local.SongDao
import com.gaabaariaa.music.domain.model.TagField
import com.gaabaariaa.music.domain.model.TagValues
import com.gaabaariaa.music.domain.model.TagWriteResult
import com.gaabaariaa.music.domain.repository.TagRepository
import com.kyant.taglib.TagLib
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

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
    private val writer: SafeFileWriter,
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
                WriteOutcome.OK -> saved++
                WriteOutcome.FAILED -> failed++
                WriteOutcome.DENIED -> return@withContext TagWriteResult(saved, failed + 1, permissionDenied = true)
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

    private suspend fun writeOne(id: Long, changes: TagValues): WriteOutcome {
        val uri = uriOf(id)
        val outcome = writer.write(uri, id) { applyChanges(uri, changes) }
        if (outcome == WriteOutcome.OK) updateDatabase(id, changes)
        return outcome
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
