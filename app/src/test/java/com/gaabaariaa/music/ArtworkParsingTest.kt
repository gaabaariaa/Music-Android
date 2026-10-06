package com.gaabaariaa.music

import com.gaabaariaa.music.data.artwork.buildQuery
import com.gaabaariaa.music.data.artwork.imageMimeType
import com.gaabaariaa.music.data.artwork.luceneQuote
import com.gaabaariaa.music.data.artwork.parseReleases
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArtworkParsingTest {
    private val sample = """
        {"releases":[
          {"id":"a1","title":"After Hours","date":"2020-03-20",
           "artist-credit":[{"name":"The Weeknd","joinphrase":""}]},
          {"id":"a2","title":"After Hours","date":"2020-03-20",
           "artist-credit":[{"name":"The Weeknd","joinphrase":""}]},
          {"id":"b1","title":"Duets","date":"",
           "artist-credit":[{"name":"A","joinphrase":" & "},{"name":"B","joinphrase":""}]},
          {"title":"No id"}
        ]}
    """.trimIndent()

    @Test
    fun parsesAndDeduplicatesReleases() {
        val result = parseReleases(sample)
        assertEquals(listOf("a1", "b1"), result.map { it.releaseId })
        assertEquals("2020", result[0].year)
        assertEquals("A & B", result[1].artist)
    }

    @Test
    fun emptyResponseGivesNoCandidates() {
        assertEquals(0, parseReleases("{}").size)
    }

    @Test
    fun buildsQueriesAndEscapesQuotes() {
        assertEquals("release:\"After Hours\" AND artist:\"The Weeknd\"", buildQuery("x", "The Weeknd", "After Hours"))
        assertEquals("recording:\"Blinding Lights\"", buildQuery("Blinding Lights", "", ""))
        assertNull(buildQuery("", "Artist", ""))
        assertEquals("\"say \\\"hi\\\"\"", luceneQuote("say \"hi\""))
    }

    @Test
    fun detectsImageTypesByMagicBytes() {
        assertEquals("image/jpeg", imageMimeType(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())))
        assertEquals(
            "image/png",
            imageMimeType(byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 0, 0, 0, 0, 0))
        )
        assertNull(imageMimeType(byteArrayOf(1, 2, 3, 4, 5)))
    }
}
