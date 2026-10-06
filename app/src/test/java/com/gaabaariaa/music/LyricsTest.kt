package com.gaabaariaa.music

import com.gaabaariaa.music.core.util.currentLineIndex
import com.gaabaariaa.music.core.util.decodeLyricsBytes
import com.gaabaariaa.music.core.util.parseLyrics
import com.gaabaariaa.music.data.lyrics.chooseBest
import com.gaabaariaa.music.data.lyrics.parseLrclibArray
import com.gaabaariaa.music.data.lyrics.parseLrclibObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsTest {
    @Test
    fun parsesSyncedLyricsWithMetadataAndMultipleStamps() {
        val lrc = """
            [ar:Someone]
            [ti:Song]
            [00:01.50]First
            [00:05.5][01:00.00]Chorus
            [00:09]Last
        """.trimIndent()
        val parsed = parseLyrics(lrc)
        assertTrue(parsed.synced)
        assertEquals(listOf(1500L, 5500L, 9000L, 60000L), parsed.lines.map { it.timeMs })
        assertEquals(listOf("First", "Chorus", "Last", "Chorus"), parsed.lines.map { it.text })
    }

    @Test
    fun appliesOffsetTag() {
        val parsed = parseLyrics("[offset:+500]\n[00:02.00]Hi")
        assertEquals(1500L, parsed.lines.single().timeMs)
    }

    @Test
    fun plainTextIsNotSynced() {
        val parsed = parseLyrics("\nline one\n\nline two\n")
        assertFalse(parsed.synced)
        assertEquals(listOf("line one", "", "line two"), parsed.lines.map { it.text })
    }

    @Test
    fun findsCurrentLine() {
        val lines = parseLyrics("[00:01.00]a\n[00:05.00]b\n[00:09.00]c").lines
        assertEquals(-1, currentLineIndex(lines, 500))
        assertEquals(0, currentLineIndex(lines, 1000))
        assertEquals(1, currentLineIndex(lines, 8999))
        assertEquals(2, currentLineIndex(lines, 60_000))
    }

    @Test
    fun decodesUtf8WithBomAndLegacyPersian() {
        val persian = "سلام"
        val utf8 = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + persian.toByteArray(Charsets.UTF_8)
        assertEquals(persian, decodeLyricsBytes(utf8))
        val cp1256 = byteArrayOf(0xD3.toByte(), 0xE1.toByte(), 0xC7.toByte(), 0xE3.toByte())
        assertEquals(persian, decodeLyricsBytes(cp1256))
    }

    @Test
    fun parsesLrclibAndPicksBestDuration() {
        val one = parseLrclibObject("""{"syncedLyrics":null,"plainLyrics":"hello","duration":200.0,"instrumental":false}""")
        assertNull(one.synced)
        assertEquals("hello", one.plain)

        val hits = parseLrclibArray(
            """[
              {"plainLyrics":"wrong length","syncedLyrics":"[00:01.00]x","duration":300},
              {"plainLyrics":"p","syncedLyrics":"[00:01.00]right","duration":201},
              {"instrumental":true,"duration":200}
            ]"""
        )
        assertEquals("[00:01.00]right", chooseBest(hits, 200)?.synced)
        assertNull(chooseBest(emptyList(), 200))
    }
}
