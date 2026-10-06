package com.gaabaariaa.music.data.net

import java.net.HttpURLConnection
import java.net.URL

internal const val USER_AGENT = "Musiq/0.9.0 ( https://github.com/gaabaariaa/Music-Android )"

internal class HttpResponse(val code: Int, val body: ByteArray?)

/** Plain GET. Throws IOException when the network is unreachable; other failures are in [HttpResponse.code]. */
internal fun httpGet(url: String, accept: String? = null, maxBytes: Int = 2 * 1024 * 1024): HttpResponse {
    val connection = URL(url).openConnection() as HttpURLConnection
    try {
        connection.connectTimeout = 10_000
        connection.readTimeout = 15_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", USER_AGENT)
        if (accept != null) connection.setRequestProperty("Accept", accept)
        val code = connection.responseCode
        if (code != 200) return HttpResponse(code, null)
        val body = connection.inputStream.use { it.readBytes() }
        return HttpResponse(code, body.takeIf { it.size <= maxBytes })
    } finally {
        connection.disconnect()
    }
}
