package com.gaabaariaa.music.data.lyrics

import android.content.ContentResolver
import android.net.Uri
import com.gaabaariaa.music.core.di.IoDispatcher
import com.gaabaariaa.music.core.util.decodeLyricsBytes
import com.gaabaariaa.music.core.util.parseLyrics
import com.gaabaariaa.music.data.local.LyricsDao
import com.gaabaariaa.music.data.local.LyricsEntity
import com.gaabaariaa.music.data.net.httpGet
import com.gaabaariaa.music.domain.model.Lyrics
import com.gaabaariaa.music.domain.model.LyricsSource
import com.gaabaariaa.music.domain.model.OnlineLyricsResult
import com.gaabaariaa.music.domain.model.TagField
import com.gaabaariaa.music.domain.repository.LyricsRepository
import com.gaabaariaa.music.domain.repository.TagRepository
import java.io.IOException
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

private const val MAX_TEXT_BYTES = 1024 * 1024

@Singleton
class LyricsRepositoryImpl @Inject constructor(
    private val dao: LyricsDao,
    private val tags: TagRepository,
    private val resolver: ContentResolver,
    @IoDispatcher private val io: CoroutineDispatcher
) : LyricsRepository {

    override fun observe(songId: Long): Flow<Lyrics?> =
        dao.observe(songId).map { stored ->
            stored?.let { Lyrics(it.content, it.synced, it.source) } ?: embedded(songId)
        }

    override suspend fun get(songId: Long): Lyrics? = observe(songId).first()

    private suspend fun embedded(songId: Long): Lyrics? {
        val text = tags.read(listOf(songId))[songId]?.get(TagField.LYRICS).orEmpty()
        if (text.isBlank()) return null
        return Lyrics(text, parseLyrics(text).synced, LyricsSource.EMBEDDED)
    }

    override suspend fun save(songId: Long, content: String, source: String) {
        val text = content.trim()
        if (text.isEmpty()) {
            dao.delete(songId)
        } else {
            dao.upsert(LyricsEntity(songId, text, parseLyrics(text).synced, source))
        }
    }

    override suspend fun delete(songId: Long) = dao.delete(songId)

    override suspend fun readText(uri: String): String? = withContext(io) {
        try {
            resolver.openInputStream(Uri.parse(uri))?.use { input ->
                val bytes = input.readBytes()
                if (bytes.size > MAX_TEXT_BYTES) null else decodeLyricsBytes(bytes)
            }
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun fetchOnline(
        title: String,
        artist: String,
        album: String,
        durationMs: Long
    ): OnlineLyricsResult = withContext(io) {
        if (title.isBlank()) return@withContext OnlineLyricsResult.NotFound
        val seconds = (durationMs / 1000).toInt()
        try {
            if (album.isNotBlank() && artist.isNotBlank() && seconds > 0) {
                val exact = httpGet(
                    "https://lrclib.net/api/get?" + query(title, artist, album, seconds), "application/json"
                )
                if (exact.code == 200 && exact.body != null) {
                    parseLrclibObject(String(exact.body, Charsets.UTF_8)).toLyrics()
                        ?.let { return@withContext OnlineLyricsResult.Found(it) }
                } else if (exact.code != 404 && exact.code != 400) {
                    return@withContext OnlineLyricsResult.Error
                }
            }
            val search = httpGet(
                "https://lrclib.net/api/search?" + query(title, artist, "", 0), "application/json"
            )
            if (search.code != 200 || search.body == null) {
                return@withContext if (search.code == 404) OnlineLyricsResult.NotFound else OnlineLyricsResult.Error
            }
            val best = chooseBest(parseLrclibArray(String(search.body, Charsets.UTF_8)), seconds)?.toLyrics()
            if (best != null) OnlineLyricsResult.Found(best) else OnlineLyricsResult.NotFound
        } catch (e: IOException) {
            OnlineLyricsResult.Offline
        } catch (e: Exception) {
            OnlineLyricsResult.Error
        }
    }

    private fun query(title: String, artist: String, album: String, seconds: Int): String {
        fun enc(value: String) = URLEncoder.encode(value.trim(), "UTF-8")
        return buildList {
            add("track_name=" + enc(title))
            if (artist.isNotBlank()) add("artist_name=" + enc(artist))
            if (album.isNotBlank()) add("album_name=" + enc(album))
            if (seconds > 0) add("duration=$seconds")
        }.joinToString("&")
    }
}
