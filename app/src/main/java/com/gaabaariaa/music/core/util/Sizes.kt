package com.gaabaariaa.music.core.util

import java.util.Locale

fun formatBytes(bytes: Long, locale: Locale = Locale.getDefault()): String = when {
    bytes < 1024 -> String.format(locale, "%d B", bytes.coerceAtLeast(0))
    bytes < 1024 * 1024 -> String.format(locale, "%.0f KB", bytes / 1024.0)
    bytes < 1024L * 1024 * 1024 -> String.format(locale, "%.1f MB", bytes / 1_048_576.0)
    else -> String.format(locale, "%.2f GB", bytes / 1_073_741_824.0)
}

/** Seconds left at the current speed, or null when it cannot be estimated. */
fun remainingSeconds(downloaded: Long, total: Long, speedBps: Long): Long? {
    if (total <= 0 || speedBps <= 0 || downloaded >= total) return null
    return (total - downloaded) / speedBps
}
