package com.gaabaariaa.music.data.sources

import com.gaabaariaa.music.domain.model.DownloadableTrack
import com.gaabaariaa.music.domain.model.LicenseCodes
import java.io.ByteArrayInputStream
import java.net.URLEncoder
import javax.xml.parsers.DocumentBuilderFactory
import org.json.JSONArray
import org.json.JSONObject
import org.w3c.dom.Element

internal val AUDIO_EXTENSIONS = setOf("mp3", "flac", "ogg", "oga", "opus", "m4a", "aac", "wav")

internal fun extensionOf(nameOrUrl: String): String =
    nameOrUrl.substringBefore('?').substringBefore('#').substringAfterLast('/')
        .substringAfterLast('.', "").lowercase()

internal fun String.urlEncode(): String = URLEncoder.encode(this, "UTF-8").replace("+", "%20")

/** "https://creativecommons.org/licenses/by-sa/4.0/" -> "CC BY-SA 4.0". */
internal fun shortLicense(url: String?): String {
    if (url.isNullOrBlank()) return ""
    val match = Regex("creativecommons\\.org/(licenses|publicdomain)/([a-z\\-]+)/?([0-9.]*)").find(url)
        ?: return if (url.contains("publicdomain", ignoreCase = true)) "Public domain" else ""
    val kind = match.groupValues[1]
    val code = match.groupValues[2]
    val version = match.groupValues[3].trim('.')
    return when {
        kind == "publicdomain" && code == "zero" -> "CC0 1.0"
        kind == "publicdomain" -> "Public domain"
        else -> ("CC " + code.uppercase() + " " + version).trim()
    }
}

private fun JSONObject.str(key: String): String =
    if (isNull(key)) "" else optString(key).trim()

/** Values such as "creator" are sometimes a string and sometimes an array of strings. */
private fun JSONObject.firstString(key: String): String {
    if (isNull(key)) return ""
    return when (val value = opt(key)) {
        is JSONArray -> if (value.length() > 0) value.optString(0).trim() else ""
        else -> value.toString().trim()
    }
}

internal fun stripHtml(html: String): String =
    html.replace(Regex("<[^>]*>"), "").replace("&amp;", "&").replace("&quot;", "\"")
        .replace("&#039;", "'").replace("&lt;", "<").replace("&gt;", ">").trim()

// ---------------------------------------------------------------- Internet Archive

internal class ArchiveItem(val identifier: String, val title: String, val creator: String, val licenseUrl: String)

internal fun parseArchiveSearch(json: String): List<ArchiveItem> {
    val docs = JSONObject(json).optJSONObject("response")?.optJSONArray("docs") ?: return emptyList()
    return (0 until docs.length()).mapNotNull { i ->
        val doc = docs.optJSONObject(i) ?: return@mapNotNull null
        val id = doc.str("identifier")
        if (id.isEmpty()) null
        else ArchiveItem(id, doc.firstString("title"), doc.firstString("creator"), doc.firstString("licenseurl"))
    }
}

/** Picks one file per track, preferring MP3, then Ogg Vorbis, then FLAC. */
internal fun parseArchiveMetadata(json: String, item: ArchiveItem, limit: Int = 12): List<DownloadableTrack> {
    val root = JSONObject(json)
    val files = root.optJSONArray("files") ?: return emptyList()
    val meta = root.optJSONObject("metadata")
    val album = item.title.ifBlank { meta?.firstString("title").orEmpty() }
    val creator = item.creator.ifBlank { meta?.firstString("creator").orEmpty() }
    val licenseUrl = item.licenseUrl.ifBlank { meta?.firstString("licenseurl").orEmpty() }
    val license = shortLicense(licenseUrl).ifBlank { LicenseCodes.SEE_ITEM_PAGE }

    val byFormat = listOf(setOf("VBR MP3", "MP3"), setOf("Ogg Vorbis"), setOf("Flac", "FLAC"))
    for (formats in byFormat) {
        val tracks = ArrayList<DownloadableTrack>()
        for (i in 0 until files.length()) {
            val file = files.optJSONObject(i) ?: continue
            val name = file.str("name")
            if (file.str("format") !in formats || name.isEmpty() || name.contains("/")) continue
            val ext = extensionOf(name)
            if (ext !in AUDIO_EXTENSIONS) continue
            val title = file.str("title").ifBlank { name.substringBeforeLast('.') }
            tracks += DownloadableTrack(
                providerId = "archive",
                remoteId = item.identifier + "/" + name,
                title = title,
                artist = file.str("creator").ifBlank { creator },
                album = file.str("album").ifBlank { album },
                durationSec = parseLengthSeconds(file.str("length")),
                license = license,
                pageUrl = "https://archive.org/details/" + item.identifier,
                downloadUrl = "https://archive.org/download/" + item.identifier + "/" + name.urlEncode(),
                coverUrl = "",
                extension = ext
            )
            if (tracks.size >= limit) break
        }
        if (tracks.isNotEmpty()) return tracks
    }
    return emptyList()
}

