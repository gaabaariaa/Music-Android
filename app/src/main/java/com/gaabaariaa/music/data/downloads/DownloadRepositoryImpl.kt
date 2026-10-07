package com.gaabaariaa.music.data.downloads

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.gaabaariaa.music.data.local.DownloadDao
import com.gaabaariaa.music.data.local.DownloadEntity
import com.gaabaariaa.music.domain.model.DownloadItem
import com.gaabaariaa.music.domain.model.DownloadState
import com.gaabaariaa.music.domain.model.DownloadableTrack
import com.gaabaariaa.music.domain.repository.DownloadRepository
import com.gaabaariaa.music.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

internal fun downloadsDir(context: Context): File = File(context.filesDir, "downloads").apply { mkdirs() }

internal fun partFile(context: Context, id: Long): File = File(downloadsDir(context), "$id.part")

internal const val KEY_DOWNLOAD_ID = "id"

internal fun workName(id: Long) = "download_$id"

private fun DownloadEntity.toDomain() = DownloadItem(
    id = id,
    providerId = providerId,
    title = title,
    artist = artist,
    album = album,
    license = license,
    pageUrl = pageUrl,
    state = runCatching { DownloadState.valueOf(state) }.getOrDefault(DownloadState.FAILED),
    totalBytes = totalBytes,
    downloadedBytes = downloadedBytes,
    speedBps = speedBps,
    error = error,
    songId = songId,
    createdAt = createdAt
)

@Singleton
class DownloadRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: DownloadDao,
    private val settings: SettingsRepository
) : DownloadRepository {

    private val workManager: WorkManager get() = WorkManager.getInstance(context)

    override fun observe(): Flow<List<DownloadItem>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun enqueue(track: DownloadableTrack): Long {
        val id = dao.insert(
            DownloadEntity(
                providerId = track.providerId,
                remoteId = track.remoteId,
                title = track.title,
                artist = track.artist,
                album = track.album,
                url = track.downloadUrl,
                extension = track.extension,
                license = track.license,
                pageUrl = track.pageUrl,
                coverUrl = track.coverUrl,
                state = DownloadState.QUEUED.name,
                totalBytes = -1L,
                downloadedBytes = 0L,
                speedBps = 0L,
                error = "",
                songId = 0L,
                createdAt = System.currentTimeMillis()
            )
        )
        schedule(id, ExistingWorkPolicy.REPLACE)
        return id
    }

    override suspend fun pause(id: Long) {
        dao.updateState(id, DownloadState.PAUSED.name, "")
        workManager.cancelUniqueWork(workName(id))
    }

    override suspend fun resume(id: Long) = restart(id)

    override suspend fun retry(id: Long) = restart(id)

    private suspend fun restart(id: Long) {
        dao.updateState(id, DownloadState.QUEUED.name, "")
        schedule(id, ExistingWorkPolicy.REPLACE)
    }

    override suspend fun cancel(id: Long) {
        workManager.cancelUniqueWork(workName(id))
        partFile(context, id).delete()
        dao.delete(id)
    }

    override suspend fun clearFinished() = dao.deleteFinished()

    override suspend fun reconcile() {
        dao.getActive().forEach { schedule(it.id, ExistingWorkPolicy.KEEP) }
    }

    private suspend fun schedule(id: Long, policy: ExistingWorkPolicy) {
        val wifiOnly = settings.settings.first().downloadWifiOnly
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(workDataOf(KEY_DOWNLOAD_ID to id))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                    .build()
            )
            .build()
        workManager.enqueueUniqueWork(workName(id), policy, request)
    }
}
