package com.gaabaariaa.music.data.sources

import com.gaabaariaa.music.core.di.IoDispatcher
import com.gaabaariaa.music.data.net.USER_AGENT
import com.gaabaariaa.music.data.net.httpGet
import com.gaabaariaa.music.domain.model.DownloadableTrack
import com.gaabaariaa.music.domain.model.SourceError
import com.gaabaariaa.music.domain.model.SourceInfo
import com.gaabaariaa.music.domain.model.SourceInput
import com.gaabaariaa.music.domain.model.SourceSearchOutcome
import com.gaabaariaa.music.domain.repository.MusicSourceRepository
import com.gaabaariaa.music.domain.repository.SettingsRepository
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** Raised by a provider; the repository turns it into a [SourceError]. */
internal class SourceException(val error: SourceError) : Exception()

private fun getJson(url: String, maxBytes: Int = 4 * 1024 * 1024): String {
    val response = httpGet(url, "application/json", maxBytes)
    val body = response.body
    if (response.code != 200 || body == null) throw SourceException(SourceError.SERVER)
    return String(body, Charsets.UTF_8)
}

private suspend fun archive(query: String): List<DownloadableTrack> = coroutineScope {
    val words = searchWords(query)
    if (words.isEmpty()) return@coroutineScope emptyList()
    // Only items with a Creative Commons / public-domain license, or from collections whose uploads are
    // openly licensed or permitted by the artists.
    val q = "($words) AND mediatype:audio AND (licenseurl:* OR collection:(netlabels OR etree OR " +
        "librivoxaudio OR opensource_audio))"
    val url = "https://archive.org/advancedsearch.php?q=" + q.urlEncode() +
        "&fl%5B%5D=identifier&fl%5B%5D=title&fl%5B%5D=creator&fl%5B%5D=licenseurl" +
        "&rows=8&page=1&output=json&sort%5B%5D=downloads%20desc"
    val items = parseArchiveSearch(getJson(url))
    items.map { item ->
        async {
            try {
                parseArchiveMetadata(getJson("https://archive.org/metadata/" + item.identifier, 8 * 1024 * 1024), item)
            } catch (e: Exception) {
                emptyList()
            }
        }
    }.awaitAll().flatten().take(40)
}

private fun openverse(query: String): List<DownloadableTrack> {
    val words = searchWords(query)
    if (words.isEmpty()) return emptyList()
    return parseOpenverse(getJson("https://api.openverse.org/v1/audio/?page_size=20&q=" + words.urlEncode()))
}

private fun commons(query: String): List<DownloadableTrack> {
    val words = searchWords(query)
    if (words.isEmpty()) return emptyList()
    val url = "https://commons.wikimedia.org/w/api.php?action=query&format=json&generator=search" +
        "&gsrnamespace=6&gsrlimit=20&gsrsearch=" + (words + " filetype:audio").urlEncode() +
        "&prop=imageinfo&iiprop=" + "url|mime|extmetadata".urlEncode()
    return parseCommons(getJson(url))
}

private fun ccMixter(query: String): List<DownloadableTrack> {
    val words = searchWords(query)
    if (words.isEmpty()) return emptyList()
    return parseCcMixter(getJson("https://ccmixter.org/api/query?f=json&limit=20&search_type=all&search=" + words.urlEncode()))
}

private fun jamendo(query: String, clientId: String): List<DownloadableTrack> {
    if (clientId.isBlank()) throw SourceException(SourceError.NOT_CONFIGURED)
    val words = searchWords(query)
    if (words.isEmpty()) return emptyList()
    return parseJamendo(
        getJson(
            "https://api.jamendo.com/v3.0/tracks/?format=json&limit=20&audiodownload_allowed=true" +
                "&client_id=" + clientId.urlEncode() + "&search=" + words.urlEncode()
        )
    )
}

