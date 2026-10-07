package com.gaabaariaa.music.core.util

import com.gaabaariaa.music.domain.model.Song
import kotlin.math.abs

private fun normalize(value: String): String =
    value.trim().lowercase().replace(Regex("\\s+"), " ")

/**
 * Groups songs that are probably the same recording:
 *  - same title and artist with durations within 2 seconds, or
 *  - identical file size and duration (a plain copy under another name).
 * Either rule links songs; linked songs end up in one group of at least two.
 */
fun findDuplicateGroups(songs: List<Song>): List<List<Song>> {
    if (songs.size < 2) return emptyList()
    val parent = IntArray(songs.size) { it }
    fun find(i: Int): Int {
        var x = i
        while (parent[x] != x) {
            parent[x] = parent[parent[x]]
            x = parent[x]
        }
        return x
    }
    fun union(a: Int, b: Int) {
        parent[find(a)] = find(b)
    }

    songs.indices
        .filter { songs[it].title.isNotBlank() }
        .groupBy { normalize(songs[it].title) + "|" + normalize(songs[it].artist) }
        .values
        .forEach { indices ->
            val sorted = indices.sortedBy { songs[it].durationMs }
            for (k in 1 until sorted.size) {
                if (abs(songs[sorted[k]].durationMs - songs[sorted[k - 1]].durationMs) <= 2_000L) {
                    union(sorted[k], sorted[k - 1])
                }
            }
        }

    songs.indices
        .filter { songs[it].sizeBytes > 0 && songs[it].durationMs > 0 }
        .groupBy { songs[it].sizeBytes to songs[it].durationMs }
        .values
        .forEach { indices -> indices.drop(1).forEach { union(it, indices.first()) } }

    return songs.indices
        .groupBy { find(it) }
        .values
        .filter { it.size > 1 }
        .map { group -> group.map { songs[it] }.sortedBy { it.path } }
        .sortedBy { it.first().title.lowercase() }
}

/** The line of the lyrics that contains the query, shortened for display. */
fun lyricSnippet(content: String, query: String, maxLength: Int = 80): String {
    val line = content.lineSequence()
        .map { it.replace(Regex("^(\\[[^\\]]*])+"), "").trim() }
        .firstOrNull { it.contains(query.trim(), ignoreCase = true) }
        ?: content.trim().lineSequence().first()
    return if (line.length <= maxLength) line else line.take(maxLength - 1) + "…"
}

/** Escapes % and _ so user text can be used inside a SQL LIKE pattern with ESCAPE '\'. */
fun escapeLike(value: String): String =
    value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
