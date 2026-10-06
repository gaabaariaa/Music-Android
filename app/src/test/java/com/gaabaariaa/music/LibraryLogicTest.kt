package com.gaabaariaa.music

import com.gaabaariaa.music.core.util.formatDuration
import com.gaabaariaa.music.domain.model.Song
import com.gaabaariaa.music.domain.model.SongSort
import com.gaabaariaa.music.feature.library.DetailType
import com.gaabaariaa.music.feature.library.decodeDetailValue
import com.gaabaariaa.music.feature.library.detailRoute
import com.gaabaariaa.music.feature.library.filterAndSort
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryLogicTest {
    private fun song(
        id: Long,
        title: String,
        artist: String = "",
        album: String = "",
        durationMs: Long = 1000,
        dateAdded: Long = 0,
        track: Int = 0
    ) = Song(
        id = id, title = title, artist = artist, album = album, albumArtist = "", genre = "",
        year = 0, track = track, durationMs = durationMs, sizeBytes = 0, mimeType = "", path = "",
        dateAdded = dateAdded, folder = ""
    )

    private val library = listOf(
        song(1, "Blinding Lights", "The Weeknd", "After Hours", durationMs = 200_000, dateAdded = 10),
        song(2, "alpha", "Zed", "Beta", durationMs = 400_000, dateAdded = 30),
        song(3, "Charlie", "Abba", "Gold", durationMs = 100_000, dateAdded = 20)
    )

    @Test
    fun searchIsCaseInsensitiveAndMatchesArtist() {
        val result = filterAndSort(library, "weeknd", SongSort.TITLE)
        assertEquals(listOf(1L), result.map { it.id })
    }

    @Test
    fun blankQueryKeepsEverything() {
        assertEquals(3, filterAndSort(library, "  ", SongSort.TITLE).size)
    }

    @Test
    fun sortsByTitleIgnoringCase() {
        assertEquals(listOf(2L, 1L, 3L), filterAndSort(library, "", SongSort.TITLE).map { it.id })
    }

    @Test
    fun sortsByDateAddedNewestFirst() {
        assertEquals(listOf(2L, 3L, 1L), filterAndSort(library, "", SongSort.DATE_ADDED).map { it.id })
    }

    @Test
    fun sortsByDurationLongestFirst() {
        assertEquals(listOf(2L, 1L, 3L), filterAndSort(library, "", SongSort.DURATION).map { it.id })
    }

    @Test
    fun detailRouteRoundTripsAwkwardNames() {
        for (name in listOf("", "AC/DC", "Björk – ۱", "a b?c=d&e")) {
            val segment = detailRoute(DetailType.ARTIST, name).substringAfterLast('/')
            assertEquals(name, decodeDetailValue(segment))
        }
    }

    @Test
    fun formatsDurations() {
        assertEquals("3:05", formatDuration(185_000, Locale.US))
        assertEquals("0:00", formatDuration(-5, Locale.US))
    }
}
