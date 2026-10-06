package com.gaabaariaa.music.data.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.gaabaariaa.music.core.util.hasAudioPermission
import com.gaabaariaa.music.domain.model.ScanState
import com.gaabaariaa.music.domain.repository.LibraryRepository
import com.gaabaariaa.music.domain.repository.LibraryScanner
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.map

private const val KEY_COUNT = "count"

@HiltWorker
class LibraryScanWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: LibraryRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // Without permission MediaStore returns nothing, which would wipe the library.
        if (!hasAudioPermission(applicationContext)) return Result.success()
        return try {
            Result.success(workDataOf(KEY_COUNT to repository.scan()))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure()
        }
    }
}

@Singleton
class WorkManagerLibraryScanner @Inject constructor(
    @ApplicationContext private val context: Context
) : LibraryScanner {

    private val workManager: WorkManager get() = WorkManager.getInstance(context)

    override val state: Flow<ScanState> =
        kotlinx.coroutines.flow.flow {
            emitAll(workManager.getWorkInfosForUniqueWorkFlow(ONE_TIME).map { infos ->
                val info = infos.lastOrNull()
                when (info?.state) {
                    WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING, WorkInfo.State.BLOCKED ->
                        ScanState(scanning = true)
                    WorkInfo.State.SUCCEEDED ->
                        ScanState(lastCount = info.outputData.getInt(KEY_COUNT, -1).takeIf { it >= 0 })
                    WorkInfo.State.FAILED -> ScanState(failed = true)
                    else -> ScanState()
                }
            })
        }

    override fun scanNow(force: Boolean) {
        workManager.enqueueUniqueWork(
            ONE_TIME,
            if (force) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<LibraryScanWorker>().build()
        )
    }

    override fun schedulePeriodic() {
        val request = PeriodicWorkRequestBuilder<LibraryScanWorker>(6, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            .build()
        workManager.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    private companion object {
        const val ONE_TIME = "library_scan"
        const val PERIODIC = "library_scan_periodic"
    }
}
