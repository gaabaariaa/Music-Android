package com.gaabaariaa.music.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.gaabaariaa.music.domain.model.Accent
import com.gaabaariaa.music.domain.model.AppSettings
import com.gaabaariaa.music.domain.model.ThemeMode
import com.gaabaariaa.music.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class DataStoreSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) : SettingsRepository {

    private object Keys {
        val THEME = stringPreferencesKey("theme_mode")
        val DYNAMIC = booleanPreferencesKey("dynamic_color")
        val AMOLED = booleanPreferencesKey("amoled")
        val ACCENT = stringPreferencesKey("accent")
    }

    override val settings: Flow<AppSettings> = context.settingsDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { p ->
            val defaults = AppSettings()
            AppSettings(
                themeMode = p[Keys.THEME].toEnum(defaults.themeMode),
                dynamicColor = p[Keys.DYNAMIC] ?: defaults.dynamicColor,
                amoled = p[Keys.AMOLED] ?: defaults.amoled,
                accent = p[Keys.ACCENT].toEnum(defaults.accent)
            )
        }

    override suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { it[Keys.THEME] = mode.name }
    }

    override suspend fun setDynamicColor(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.DYNAMIC] = enabled }
    }

    override suspend fun setAmoled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.AMOLED] = enabled }
    }

    override suspend fun setAccent(accent: Accent) {
        context.settingsDataStore.edit { it[Keys.ACCENT] = accent.name }
    }
}

private inline fun <reified E : Enum<E>> String?.toEnum(default: E): E =
    this?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: default