/** "215.3" or "3:35" -> seconds. */
internal fun parseLengthSeconds(value: String): Int? {
    if (value.isBlank()) return null
    if (value.contains(':')) {
        val parts = value.split(':').mapNotNull { it.toIntOrNull() }
        if (parts.size < 2) return null
        return parts.fold(0) { acc, part -> acc * 60 + part }
    }
    return value.toDoubleOrNull()?.toInt()
}

// ---------------------------------------------------------------- Openverse

internal fun parseOpenverse(json: String): List<DownloadableTrack> {
    val results = JSONObject(json).optJSONArray("results") ?: return emptyList()
    return (0 until results.length()).mapNotNull { i ->
        val item = results.optJSONObject(i) ?: return@mapNotNull null
        val url = item.str("url")
        if (!url.startsWith("https://")) return@mapNotNull null
        val ext = item.str("filetype").lowercase().ifBlank { extensionOf(url) }
        if (ext !in AUDIO_EXTENSIONS) return@mapNotNull null
        val license = item.str("license")
        val licenseText = when (license.lowercase()) {
            "" -> ""
            "cc0" -> "CC0 1.0"
            "pdm" -> "Public domain"
            else -> ("CC " + license.uppercase() + " " + item.str("license_version")).trim()
        }
        DownloadableTrack(
            providerId = "openverse",
            remoteId = item.str("id"),
            title = item.str("title"),
            artist = item.str("creator"),
            album = item.optJSONObject("audio_set")?.str("title").orEmpty(),
            durationSec = if (item.isNull("duration")) null else item.optInt("duration") / 1000,
            license = licenseText.ifBlank { LicenseCodes.SEE_SOURCE_PAGE },
            pageUrl = item.str("foreign_landing_url"),
            downloadUrl = url,
            coverUrl = item.str("thumbnail").takeIf { it.startsWith("https://") }.orEmpty(),
            extension = ext
        )
    }
}

// ---------------------------------------------------------------- Wikimedia Commons

internal fun parseCommons(json: String): List<DownloadableTrack> {
    val pages = JSONObject(json).optJSONObject("query")?.optJSONObject("pages") ?: return emptyList()
    val out = ArrayList<DownloadableTrack>()
    for (key in pages.keys()) {
        val page = pages.optJSONObject(key) ?: continue
        val info = page.optJSONArray("imageinfo")?.optJSONObject(0) ?: continue
        val url = info.str("url")
        val mime = info.str("mime")
        val ext = extensionOf(url)
        if (!url.startsWith("https://") || !(mime.startsWith("audio/") || mime == "application/ogg")) continue
        if (ext !in AUDIO_EXTENSIONS) continue
        val ext2 = info.optJSONObject("extmetadata")
        fun meta(name: String) = ext2?.optJSONObject(name)?.str("value").orEmpty()
        val fileTitle = page.str("title").removePrefix("File:").substringBeforeLast('.')
        out += DownloadableTrack(
            providerId = "commons",
            remoteId = page.str("pageid").ifBlank { key },
            title = stripHtml(meta("ObjectName")).ifBlank { fileTitle },
            artist = stripHtml(meta("Artist")),
            album = "",
            durationSec = null,
            license = stripHtml(meta("LicenseShortName")).ifBlank { LicenseCodes.FREE_LICENSE },
            pageUrl = info.str("descriptionurl"),
            downloadUrl = url,
            coverUrl = "",
            extension = ext
        )
    }
    return out
}

// ---------------------------------------------------------------- ccMixter

