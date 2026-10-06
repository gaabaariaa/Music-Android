package com.gaabaariaa.music.core.util

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

data class LyricLine(val timeMs: Long, val text: String)

data class ParsedLyrics(val lines: List<LyricLine>, val synced: Boolean)

private val timestamp = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")
private val offsetTag = Regex("""\[offset:\s*([+-]?\d+)\s*]""", RegexOption.IGNORE_CASE)

/** Parses LRC ("[mm:ss.xx] text", several stamps per line, offset tag) or plain lyrics. */
fun parseLyrics(raw: String): ParsedLyrics {
    val text = raw.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n')
    val offsetMs = offsetTag.find(text)?.groupValues?.get(1)?.toLongOrNull() ?: 0L

    val synced = ArrayList<LyricLine>()
    var anyStamp = false
    for (line in text.split('\n')) {
        var rest = line.trimStart()
        val stamps = ArrayList<Long>()
        while (true) {
            val match = timestamp.find(rest)
            if (match == null || match.range.first != 0) break
            val minutes = match.groupValues[1].toLong()
            val seconds = match.groupValues[2].toLong()
            val fraction = match.groupValues[3].let {
                when (it.length) {
                    0 -> 0L
                    1 -> it.toLong() * 100
                    2 -> it.toLong() * 10
                    else -> it.take(3).toLong()
                }
            }
            stamps += ((minutes * 60 + seconds) * 1000 + fraction - offsetMs).coerceAtLeast(0L)
            rest = rest.substring(match.range.last + 1)
        }
        if (stamps.isNotEmpty()) {
            anyStamp = true
            val content = rest.trim()
            stamps.forEach { synced += LyricLine(it, content) }
        }
    }
    if (anyStamp) return ParsedLyrics(synced.sortedBy { it.timeMs }, synced = true)

    val plain = text.split('\n').map { LyricLine(-1L, it.trimEnd()) }
    return ParsedLyrics(plain.dropWhile { it.text.isBlank() }.dropLastWhile { it.text.isBlank() }, synced = false)
}

/** Index of the last line that started at or before [positionMs], or -1 before the first line. */
fun currentLineIndex(lines: List<LyricLine>, positionMs: Long): Int {
    var low = 0
    var high = lines.size - 1
    var result = -1
    while (low <= high) {
        val mid = (low + high) ushr 1
        if (lines[mid].timeMs <= positionMs) {
            result = mid
            low = mid + 1
        } else {
            high = mid - 1
        }
    }
    return result
}

/** UTF-8 first, UTF-16 when a BOM says so, otherwise the legacy Persian code page. */
fun decodeLyricsBytes(bytes: ByteArray): String {
    if (bytes.size >= 2) {
        val b0 = bytes[0].toInt() and 0xFF
        val b1 = bytes[1].toInt() and 0xFF
        if ((b0 == 0xFF && b1 == 0xFE) || (b0 == 0xFE && b1 == 0xFF)) {
            return String(bytes, Charsets.UTF_16).removePrefix("\uFEFF")
        }
    }
    return try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (e: CharacterCodingException) {
        String(bytes, Charset.forName("windows-1256"))
    }.removePrefix("\uFEFF")
}
