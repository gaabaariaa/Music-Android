package com.gaabaariaa.music.data.lyrics

import com.gaabaariaa.music.domain.model.Lyrics
import com.gaabaariaa.music.domain.model.LyricsSource
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

internal class LrclibHit(
    val synced: String?,
    val plain: String?,
    val durationSec: Double?,
    val instrumental: Boolean
) {
    fun toLyrics(): Lyrics? = when {
        !synced.isNullOrBlank() -> Lyrics(synced, true, LyricsSource.ONLINE)
        !plain.isNullOrBlank() -> Lyrics(plain, false, LyricsSource.ONLINE)
        else -> null
    }
}

private fun JSONObject.stringOrNull(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

internal fun parseLrclibObject(obj: JSONObject): LrclibHit = LrclibHit(
    synced = obj.stringOrNull("syncedLyrics"),
    plain = obj.stringOrNull("plainLyrics"),
    durationSec = if (obj.isNull("duration")) null else obj.optDouble("duration"),
    instrumental = obj.optBoolean("instrumental", false)
)

internal fun parseLrclibObject(json: String): LrclibHit = parseLrclibObject(JSONObject(json))

internal fun parseLrclibArray(json: String): List<LrclibHit> {
    val array = JSONArray(json)
    return (0 until array.length()).mapNotNull { array.optJSONObject(it)?.let(::parseLrclibObject) }
}

/** Prefers hits with synced lyrics whose duration is within 3 seconds of the song's. */
internal fun chooseBest(hits: List<LrclibHit>, durationSec: Int): LrclibHit? {
    val usable = hits.filter { !it.instrumental && it.toLyrics() != null }
    if (usable.isEmpty()) return null
    fun closeEnough(hit: LrclibHit) =
        durationSec <= 0 || hit.durationSec == null || abs(hit.durationSec - durationSec) <= 3.0
    return usable.firstOrNull { closeEnough(it) && !it.synced.isNullOrBlank() }
        ?: usable.firstOrNull { closeEnough(it) }
}
