package com.gaabaariaa.music.data.work

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.gaabaariaa.music.core.util.hasAudioPermission
import com.gaabaariaa.music.data.local.AuditEntity
import com.gaabaariaa.music.data.local.InsightsDao
import com.gaabaariaa.music.domain.model.AuditState
import com.gaabaariaa.music.domain.repository.LibraryAuditor
import com.kyant.taglib.TagLib
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

private const val KEY_DONE = "done"
private const val KEY_TOTAL = "total"

@HiltWorker
class LibraryAuditWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val dao: InsightsDao,
    private val resolver: ContentResolver
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!hasAudioPermission(applicationContext)) return Result.success()
        return try {
            val pending = dao.songsNeedingAudit()
            val total = pending.size
            var done = 0
            val batch = ArrayList<AuditEntity>()
            for (song in pending) {
                val audit = withContext(Dispatchers.IO) { inspect(song.mediaStoreId, song.sizeBytes) }
                if (audit != null) batch += audit
                done++
                if (batch.size >= 50) {
                    dao.upsertAudits(batch.toList())
                    batch.clear()
                }
                if (done % 20 == 0) setProgress(workDataOf(KEY_DONE to done, KEY_TOTAL to total))
            }
            if (batch.isNotEmpty()) dao.upsertAudits(batch)
            Result.success(workDataOf(KEY_DONE to done, KEY_TOTAL to total))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure()
        }
    }

    /** Reads the file's tags once; unreadable files are skipped and retried at the next audit. */
    private fun inspect(id: Long, size: Long): AuditEntity? = try {
        val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
        resolver.openFileDescriptor(uri, "r")?.use { pfd ->
            val metadata = TagLib.getMetadata(pfd.dup().detachFd(), true)
            metadata?.let {
                AuditEntity(
                    songId = id,
                    sizeBytes = size,
                    hasArtwork = it.pictures.isNotEmpty(),
                    hasLyrics = it.propertyMap["LYRICS"]?.any { text -> text.isNotBlank() } == true
                )
            }
        }
    } catch (e: Exception) {
        null
    }
}

@Singleton
class WorkManagerLibraryAuditor @Inject constructor(
    @ApplicationContext private val context: Context
) : LibraryAuditor {

    private val workManager: WorkManager get() = WorkManager.getInstance(context)

    override val state: Flow<AuditState> = flow {
        emitAll(workManager.getWorkInfosForUniqueWorkFlow(NAME).map { infos ->
            val info = infos.lastOrNull()
            when (info?.state) {
                WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING, WorkInfo.State.BLOCKED ->
                    AuditState(
                        running = true,
                        done = info.progress.getInt(KEY_DONE, 0),
                        total = info.progress.getInt(KEY_TOTAL, 0)
                    )
                else -> AuditState()
            }
        })
    }

    override fun start() {
        workManager.enqueueUniqueWork(
            NAME,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<LibraryAuditWorker>().build()
        )
    }

    private companion object {
        const val NAME = "library_audit"
    }
}
