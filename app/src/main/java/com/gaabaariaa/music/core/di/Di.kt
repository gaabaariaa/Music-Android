package com.gaabaariaa.music.core.di

import android.content.ContentResolver
import android.content.Context
import androidx.room.Room
import com.gaabaariaa.music.data.LibraryRepositoryImpl
import com.gaabaariaa.music.data.local.MusicDatabase
import com.gaabaariaa.music.data.local.SongDao
import com.gaabaariaa.music.data.settings.DataStoreSettingsRepository
import com.gaabaariaa.music.domain.repository.LibraryRepository
import com.gaabaariaa.music.domain.repository.SettingsRepository
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
        Room.databaseBuilder(context, MusicDatabase::class.java, "music.db").build()

    @Provides
    fun provideSongDao(db: MusicDatabase): SongDao = db.songDao()

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
    abstract fun bindSettingsRepository(impl: DataStoreSettingsRepository): SettingsRepository
}
