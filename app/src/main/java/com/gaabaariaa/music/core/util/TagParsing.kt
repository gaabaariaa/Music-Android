package com.gaabaariaa.music.core.util

/** "2020", "2020-05-01" -> 2020; anything else -> 0. */
fun parseYear(value: String): Int = value.trim().take(4).toIntOrNull() ?: 0

/** "5", "5/12" -> 5; anything else -> 0. */
fun parseTrackNumber(value: String): Int = value.trim().substringBefore('/').toIntOrNull() ?: 0
