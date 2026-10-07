package com.gaabaariaa.music.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.gaabaariaa.music.domain.repository.LibraryScanner
import com.gaabaariaa.music.domain.repository.DownloadRepository
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class MusicApplication : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var scanner: LibraryScanner
    @Inject lateinit var downloads: DownloadRepository

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        scanner.schedulePeriodic()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { downloads.reconcile() }
    }
}
