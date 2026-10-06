package com.gaabaariaa.music.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gaabaariaa.music.domain.model.Accent
import com.gaabaariaa.music.domain.model.AppSettings
import com.gaabaariaa.music.domain.model.ThemeMode
import com.gaabaariaa.music.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository
) : ViewModel() {

    val settings: StateFlow<AppSettings> = repository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    fun setThemeMode(mode: ThemeMode) { viewModelScope.launch { repository.setThemeMode(mode) } }
    fun setDynamicColor(enabled: Boolean) { viewModelScope.launch { repository.setDynamicColor(enabled) } }
    fun setAmoled(enabled: Boolean) { viewModelScope.launch { repository.setAmoled(enabled) } }
    fun setAccent(accent: Accent) { viewModelScope.launch { repository.setAccent(accent) } }
}
