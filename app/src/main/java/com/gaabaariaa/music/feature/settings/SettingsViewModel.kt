package com.gaabaariaa.music.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gaabaariaa.music.domain.model.Accent
import com.gaabaariaa.music.domain.model.AppLanguage
import com.gaabaariaa.music.domain.model.AppSettings
import com.gaabaariaa.music.domain.model.CardShape
import com.gaabaariaa.music.domain.model.HomeSection
import com.gaabaariaa.music.domain.model.LibraryLayout
import com.gaabaariaa.music.domain.model.LibraryTab
import com.gaabaariaa.music.domain.model.ThemeMode
import com.gaabaariaa.music.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository
) : ViewModel() {

    // The first value is read synchronously (a few milliseconds), so the very first frame already has the
    // saved theme and language instead of flashing the defaults.
    val settings: StateFlow<AppSettings> = repository.settings
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            try {
                runBlocking { repository.settings.first() }
            } catch (e: Exception) {
                AppSettings()
            }
        )

    fun setThemeMode(mode: ThemeMode) { viewModelScope.launch { repository.setThemeMode(mode) } }
    fun setDynamicColor(enabled: Boolean) { viewModelScope.launch { repository.setDynamicColor(enabled) } }
    fun setAmoled(enabled: Boolean) { viewModelScope.launch { repository.setAmoled(enabled) } }
    fun setDownloadWifiOnly(enabled: Boolean) { viewModelScope.launch { repository.setDownloadWifiOnly(enabled) } }
    fun setJamendoClientId(id: String) { viewModelScope.launch { repository.setJamendoClientId(id) } }
    fun setLanguage(language: AppLanguage) { viewModelScope.launch { repository.setLanguage(language) } }
    fun setCornerRadius(dp: Int) { viewModelScope.launch { repository.setCornerRadius(dp) } }
    fun setCardShape(shape: CardShape) { viewModelScope.launch { repository.setCardShape(shape) } }
    fun setTextScale(scale: Float) { viewModelScope.launch { repository.setTextScale(scale) } }
    fun setLibraryLayout(layout: LibraryLayout) { viewModelScope.launch { repository.setLibraryLayout(layout) } }
    fun setGridColumns(columns: Int) { viewModelScope.launch { repository.setGridColumns(columns) } }
    fun setShowArtwork(enabled: Boolean) { viewModelScope.launch { repository.setShowArtwork(enabled) } }
    fun setAnimations(enabled: Boolean) { viewModelScope.launch { repository.setAnimations(enabled) } }
    fun setMiniPlayer(enabled: Boolean) { viewModelScope.launch { repository.setMiniPlayer(enabled) } }
    fun setKeepScreenOn(enabled: Boolean) { viewModelScope.launch { repository.setKeepScreenOn(enabled) } }
    fun setLyricsFirst(enabled: Boolean) { viewModelScope.launch { repository.setLyricsFirst(enabled) } }

    fun moveLibraryTab(tab: LibraryTab, delta: Int) {
        val list = settings.value.libraryTabs.toMutableList()
        val from = list.indexOf(tab)
        val to = from + delta
        if (from < 0 || to !in list.indices) return
        list.add(to, list.removeAt(from))
        viewModelScope.launch { repository.setLibraryTabs(list) }
    }

    fun moveHomeSection(section: HomeSection, delta: Int) {
        val list = settings.value.homeSections.toMutableList()
        val from = list.indexOfFirst { it.section == section }
        val to = from + delta
        if (from < 0 || to !in list.indices) return
        list.add(to, list.removeAt(from))
        viewModelScope.launch { repository.setHomeSections(list) }
    }

    fun setHomeSectionEnabled(section: HomeSection, enabled: Boolean) {
        val list = settings.value.homeSections.map { if (it.section == section) it.copy(enabled = enabled) else it }
        viewModelScope.launch { repository.setHomeSections(list) }
    }

    fun setAccent(accent: Accent) { viewModelScope.launch { repository.setAccent(accent) } }
}
