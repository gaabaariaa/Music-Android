package com.gaabaariaa.music.feature.library

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gaabaariaa.music.domain.repository.PersonalRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What any song row or the player needs to show and change favorites without extra plumbing. */
class FavoritesState(val ids: Set<Long>, val toggle: (Long) -> Unit)

val LocalFavorites = staticCompositionLocalOf { FavoritesState(emptySet()) {} }

@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val repository: PersonalRepository
) : ViewModel() {
    val ids: StateFlow<Set<Long>> = repository.observeFavoriteIds()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    fun toggle(songId: Long) {
        viewModelScope.launch { repository.toggleFavorite(songId) }
    }
}
