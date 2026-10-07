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
