package com.gaabaariaa.music

import com.gaabaariaa.music.core.util.escapeLike
import com.gaabaariaa.music.core.util.findDuplicateGroups
import com.gaabaariaa.music.core.util.lyricSnippet
import com.gaabaariaa.music.domain.model.LibraryHealth
import com.gaabaariaa.music.domain.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InsightsTest {
    private fun song(
        id: Long,
        title: String,
        artist: String = "A",
        durationMs: Long = 200_000,
        size: Long = id * 1000,
        path: String = "/music/$id.mp3"
    ) = Song(
        id = id, title = title, artist = artist, album = "", albumArtist = "", genre = "", year = 0,
        track = 0, durationMs = durationMs, sizeBytes = size, mimeType = "", path = path
    )

    @Test
    fun groupsSameTitleAndArtistWithSimilarDuration() {
        val groups = findDuplicateGroups(
            listOf(
                song(1, "Blinding Lights", "The Weeknd", 200_000),
                song(2, "  blinding   lights ", "the weeknd", 201_500),
                song(3, "Blinding Lights", "The Weeknd", 260_000), // different recording (long edit)
                song(4, "Other")
            )
        )
        assertEquals(1, groups.size)
        assertEquals(setOf(1L, 2L), groups.single().map { it.id }.toSet())
    }

    @Test
    fun groupsIdenticalFilesWithDifferentNames() {
        val groups = findDuplicateGroups(
            listOf(
                song(1, "x", artist = "", size = 5_000_000, durationMs = 180_000),
                song(2, "y", artist = "", size = 5_000_000, durationMs = 180_000),
                song(3, "z", artist = "", size = 4_000_000, durationMs = 180_000)
            )
        )
        assertEquals(listOf(setOf(1L, 2L)), groups.map { g -> g.map { it.id }.toSet() })
    }

    @Test
    fun noDuplicatesInAnEmptyOrUniqueLibrary() {
        assertTrue(findDuplicateGroups(emptyList()).isEmpty())
        assertTrue(findDuplicateGroups(listOf(song(1, "a"), song(2, "b"))).isEmpty())
    }

    @Test
    fun healthScoreCountsPassedChecks() {
        assertEquals(100, LibraryHealth().score)
        val health = LibraryHealth(total = 10, missingArtist = 4)
        assertEquals(90, health.score) // 40 checks, 4 failed
        val audited = LibraryHealth(total = 10, audited = 10, missingArtwork = 10, missingLyrics = 10)
        assertEquals(66, audited.score) // 60 checks, 20 failed
        val old = LibraryHealth(total = 10, missingGenre = 10, extendedInfoSupported = false)
        assertEquals(100, old.score) // genre is not checked without Android 11
    }

    @Test
    fun escapesLikeWildcards() {
        assertEquals("100\\% \\_ok\\\\", escapeLike("100% _ok\\"))
    }

    @Test
    fun snippetPicksMatchingLineAndStripsTimestamps() {
        val lyrics = "[00:01.00]first line\n[00:05.00]I see the light\n[00:09.00]last"
        assertEquals("I see the light", lyricSnippet(lyrics, "LIGHT"))
        assertEquals("first line", lyricSnippet(lyrics, "nothing here"))
        assertEquals(10, lyricSnippet("x".repeat(200), "x", maxLength = 10).length)
    }
}

class SmartPlaylistTest {
    private fun song(
        id: Long, title: String = "t$id", artist: String = "", genre: String = "", durationMs: Long = 200_000,
        dateAdded: Long = 0, mime: String = "audio/mpeg", bitrate: Int = 0
    ) = com.gaabaariaa.music.domain.model.Song(
        id = id, title = title, artist = artist, album = "", albumArtist = "", genre = genre, year = 0, track = 0,
        durationMs = durationMs, sizeBytes = 1, mimeType = mime, path = "", dateAdded = dateAdded, bitrate = bitrate
    )

    private fun rule(type: com.gaabaariaa.music.domain.model.SmartRuleType, param: String = "") =
        com.gaabaariaa.music.domain.model.SmartRule(type, param)

    private fun eval(
        r: com.gaabaariaa.music.domain.model.SmartRule,
        songs: List<com.gaabaariaa.music.domain.model.Song>,
        plays: Map<Long, Int> = emptyMap(),
        favorites: Set<Long> = emptySet(),
        now: Long = 100L * 24 * 3600
    ) = com.gaabaariaa.music.core.util.evaluateSmartRule(r, songs, plays, favorites, now).map { it.id }

    @Test
    fun recentlyAddedUsesThirtyDays() {
        val now = 100L * 24 * 3600
        val songs = listOf(song(1, dateAdded = now - 5 * 24 * 3600), song(2, dateAdded = now - 40 * 24 * 3600), song(3, dateAdded = now - 1))
        assertEquals(listOf(3L, 1L), eval(rule(com.gaabaariaa.music.domain.model.SmartRuleType.RECENTLY_ADDED), songs, now = now))
    }

    @Test
    fun playCountRules() {
        val songs = listOf(song(1), song(2), song(3))
        val plays = mapOf(1L to 2, 3L to 9)
        assertEquals(listOf(3L, 1L), eval(rule(com.gaabaariaa.music.domain.model.SmartRuleType.MOST_PLAYED), songs, plays))
        assertEquals(listOf(2L), eval(rule(com.gaabaariaa.music.domain.model.SmartRuleType.NEVER_PLAYED), songs, plays))
        assertEquals(listOf(1L, 3L), eval(rule(com.gaabaariaa.music.domain.model.SmartRuleType.FAVORITES), songs, favorites = setOf(3L, 1L)))
    }

    @Test
    fun genreDurationAndQualityRules() {
        val songs = listOf(
            song(1, genre = "Alternative Rock", durationMs = 400_000),
            song(2, genre = "Pop", durationMs = 310_000, mime = "audio/flac"),
            song(3, genre = "Pop", durationMs = 100_000, bitrate = 320_000)
        )
        assertEquals(listOf(1L), eval(rule(com.gaabaariaa.music.domain.model.SmartRuleType.GENRE, " rock "), songs))
        assertTrue(eval(rule(com.gaabaariaa.music.domain.model.SmartRuleType.GENRE, ""), songs).isEmpty())
        assertEquals(listOf(1L, 2L), eval(rule(com.gaabaariaa.music.domain.model.SmartRuleType.LONGER_THAN, "5"), songs))
        assertEquals(listOf(2L, 3L), eval(rule(com.gaabaariaa.music.domain.model.SmartRuleType.HIGH_QUALITY), songs))
    }

    @Test
    fun persianRuleDetectsScriptOrGenre() {
        val songs = listOf(song(1, title = "سلام"), song(2, title = "Hello"), song(3, title = "x", genre = "Persian Pop"))
        assertEquals(listOf(3L, 1L).sorted(), eval(rule(com.gaabaariaa.music.domain.model.SmartRuleType.PERSIAN), songs).sorted())
    }

    @Test
    fun smartRulesSurviveEncoding() {
        val original = rule(com.gaabaariaa.music.domain.model.SmartRuleType.GENRE, "hip:hop")
        assertEquals(original, com.gaabaariaa.music.domain.model.decodeSmartRule(original.encode()))
        assertTrue(com.gaabaariaa.music.domain.model.decodeSmartRule("") == null)
        assertTrue(com.gaabaariaa.music.domain.model.decodeSmartRule("NOPE:1") == null)
    }
}
