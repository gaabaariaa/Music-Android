package com.gaabaariaa.music.data.artwork

import com.gaabaariaa.music.domain.model.ArtworkCandidate
import org.json.JSONObject

/** Parses a MusicBrainz release search response (fmt=json) into unique candidates. */
internal fun parseReleases(json: String, limit: Int = 12): List<ArtworkCandidate> {
    val releases = JSONObject(json).optJSONArray("releases") ?: return emptyList()
    val seen = HashSet<String>()
    val out = ArrayList<ArtworkCandidate>()
    for (i in 0 until releases.length()) {
        val release = releases.optJSONObject(i) ?: continue
        val id = release.optString("id")
        if (id.isBlank()) continue
        val title = release.optString("title")
        val year = release.optString("date").take(4)
        val credits = release.optJSONArray("artist-credit")
        val artist = buildString {
            if (credits != null) {
                for (j in 0 until credits.length()) {
                    val credit = credits.optJSONObject(j) ?: continue
                    append(credit.optString("name"))
                    append(credit.optString("joinphrase"))
                }
            }
        }
        if (seen.add("$title|$artist|$year")) out += ArtworkCandidate(id, title, artist, year)
        if (out.size >= limit) break
    }
    return out
}

/** Escapes a value for use inside a quoted Lucene phrase. */
internal fun luceneQuote(value: String): String =
    "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

internal fun buildQuery(title: String, artist: String, album: String): String? {
    val parts = ArrayList<String>()
    if (album.isNotBlank()) parts += "release:" + luceneQuote(album.trim())
    else if (title.isNotBlank()) parts += "recording:" + luceneQuote(title.trim())
    if (artist.isNotBlank()) parts += "artist:" + luceneQuote(artist.trim())
    return if (parts.isEmpty() || (album.isBlank() && title.isBlank())) null else parts.joinToString(" AND ")
}

internal fun imageMimeType(bytes: ByteArray): String? = when {
    bytes.size > 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() -> "image/jpeg"
    bytes.size > 8 && bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte() &&
        bytes[2] == 'N'.code.toByte() && bytes[3] == 'G'.code.toByte() -> "image/png"
    else -> null
}
