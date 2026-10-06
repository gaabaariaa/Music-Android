package com.gaabaariaa.music.feature.library

import java.util.Base64

enum class DetailType { ARTIST, ALBUM, GENRE, FOLDER }

const val DETAIL_ROUTE = "detail/{type}/{value}"

/** Values are Base64-url encoded so any name (including empty or with slashes) is a safe path segment. */
fun detailRoute(type: DetailType, value: String): String {
    val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(Charsets.UTF_8))
    return "detail/${type.name}/_$encoded"
}

fun decodeDetailValue(arg: String): String {
    val raw = arg.removePrefix("_")
    if (raw.isEmpty()) return ""
    return String(Base64.getUrlDecoder().decode(raw), Charsets.UTF_8)
}
