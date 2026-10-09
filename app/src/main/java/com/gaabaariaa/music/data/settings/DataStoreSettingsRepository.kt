package com.gaabaariaa.music.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.gaabaariaa.music.domain.model.Accent
import com.gaabaariaa.music.domain.model.AppLanguage
import com.gaabaariaa.music.domain.model.AppSettings
import com.gaabaariaa.music.domain.model.CardShape
import com.gaabaariaa.music.domain.model.HomeSectionConfig
import com.gaabaariaa.music.domain.model.LibraryLayout
import com.gaabaariaa.music.domain.model.LibraryTab
import com.gaabaariaa.music.domain.model.decodeHomeSections
import com.gaabaariaa.music.domain.model.decodeLibraryTabs
import com.gaabaariaa.music.domain.model.encodeHomeSections
import com.gaabaariaa.music.domain.model.encodeLibraryTabs
import com.gaabaariaa.music.domain.model.ThemeMode
import com.gaabaariaa.music.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
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
        val WIFI_ONLY = booleanPreferencesKey("download_wifi_only")
        val JAMENDO = stringPreferencesKey("jamendo_client_id")
        val CORNER = intPreferencesKey("corner_radius")
        val CARD_SHAPE = stringPreferencesKey("card_shape")
        val TEXT_SCALE = floatPreferencesKey("text_scale")
        val LAYOUT = stringPreferencesKey("library_layout")
        val COLUMNS = intPreferencesKey("grid_columns")
        val ARTWORK = booleanPreferencesKey("show_artwork")
        val ANIMATIONS = booleanPreferencesKey("animations")
        val MINI_PLAYER = booleanPreferencesKey("mini_player")
        val KEEP_ON = booleanPreferencesKey("keep_screen_on")
        val LYRICS_FIRST = booleanPreferencesKey("lyrics_first")
        val TABS = stringPreferencesKey("library_tabs")
        val HOME = stringPreferencesKey("home_sections")
        val LANGUAGE = stringPreferencesKey("language")
    }

    override val settings: Flow<AppSettings> = context.settingsDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { p ->
            val defaults = AppSettings()
            AppSettings(
                themeMode = p[Keys.THEME].toEnum(defaults.themeMode),
                dynamicColor = p[Keys.DYNAMIC] ?: defaults.dynamicColor,
                amoled = p[Keys.AMOLED] ?: defaults.amoled,
                accent = p[Keys.ACCENT].toEnum(defaults.accent),
                downloadWifiOnly = p[Keys.WIFI_ONLY] ?: defaults.downloadWifiOnly,
                jamendoClientId = p[Keys.JAMENDO] ?: defaults.jamendoClientId,
                cornerRadiusDp = (p[Keys.CORNER] ?: defaults.cornerRadiusDp).coerceIn(0, 32),
                cardShape = p[Keys.CARD_SHAPE].toEnum(defaults.cardShape),
                textScale = (p[Keys.TEXT_SCALE] ?: defaults.textScale).coerceIn(0.8f, 1.4f),
                libraryLayout = p[Keys.LAYOUT].toEnum(defaults.libraryLayout),
                gridColumns = (p[Keys.COLUMNS] ?: defaults.gridColumns).coerceIn(2, 4),
                showArtwork = p[Keys.ARTWORK] ?: defaults.showArtwork,
                animationsEnabled = p[Keys.ANIMATIONS] ?: defaults.animationsEnabled,
                miniPlayerEnabled = p[Keys.MINI_PLAYER] ?: defaults.miniPlayerEnabled,
                keepScreenOn = p[Keys.KEEP_ON] ?: defaults.keepScreenOn,
                lyricsFirst = p[Keys.LYRICS_FIRST] ?: defaults.lyricsFirst,
                libraryTabs = decodeLibraryTabs(p[Keys.TABS]),
                homeSections = decodeHomeSections(p[Keys.HOME]),
                language = p[Keys.LANGUAGE].toEnum(defaults.language)
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

    override suspend fun setDownloadWifiOnly(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.WIFI_ONLY] = enabled }
    }

    override suspend fun setJamendoClientId(id: String) {
        context.settingsDataStore.edit { it[Keys.JAMENDO] = id.trim() }
    }

    override suspend fun setLanguage(language: AppLanguage) { context.settingsDataStore.edit { it[Keys.LANGUAGE] = language.name } }
    override suspend fun setCornerRadius(dp: Int) { context.settingsDataStore.edit { it[Keys.CORNER] = dp } }
    override suspend fun setCardShape(shape: CardShape) { context.settingsDataStore.edit { it[Keys.CARD_SHAPE] = shape.name } }
    override suspend fun setTextScale(scale: Float) { context.settingsDataStore.edit { it[Keys.TEXT_SCALE] = scale } }
    override suspend fun setLibraryLayout(layout: LibraryLayout) { context.settingsDataStore.edit { it[Keys.LAYOUT] = layout.name } }
    override suspend fun setGridColumns(columns: Int) { context.settingsDataStore.edit { it[Keys.COLUMNS] = columns } }
    override suspend fun setShowArtwork(enabled: Boolean) { context.settingsDataStore.edit { it[Keys.ARTWORK] = enabled } }
    override suspend fun setAnimations(enabled: Boolean) { context.settingsDataStore.edit { it[Keys.ANIMATIONS] = enabled } }
    override suspend fun setMiniPlayer(enabled: Boolean) { context.settingsDataStore.edit { it[Keys.MINI_PLAYER] = enabled } }
    override suspend fun setKeepScreenOn(enabled: Boolean) { context.settingsDataStore.edit { it[Keys.KEEP_ON] = enabled } }
    override suspend fun setLyricsFirst(enabled: Boolean) { context.settingsDataStore.edit { it[Keys.LYRICS_FIRST] = enabled } }
    override suspend fun setLibraryTabs(tabs: List<LibraryTab>) { context.settingsDataStore.edit { it[Keys.TABS] = encodeLibraryTabs(tabs) } }
    override suspend fun setHomeSections(sections: List<HomeSectionConfig>) {
        context.settingsDataStore.edit { it[Keys.HOME] = encodeHomeSections(sections) }
    }

    override suspend fun setAccent(accent: Accent) {
        context.settingsDataStore.edit { it[Keys.ACCENT] = accent.name }
    }
}

private inline fun <reified E : Enum<E>> String?.toEnum(default: E): E =
    this?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: default

/** Reads the saved language before any Activity exists (needed to build the localized context). */
fun readLanguageBlocking(context: Context): AppLanguage = try {
    kotlinx.coroutines.runBlocking {
        context.settingsDataStore.data.map { p -> p[stringPreferencesKey("language")].toEnum(AppLanguage.SYSTEM) }
            .first()
    }
} catch (e: Exception) {
    AppLanguage.SYSTEM
}
