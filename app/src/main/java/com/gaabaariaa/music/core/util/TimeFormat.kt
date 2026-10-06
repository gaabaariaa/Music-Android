package com.gaabaariaa.music.core.util

import java.util.Locale

fun formatDuration(ms: Long, locale: Locale = Locale.getDefault()): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    return String.format(locale, "%d:%02d", totalSeconds / 60, totalSeconds % 60)
}
