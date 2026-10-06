package com.gaabaariaa.music.core.designsystem

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.gaabaariaa.music.domain.model.Accent
import com.gaabaariaa.music.domain.model.AppSettings
import com.gaabaariaa.music.domain.model.ThemeMode

val DynamicColorSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

@Composable
fun isDarkTheme(settings: AppSettings): Boolean = when (settings.themeMode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun MusicTheme(settings: AppSettings, content: @Composable () -> Unit) {
    val dark = isDarkTheme(settings)
    val context = LocalContext.current
    var scheme = when {
        settings.dynamicColor && DynamicColorSupported ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        else -> accentColorScheme(settings.accent, dark)
    }
    if (dark && settings.amoled) scheme = scheme.toAmoled()
    MaterialTheme(colorScheme = scheme, typography = Typography(), content = content)
}

private class Palette(
    val lightPrimary: Long,
    val lightContainer: Long,
    val lightOnContainer: Long,
    val darkPrimary: Long,
    val darkOnPrimary: Long,
    val darkContainer: Long,
    val darkOnContainer: Long
)

private fun palette(accent: Accent): Palette = when (accent) {
    Accent.PURPLE -> Palette(0x6750A4, 0xEADDFF, 0x21005D, 0xD0BCFF, 0x381E72, 0x4F378B, 0xEADDFF)
    Accent.BLUE -> Palette(0x0061A4, 0xD1E4FF, 0x001D36, 0x9ECAFF, 0x003258, 0x00497D, 0xD1E4FF)
    Accent.GREEN -> Palette(0x006D3B, 0x94F7B5, 0x002110, 0x78DB9A, 0x00391E, 0x00522D, 0x94F7B5)
    Accent.ORANGE -> Palette(0x8B5000, 0xFFDCBE, 0x2C1600, 0xFFB870, 0x4A2800, 0x693C00, 0xFFDCBE)
    Accent.RED -> Palette(0xBB1614, 0xFFDAD5, 0x410001, 0xFFB4A9, 0x690003, 0x93000A, 0xFFDAD5)
}

private fun argb(rgb: Long) = Color(0xFF000000L or rgb)

/** Color used for the accent swatches in Settings. */
fun accentSwatch(accent: Accent, dark: Boolean): Color =
    palette(accent).let { if (dark) argb(it.darkPrimary) else argb(it.lightPrimary) }

fun accentColorScheme(accent: Accent, dark: Boolean): ColorScheme {
    val p = palette(accent)
    return if (dark) {
        darkColorScheme(
            primary = argb(p.darkPrimary),
            onPrimary = argb(p.darkOnPrimary),
            primaryContainer = argb(p.darkContainer),
            onPrimaryContainer = argb(p.darkOnContainer),
            secondary = argb(p.darkPrimary),
            secondaryContainer = argb(p.darkContainer),
            onSecondaryContainer = argb(p.darkOnContainer)
        )
    } else {
        lightColorScheme(
            primary = argb(p.lightPrimary),
            onPrimary = Color.White,
            primaryContainer = argb(p.lightContainer),
            onPrimaryContainer = argb(p.lightOnContainer),
            secondary = argb(p.lightPrimary),
            secondaryContainer = argb(p.lightContainer),
            onSecondaryContainer = argb(p.lightOnContainer)
        )
    }
}

private fun ColorScheme.toAmoled(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0A0A0A),
    surfaceContainer = Color(0xFF121212),
    surfaceContainerHigh = Color(0xFF1A1A1A),
    surfaceContainerHighest = Color(0xFF222222)
)
