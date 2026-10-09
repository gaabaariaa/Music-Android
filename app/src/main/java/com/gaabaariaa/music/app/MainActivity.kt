package com.gaabaariaa.music.app

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gaabaariaa.music.core.designsystem.MusicTheme
import com.gaabaariaa.music.core.designsystem.isDarkTheme
import com.gaabaariaa.music.core.util.withLanguage
import com.gaabaariaa.music.data.settings.readLanguageBlocking
import com.gaabaariaa.music.domain.model.AppLanguage
import com.gaabaariaa.music.feature.settings.SettingsViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val settingsViewModel: SettingsViewModel by viewModels()

    /** The language this Activity was built with; a different saved language means it must restart. */
    private var appliedLanguage = AppLanguage.SYSTEM

    override fun attachBaseContext(newBase: Context) {
        appliedLanguage = readLanguageBlocking(newBase)
        super.attachBaseContext(newBase.withLanguage(appliedLanguage))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
            val dark = isDarkTheme(settings)
            LaunchedEffect(settings.language) {
                if (settings.language != appliedLanguage) recreate()
            }
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                    else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                    navigationBarStyle = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                    else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                )
                onDispose { }
            }
            MusicTheme(settings) { MusicApp() }
        }
    }
}
