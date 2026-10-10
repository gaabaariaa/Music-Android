package com.gaabaariaa.music.core.util

import com.gaabaariaa.music.domain.model.SmartRule
import com.gaabaariaa.music.domain.model.SmartRuleType
import com.gaabaariaa.music.domain.model.Song

private const val DAY_SECONDS = 24L * 60 * 60
private val persianScript = Regex("[\\u0600-\\u06FF\\u0750-\\u077F\\uFB50-\\uFDFF\\uFE70-\\uFEFF]")
private val persianGenres = listOf("persian", "farsi", "iranian")
private val losslessTypes = listOf("flac", "wav", "alac", "aiff", "ape")

private fun Song.isPersian(): Boolean =
    persianScript.containsMatchIn(title + artist + album) ||
        persianGenres.any { genre.contains(it, ignoreCase = true) }

private fun Song.isHighQuality(): Boolean =
    losslessTypes.any { mimeType.contains(it, ignoreCase = true) } || bitrate >= 256_000

/**
 * The songs a smart playlist currently contains.
 * [plays] maps song id to its play count; [nowSeconds] is the current time in seconds since the epoch.
 */
fun evaluateSmartRule(
    rule: SmartRule,
    songs: List<Song>,
    plays: Map<Long, Int>,
    favorites: Set<Long>,
    nowSeconds: Long
): List<Song> {
    val byTitle = compareBy<Song, String>(String.CASE_INSENSITIVE_ORDER) { it.title }
    return when (rule.type) {
        SmartRuleType.RECENTLY_ADDED ->
            songs.filter { it.dateAdded >= nowSeconds - 30 * DAY_SECONDS }
                .sortedByDescending { it.dateAdded }.take(200)
        SmartRuleType.MOST_PLAYED ->
            songs.filter { (plays[it.id] ?: 0) > 0 }
                .sortedWith(compareByDescending<Song> { plays[it.id] ?: 0 }.then(byTitle)).take(50)
        SmartRuleType.NEVER_PLAYED -> songs.filter { (plays[it.id] ?: 0) == 0 }.sortedWith(byTitle)
        SmartRuleType.FAVORITES -> songs.filter { it.id in favorites }.sortedWith(byTitle)
        SmartRuleType.GENRE -> {
            val wanted = rule.param.trim()
            if (wanted.isEmpty()) emptyList()
            else songs.filter { it.genre.contains(wanted, ignoreCase = true) }.sortedWith(byTitle)
        }
        SmartRuleType.PERSIAN -> songs.filter { it.isPersian() }.sortedWith(byTitle)
        SmartRuleType.HIGH_QUALITY -> songs.filter { it.isHighQuality() }.sortedWith(byTitle)
        SmartRuleType.LONGER_THAN -> {
            val minutes = rule.param.trim().toIntOrNull()?.coerceAtLeast(1) ?: 5
            songs.filter { it.durationMs >= minutes * 60_000L }.sortedByDescending { it.durationMs }
        }
    }
}
