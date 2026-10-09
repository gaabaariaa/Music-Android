package com.gaabaariaa.music.feature.player

import android.content.ContentUris
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/** Bumped after artwork changes so every visible cover reloads. */
object ArtworkRefresh {
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()
    fun bump() { _version.value += 1 }
}

/** Small in-memory cache so scrolling a list does not decode the same covers again and again. */
private object ArtworkCache {
    private val covers = object : LruCache<String, ImageBitmap>(24 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.asAndroidBitmap().byteCount / 1024
    }
    private val missing = ConcurrentHashMap<String, Boolean>()

    fun get(key: String): ImageBitmap? = covers.get(key)
    fun isMissing(key: String): Boolean = missing.containsKey(key)

    fun put(key: String, image: ImageBitmap?) {
        if (image != null) {
            covers.put(key, image)
        } else {
            if (missing.size > 5_000) missing.clear()
            missing[key] = true
        }
    }
}

/**
 * Shows the embedded cover of a song. Loading thumbnails needs Android 10+;
 * older versions (and songs without cover) show a neutral placeholder.
 */
@Composable
fun SongArtwork(songId: String, modifier: Modifier = Modifier, sizePx: Int = 512) {
    val context = LocalContext.current
    val version by ArtworkRefresh.version.collectAsState()
    val key = "$songId@$sizePx@$version"
    val bitmap by produceState<ImageBitmap?>(initialValue = ArtworkCache.get(key), key) {
        val cached = ArtworkCache.get(key)
        val id = songId.toLongOrNull()
        value = when {
            cached != null -> cached
            id == null || ArtworkCache.isMissing(key) || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q -> null
            else -> withContext(Dispatchers.IO) {
                val loaded = try {
                    val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                    context.contentResolver.loadThumbnail(uri, Size(sizePx, sizePx), null).asImageBitmap()
                } catch (e: Exception) {
                    null
                }
                ArtworkCache.put(key, loaded)
                loaded
            }
        }
    }
    Box(
        modifier
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        val image = bitmap
        if (image != null) {
            Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(Icons.Default.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
