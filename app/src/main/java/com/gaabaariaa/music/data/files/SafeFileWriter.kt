package com.gaabaariaa.music.data.files

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

enum class WriteOutcome { OK, FAILED, DENIED }

/**
 * Runs a modification of a media file with a safety net: the original is copied first and put back
 * if the modification throws or reports failure.
 */
@Singleton
class SafeFileWriter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val resolver: ContentResolver
) {
    /** [mutate] must write the file, verify the result and return true only if everything is fine. */
    fun write(uri: Uri, id: Long, mutate: () -> Boolean): WriteOutcome {
        val dir = File(context.cacheDir, "write-backup").apply { mkdirs() }
        val backup = File(dir, "$id.bak")
        try {
            val copied = resolver.openInputStream(uri)?.use { input ->
                backup.outputStream().use { input.copyTo(it) }
                true
            } ?: false
            if (!copied) return WriteOutcome.FAILED

            val ok = try {
                mutate()
            } catch (e: SecurityException) {
                return WriteOutcome.DENIED
            } catch (e: Exception) {
                false
            }
            if (!ok) {
                restore(uri, backup)
                return WriteOutcome.FAILED
            }
            return WriteOutcome.OK
        } catch (e: SecurityException) {
            return WriteOutcome.DENIED
        } catch (e: Exception) {
            return WriteOutcome.FAILED
        } finally {
            backup.delete()
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
}
