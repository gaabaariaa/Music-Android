package com.gaabaariaa.music

import com.gaabaariaa.music.data.sources.ArchiveItem
import com.gaabaariaa.music.data.sources.parseArchiveMetadata
import com.gaabaariaa.music.data.sources.parseArchiveSearch
import com.gaabaariaa.music.data.sources.parseCcMixter
import com.gaabaariaa.music.data.sources.parseCommons
import com.gaabaariaa.music.data.sources.parseFeed
import com.gaabaariaa.music.data.sources.parseJamendo
import com.gaabaariaa.music.data.sources.parseLengthSeconds
import com.gaabaariaa.music.data.sources.parseOpenverse
import com.gaabaariaa.music.data.sources.searchWords
import com.gaabaariaa.music.data.sources.shortLicense
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceParsersTest {
    @Test
    fun shortensCreativeCommonsUrls() {
        assertEquals("CC BY-SA 4.0", shortLicense("https://creativecommons.org/licenses/by-sa/4.0/"))
        assertEquals("CC BY 3.0", shortLicense("http://creativecommons.org/licenses/by/3.0/us/"))
        assertEquals("CC0 1.0", shortLicense("https://creativecommons.org/publicdomain/zero/1.0/"))
        assertEquals("Public domain", shortLicense("https://creativecommons.org/publicdomain/mark/1.0/"))
        assertEquals("", shortLicense(null))
    }

    @Test
    fun parsesArchiveSearchAndPrefersMp3Files() {
        val items = parseArchiveSearch(
            """{"response":{"docs":[
              {"identifier":"band-live","title":"Live Set","creator":["The Band"],"licenseurl":"https://creativecommons.org/licenses/by/4.0/"},
              {"title":"no id"}]}}"""
        )
        assertEquals(1, items.size)
        assertEquals("The Band", items[0].creator)

        val tracks = parseArchiveMetadata(
            """{"metadata":{"title":"Live Set"},"files":[
              {"name":"01 Intro.mp3","format":"VBR MP3","title":"Intro","length":"215.3"},
              {"name":"01 Intro.ogg","format":"Ogg Vorbis"},
              {"name":"cover.jpg","format":"JPEG"},
              {"name":"02 Song.mp3","format":"VBR MP3","length":"3:05"}]}""",
            items[0]
        )
        assertEquals(listOf("Intro", "02 Song"), tracks.map { it.title })
        assertEquals(215, tracks[0].durationSec)
        assertEquals(185, tracks[1].durationSec)
        assertEquals("https://archive.org/download/band-live/01%20Intro.mp3", tracks[0].downloadUrl)
        assertEquals("CC BY 4.0", tracks[0].license)
    }

    @Test
    fun fallsBackToOggWhenThereIsNoMp3() {
        val item = ArchiveItem("x", "A", "B", "")
        val tracks = parseArchiveMetadata("""{"files":[{"name":"a.ogg","format":"Ogg Vorbis"}]}""", item)
        assertEquals("ogg", tracks.single().extension)
        assertEquals(com.gaabaariaa.music.domain.model.LicenseCodes.SEE_ITEM_PAGE, tracks.single().license)
    }

    @Test
    fun parsesOpenverseResultsAndSkipsInsecureUrls() {
        val tracks = parseOpenverse(
            """{"results":[
              {"id":"1","title":"Song","creator":"Me","license":"by-sa","license_version":"4.0",
               "url":"https://cdn.example/a.mp3","filetype":"mp3","duration":125000,
               "foreign_landing_url":"https://example/a","audio_set":{"title":"Album"}},
              {"id":"2","title":"Insecure","url":"http://cdn.example/b.mp3","filetype":"mp3"},
              {"id":"3","title":"PD","license":"pdm","url":"https://cdn.example/c.ogg","filetype":"ogg"}]}"""
        )
        assertEquals(listOf("Song", "PD"), tracks.map { it.title })
        assertEquals("CC BY-SA 4.0", tracks[0].license)
        assertEquals(125, tracks[0].durationSec)
        assertEquals("Album", tracks[0].album)
        assertEquals("Public domain", tracks[1].license)
    }

    @Test
    fun parsesCommonsAudioAndStripsHtml() {
        val tracks = parseCommons(
            """{"query":{"pages":{"1":{"pageid":1,"title":"File:Piano piece.ogg","imageinfo":[
              {"url":"https://upload.wikimedia.org/a.ogg","mime":"application/ogg",
               "descriptionurl":"https://commons.wikimedia.org/wiki/File:Piano_piece.ogg",
               "extmetadata":{"Artist":{"value":"<a href=\"x\">Jane &amp; Co</a>"},
                              "LicenseShortName":{"value":"CC BY-SA 3.0"}}}]},
              "2":{"title":"File:Pic.jpg","imageinfo":[{"url":"https://x/p.jpg","mime":"image/jpeg"}]}}}}"""
        )
        assertEquals(1, tracks.size)
        assertEquals("Piano piece", tracks[0].title)
        assertEquals("Jane & Co", tracks[0].artist)
        assertEquals("CC BY-SA 3.0", tracks[0].license)
    }

    @Test
    fun parsesCcMixterUploads() {
        val tracks = parseCcMixter(
            """[{"upload_id":7,"upload_name":"Remix","user_name":"dj","license_name":"Attribution (3.0)",
                 "file_page_url":"https://ccmixter.org/files/dj/7",
                 "files":[{"download_url":"https://ccmixter.org/content/dj/a.zip","file_format_info":{"default-ext":"zip"}},
                          {"download_url":"https://ccmixter.org/content/dj/a.mp3","file_format_info":{"default-ext":"mp3"}}]}]"""
        )
        assertEquals("https://ccmixter.org/content/dj/a.mp3", tracks.single().downloadUrl)
        assertEquals("dj", tracks.single().artist)
    }

    @Test
    fun parsesJamendoOnlyWhenDownloadIsAllowed() {
        val tracks = parseJamendo(
            """{"results":[
              {"id":"1","name":"Free","artist_name":"A","album_name":"B","duration":200,
               "audiodownload":"https://prod-1.storage.jamendo.com/download/track/1/mp32/",
               "audiodownload_allowed":true,"license_ccurl":"https://creativecommons.org/licenses/by-nc/3.0/"},
              {"id":"2","name":"Blocked","audiodownload":"https://x/y","audiodownload_allowed":false}]}"""
        )
        assertEquals(listOf("Free"), tracks.map { it.title })
        assertEquals("CC BY-NC 3.0", tracks[0].license)
    }

    @Test
    fun parsesFeedEnclosures() {
        val xml = """<?xml version="1.0"?><rss xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd"><channel>
            <title>Netlabel</title><itunes:author>Label</itunes:author>
            <item><title>Ep 1</title><guid>g1</guid>
              <enclosure url="https://cdn.example/ep1.mp3" type="audio/mpeg" length="1"/></item>
            <item><title>Video</title><enclosure url="https://cdn.example/v.mp4" type="video/mp4"/></item>
            <item><title>Plain</title><enclosure url="http://cdn.example/x.mp3" type="audio/mpeg"/></item>
            </channel></rss>""".trimIndent()
        val tracks = parseFeed(xml.toByteArray())
        assertEquals(listOf("Ep 1"), tracks.map { it.title })
        assertEquals("Label", tracks[0].artist)
        assertEquals("Netlabel", tracks[0].album)
    }

    @Test
    fun cleansSearchTextAndParsesLengths() {
        assertEquals("AC DC live 79", searchWords("AC/DC \"live\" '79 ("))
        assertEquals(185, parseLengthSeconds("3:05"))
        assertEquals(3725, parseLengthSeconds("1:02:05"))
        assertTrue(parseLengthSeconds("") == null)
    }
}

class SizesTest {
    @Test
    fun formatsByteSizes() {
        val us = java.util.Locale.US
        assertEquals("512 B", com.gaabaariaa.music.core.util.formatBytes(512, us))
        assertEquals("2.5 MB", com.gaabaariaa.music.core.util.formatBytes(2_621_440, us))
        assertEquals("1.50 GB", com.gaabaariaa.music.core.util.formatBytes(1_610_612_736, us))
    }

    @Test
    fun estimatesRemainingTime() {
        assertEquals(10L, com.gaabaariaa.music.core.util.remainingSeconds(0, 1000, 100))
        assertTrue(com.gaabaariaa.music.core.util.remainingSeconds(0, -1, 100) == null)
        assertTrue(com.gaabaariaa.music.core.util.remainingSeconds(0, 1000, 0) == null)
    }
}
