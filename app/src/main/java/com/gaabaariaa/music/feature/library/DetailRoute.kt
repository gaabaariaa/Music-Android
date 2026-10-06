package com.gaabaariaa.music.feature.library

import android.util.Base64

enum class DetailType { ARTIST, ALBUM, GENRE, FOLDER }

const val DETAIL_ROUTE = "detail/{type}/{value}"

/** Values are Base64-url encoded so any name (including empty or with slashes) is a safe path segment. */
fun detailRoute(type: DetailType, value: String): String {
    val encoded = Base64.encodeToString(
        value.toByteArray(Charsets.UTF_8),
        Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
    )
    return "detail/${type.name}/_$encoded"
}

fun decodeDetailValue(arg: String): String {
    val raw = arg.removePrefix("_")
    if (raw.isEmpty()) return ""
    return String(
        Base64.decode(raw, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING),
        Charsets.UTF_8
    )
}