/** A single https link to an audio file, supplied by the user. */
private fun directLink(input: String): List<DownloadableTrack> {
    val link = input.trim()
    if (!link.startsWith("https://")) throw SourceException(SourceError.INVALID_INPUT)
    val connection = try {
        URL(link).openConnection() as HttpURLConnection
    } catch (e: Exception) {
        throw SourceException(SourceError.INVALID_INPUT)
    }
    try {
        connection.connectTimeout = 10_000
        connection.readTimeout = 15_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", USER_AGENT)
        val code = connection.responseCode
        if (code !in 200..299) throw SourceException(SourceError.INVALID_INPUT)
        val type = connection.contentType.orEmpty().lowercase()
        val ext = extensionOf(connection.url.toString()).ifBlank { extensionOf(link) }
        val looksAudio = type.startsWith("audio/") || type == "application/ogg" ||
            ((type.contains("octet-stream") || type.isEmpty()) && ext in AUDIO_EXTENSIONS)
        if (!looksAudio) throw SourceException(SourceError.INVALID_INPUT)
        val name = URLDecoder.decode(link.substringBefore('?').substringAfterLast('/'), "UTF-8")
        return listOf(
            DownloadableTrack(
                providerId = "direct",
                remoteId = link,
                title = name.substringBeforeLast('.').ifBlank { "Untitled" },
                artist = "",
                album = "",
                durationSec = null,
                license = "Link provided by you",
                pageUrl = link,
                downloadUrl = link,
                coverUrl = "",
                extension = ext.ifBlank { "mp3" }
            )
        )
    } finally {
        connection.disconnect()
    }
}

private fun feed(input: String): List<DownloadableTrack> {
    val link = input.trim()
    if (!link.startsWith("https://")) throw SourceException(SourceError.INVALID_INPUT)
    val response = httpGet(link, "application/rss+xml, application/xml, text/xml", 3 * 1024 * 1024)
    val body = response.body
    if (response.code != 200 || body == null) throw SourceException(SourceError.INVALID_INPUT)
    return try {
        parseFeed(body)
    } catch (e: Exception) {
        throw SourceException(SourceError.INVALID_INPUT)
    }
}

@Singleton
class MusicSourceRepositoryImpl @Inject constructor(
    private val settings: SettingsRepository,
    @IoDispatcher private val io: CoroutineDispatcher
) : MusicSourceRepository {

    override val sources: List<SourceInfo> = listOf(
        SourceInfo("archive", "Internet Archive", SourceInput.SEARCH),
        SourceInfo("openverse", "Openverse", SourceInput.SEARCH),
        SourceInfo("commons", "Wikimedia Commons", SourceInput.SEARCH),
        SourceInfo("ccmixter", "ccMixter", SourceInput.SEARCH),
        SourceInfo("jamendo", "Jamendo", SourceInput.SEARCH),
        SourceInfo("direct", "Direct link", SourceInput.URL),
        SourceInfo("feed", "RSS / podcast feed", SourceInput.URL)
    )

    override suspend fun search(sourceId: String, query: String): SourceSearchOutcome = withContext(io) {
        val ids = if (sourceId == "all") sources.filter { it.input == SourceInput.SEARCH }.map { it.id }
        else listOf(sourceId)
        val clientId = settings.settings.first().jamendoClientId
        coroutineScope {
            val results = ids.map { id ->
                async {
                    id to runCatching { searchOne(id, query, clientId) }
                }
            }.awaitAll()
            val tracks = ArrayList<DownloadableTrack>()
            val errors = LinkedHashMap<String, SourceError>()
            for ((id, result) in results) {
                result.onSuccess { tracks += it }.onFailure { e ->
                    // In "all" mode a source that needs a key is simply left out.
                    val error = when (e) {
                        is SourceException -> e.error
                        is IOException -> SourceError.OFFLINE
                        else -> SourceError.SERVER
                    }
                    if (!(sourceId == "all" && error == SourceError.NOT_CONFIGURED)) errors[id] = error
                }
            }
            SourceSearchOutcome(tracks, errors)
        }
    }

    private suspend fun searchOne(id: String, query: String, clientId: String): List<DownloadableTrack> =
        when (id) {
            "archive" -> archive(query)
            "openverse" -> openverse(query)
            "commons" -> commons(query)
            "ccmixter" -> ccMixter(query)
            "jamendo" -> jamendo(query, clientId)
            "direct" -> directLink(query)
            "feed" -> feed(query)
            else -> emptyList()
        }
}
