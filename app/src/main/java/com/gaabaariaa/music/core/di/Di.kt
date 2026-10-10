package com.gaabaariaa.music.core.di

import android.content.ContentResolver
import android.content.Context
import androidx.room.Room
import com.gaabaariaa.music.data.LibraryRepositoryImpl
import com.gaabaariaa.music.data.local.LyricsDao
import com.gaabaariaa.music.data.local.MIGRATION_1_2
import com.gaabaariaa.music.data.local.DownloadDao
import com.gaabaariaa.music.data.local.InsightsDao
import com.gaabaariaa.music.data.local.MIGRATION_2_3
import com.gaabaariaa.music.data.local.MIGRATION_3_4
import com.gaabaariaa.music.data.local.MIGRATION_4_5
import com.gaabaariaa.music.data.local.MIGRATION_5_6
import com.gaabaariaa.music.data.local.MIGRATION_6_7
import com.gaabaariaa.music.data.local.PersonalDao
import com.gaabaariaa.music.data.local.PlaylistDao
import com.gaabaariaa.music.data.local.MusicDatabase
import com.gaabaariaa.music.data.local.SongDao
import com.gaabaariaa.music.data.settings.DataStoreSettingsRepository
import com.gaabaariaa.music.data.artwork.ArtworkRepositoryImpl
import com.gaabaariaa.music.data.downloads.DownloadRepositoryImpl
import com.gaabaariaa.music.data.insights.HealthRepositoryImpl
import com.gaabaariaa.music.data.personal.PersonalRepositoryImpl
import com.gaabaariaa.music.data.playlists.PlaylistRepositoryImpl
import com.gaabaariaa.music.data.insights.SearchRepositoryImpl
import com.gaabaariaa.music.data.lyrics.LyricsRepositoryImpl
import com.gaabaariaa.music.data.sources.MusicSourceRepositoryImpl
import com.gaabaariaa.music.data.tags.TaglibTagRepository
import com.gaabaariaa.music.data.work.WorkManagerLibraryAuditor
import com.gaabaariaa.music.data.work.WorkManagerLibraryScanner
import com.gaabaariaa.music.domain.repository.ArtworkRepository
import com.gaabaariaa.music.domain.repository.DownloadRepository
import com.gaabaariaa.music.domain.repository.HealthRepository
import com.gaabaariaa.music.domain.repository.LibraryAuditor
import com.gaabaariaa.music.domain.repository.LibraryRepository
import com.gaabaariaa.music.domain.repository.LibraryScanner
import com.gaabaariaa.music.domain.repository.LyricsRepository
import com.gaabaariaa.music.domain.repository.MusicSourceRepository
import com.gaabaariaa.music.domain.repository.PersonalRepository
import com.gaabaariaa.music.domain.repository.PlaylistRepository
import com.gaabaariaa.music.domain.repository.SearchRepository
import com.gaabaariaa.music.domain.repository.SettingsRepository
import com.gaabaariaa.music.domain.repository.TagRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MusicDatabase =
        Room.databaseBuilder(context, MusicDatabase::class.java, "music.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
            .build()

    @Provides
    fun provideSongDao(db: MusicDatabase): SongDao = db.songDao()

    @Provides
    fun providePlaylistDao(db: MusicDatabase): PlaylistDao = db.playlistDao()

    @Provides
    fun providePersonalDao(db: MusicDatabase): PersonalDao = db.personalDao()

    @Provides
    fun provideDownloadDao(db: MusicDatabase): DownloadDao = db.downloadDao()

    @Provides
    fun provideInsightsDao(db: MusicDatabase): InsightsDao = db.insightsDao()

    @Provides
    fun provideLyricsDao(db: MusicDatabase): LyricsDao = db.lyricsDao()

    @Provides
    fun provideContentResolver(@ApplicationContext context: Context): ContentResolver =
        context.contentResolver

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {
    @Binds
    abstract fun bindLibraryRepository(impl: LibraryRepositoryImpl): LibraryRepository

    @Binds
    abstract fun bindLibraryScanner(impl: WorkManagerLibraryScanner): LibraryScanner

    @Binds
    abstract fun bindArtworkRepository(impl: ArtworkRepositoryImpl): ArtworkRepository

    @Binds
    abstract fun bindLyricsRepository(impl: LyricsRepositoryImpl): LyricsRepository

    @Binds
    abstract fun bindMusicSourceRepository(impl: MusicSourceRepositoryImpl): MusicSourceRepository

    @Binds
    abstract fun bindDownloadRepository(impl: DownloadRepositoryImpl): DownloadRepository

    @Binds
    abstract fun bindPlaylistRepository(impl: PlaylistRepositoryImpl): PlaylistRepository

    @Binds
    abstract fun bindPersonalRepository(impl: PersonalRepositoryImpl): PersonalRepository

    @Binds
    abstract fun bindSearchRepository(impl: SearchRepositoryImpl): SearchRepository

    @Binds
    abstract fun bindHealthRepository(impl: HealthRepositoryImpl): HealthRepository

    @Binds
    abstract fun bindLibraryAuditor(impl: WorkManagerLibraryAuditor): LibraryAuditor

    @Binds
    abstract fun bindTagRepository(impl: TaglibTagRepository): TagRepository

    @Binds
    abstract fun bindSettingsRepository(impl: DataStoreSettingsRepository): SettingsRepository
}