internal fun parseCcMixter(json: String): List<DownloadableTrack> {
    val array = try { JSONArray(json) } catch (e: Exception) { return emptyList() }
    return (0 until array.length()).mapNotNull { i ->
        val upload = array.optJSONObject(i) ?: return@mapNotNull null
        val files = upload.optJSONArray("files") ?: return@mapNotNull null
        var url = ""
        var ext = ""
        for (j in 0 until files.length()) {
            val file = files.optJSONObject(j) ?: continue
            val candidate = file.str("download_url")
            val candidateExt = file.optJSONObject("file_format_info")?.str("default-ext").orEmpty()
                .ifBlank { extensionOf(candidate) }.lowercase()
            if (candidate.startsWith("https://") && candidateExt in AUDIO_EXTENSIONS) {
                url = candidate
                ext = candidateExt
                break
            }
        }
        if (url.isEmpty()) return@mapNotNull null
        DownloadableTrack(
            providerId = "ccmixter",
            remoteId = upload.str("upload_id").ifBlank { url },
            title = upload.str("upload_name"),
            artist = upload.str("user_real_name").ifBlank { upload.str("user_name") },
            album = "",
            durationSec = null,
            license = upload.str("license_name").ifBlank { shortLicense(upload.str("license_url")) },
            pageUrl = upload.str("file_page_url"),
            downloadUrl = url,
            coverUrl = "",
            extension = ext
        )
    }
}

// ---------------------------------------------------------------- Jamendo

internal fun parseJamendo(json: String): List<DownloadableTrack> {
    val results = JSONObject(json).optJSONArray("results") ?: return emptyList()
    return (0 until results.length()).mapNotNull { i ->
        val item = results.optJSONObject(i) ?: return@mapNotNull null
        val url = item.str("audiodownload")
        if (!url.startsWith("https://") || !item.optBoolean("audiodownload_allowed", false)) return@mapNotNull null
        DownloadableTrack(
            providerId = "jamendo",
            remoteId = item.str("id"),
            title = item.str("name"),
            artist = item.str("artist_name"),
            album = item.str("album_name"),
            durationSec = if (item.isNull("duration")) null else item.optInt("duration"),
            license = shortLicense(item.str("license_ccurl")).ifBlank { LicenseCodes.CREATIVE_COMMONS },
            pageUrl = item.str("shareurl"),
            downloadUrl = url,
            coverUrl = item.str("image").takeIf { it.startsWith("https://") }.orEmpty(),
            extension = "mp3"
        )
    }
}

// ---------------------------------------------------------------- RSS / podcast feeds

private fun Element.childText(tag: String): String =
    getElementsByTagName(tag).item(0)?.textContent?.trim().orEmpty()

internal fun parseFeed(xml: ByteArray, limit: Int = 50): List<DownloadableTrack> {
    val factory = DocumentBuilderFactory.newInstance()
    try {
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
    } catch (e: Exception) {
        // Not every parser knows this feature; feeds are size-limited anyway.
    }
    val document = factory.newDocumentBuilder().parse(ByteArrayInputStream(xml))
    val channel = document.getElementsByTagName("channel").item(0) as? Element
    val album = channel?.childText("title").orEmpty()
    val channelAuthor = channel?.childText("itunes:author").orEmpty()
    val items = document.getElementsByTagName("item")
    val out = ArrayList<DownloadableTrack>()
    for (i in 0 until items.length) {
        val item = items.item(i) as? Element ?: continue
        val enclosure = item.getElementsByTagName("enclosure").item(0) as? Element ?: continue
        val url = enclosure.getAttribute("url")
        val type = enclosure.getAttribute("type")
        val ext = extensionOf(url).ifBlank { if (type == "audio/mpeg") "mp3" else "" }
        if (!url.startsWith("https://") || !(type.startsWith("audio/") || ext in AUDIO_EXTENSIONS)) continue
        out += DownloadableTrack(
            providerId = "feed",
            remoteId = item.childText("guid").ifBlank { url },
            title = item.childText("title"),
            artist = item.childText("itunes:author").ifBlank { channelAuthor },
            album = album,
            durationSec = null,
            license = LicenseCodes.FEED_PUBLISHER,
            pageUrl = item.childText("link"),
            downloadUrl = url,
            coverUrl = "",
            extension = ext.ifBlank { "mp3" }
        )
        if (out.size >= limit) break
    }
    return out
}

/** Words only, so user text cannot inject search-engine syntax. */
internal fun searchWords(query: String): String =
    query.replace(Regex("[^\\p{L}\\p{N}\\s]"), " ").trim().replace(Regex("\\s+"), " ")
