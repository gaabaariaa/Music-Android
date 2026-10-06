package com.gaabaariaa.music

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.room.Room
import com.gaabaariaa.music.data.MusicDatabase
import com.gaabaariaa.music.data.MusicRepository
import com.gaabaariaa.music.data.SongEntity
import com.gaabaariaa.music.player.MusicPlayerManager
import kotlinx.coroutines.*

class MainActivity : ComponentActivity() {
    private lateinit var db: MusicDatabase
    private lateinit var player: MusicPlayerManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var permissionGranted by mutableStateOf(false)
    private var scanning by mutableStateOf(false)
    private var scanResult by mutableStateOf("")

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionGranted = granted
        if (granted) scan()
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        db = Room.databaseBuilder(applicationContext, MusicDatabase::class.java, "music.db").build()
        player = MusicPlayerManager(this)
        permissionGranted = hasAudioPermission()
        setContent {
            MusicApp(db, player, permissionGranted, scanning, scanResult) { requestAudioPermission() }
        }
        if (permissionGranted) scan()
    }

    private fun hasAudioPermission() =
        ContextCompat.checkSelfPermission(this, audioPermission()) == PackageManager.PERMISSION_GRANTED

    private fun audioPermission() =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE

    private fun requestAudioPermission() = permissionLauncher.launch(audioPermission())

    private fun scan() {
        if (scanning) return
        scanning = true
        scope.launch(Dispatchers.IO) {
            val count = MusicRepository(contentResolver, db.songDao()).scan()
            withContext(Dispatchers.Main) {
                scanResult = "Library scanned: $count songs"
                scanning = false
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        player.release()
        db.close()
        super.onDestroy()
    }
}

@OptIn(androidx.media3.common.util.UnstableApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun MusicApp(
    db: MusicDatabase,
    player: MusicPlayerManager,
    permission: Boolean,
    scanning: Boolean,
    result: String,
    onPermission: () -> Unit
) {
    val songs by db.songDao().observeSongs().collectAsState(emptyList())
    val artists by db.songDao().observeArtists().collectAsState(emptyList())
    val albums by db.songDao().observeAlbums().collectAsState(emptyList())
    var tab by remember { mutableStateOf(0) }
    var showNowPlaying by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(player.player.isPlaying) }
    var currentIndex by remember { mutableStateOf(player.player.currentMediaItemIndex) }
    var currentPosition by remember { mutableStateOf(player.player.currentPosition.coerceAtLeast(0L)) }
    var duration by remember { mutableStateOf(player.player.duration.coerceAtLeast(0L)) }

    DisposableEffect(player) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                currentIndex = player.player.currentMediaItemIndex
                duration = player.player.duration.coerceAtLeast(0L)
                currentPosition = 0L
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                duration = player.player.duration.coerceAtLeast(0L)
            }
        }
        player.player.addListener(listener)
        onDispose { player.player.removeListener(listener) }
    }

    LaunchedEffect(isPlaying, showNowPlaying) {
        while (showNowPlaying || isPlaying) {
            currentPosition = player.player.currentPosition.coerceAtLeast(0L)
            duration = player.player.duration.coerceAtLeast(0L)
            delay(500)
        }
    }

    val currentSong = songs.getOrNull(currentIndex)
        ?: player.player.currentMediaItem?.let { item ->
            SongEntity(
                mediaStoreId = item.mediaId.toLongOrNull() ?: -1L,
                title = item.mediaMetadata.title?.toString().orEmpty(),
                artist = item.mediaMetadata.artist?.toString().orEmpty(),
                album = item.mediaMetadata.albumTitle?.toString().orEmpty(),
                albumArtist = "",
                genre = "",
                year = 0,
                track = 0,
                durationMs = item.mediaMetadata.extras?.getLong("duration", 0L) ?: 0L,
                sizeBytes = 0L,
                mimeType = "",
                path = ""
            )
        }

    MaterialTheme {
        if (showNowPlaying && currentSong != null) {
            NowPlayingScreen(
                song = currentSong,
                queue = songs,
                currentIndex = currentIndex,
                isPlaying = isPlaying,
                positionMs = currentPosition,
                durationMs = duration,
                onBack = { showNowPlaying = false },
                onPrevious = player::previous,
                onPlayPause = player::togglePlayPause,
                onNext = player::next,
                onSeek = { player.player.seekTo(it) },
                onSelect = { index ->
                    player.setQueue(songs, index)
                    currentIndex = index
                }
            )
            return@MaterialTheme
        }

        Scaffold(
            topBar = { TopAppBar(title = { Text("Music Library") }) },
            bottomBar = {
                if (currentSong != null) {
                    MiniPlayer(
                        title = currentSong.title,
                        artist = currentSong.artist,
                        isPlaying = isPlaying,
                        onOpen = { showNowPlaying = true },
                        onPrevious = player::previous,
                        onPlayPause = player::togglePlayPause,
                        onNext = player::next
                    )
                }
            }
        ) { pad ->
            Column(Modifier.padding(pad).fillMaxSize()) {
                if (!permission) {
                    Text("Music access is required to scan songs on this device.", Modifier.padding(16.dp))
                    Button(onClick = onPermission, Modifier.padding(horizontal = 16.dp)) {
                        Text("Allow music access")
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        listOf("Songs", "Artists", "Albums").forEachIndexed { i, label ->
                            TextButton(onClick = { tab = i }) {
                                Text(if (tab == i) "● $label" else label)
                            }
                        }
                    }
                    if (scanning) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (result.isNotBlank()) Text(result, Modifier.padding(16.dp))
                    when (tab) {
                        0 -> SongList(songs) { index -> player.setQueue(songs, index) }
                        1 -> EntityList(artists) { it.artist }
                        else -> EntityList(albums) { it.album }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniPlayer(
    title: String,
    artist: String,
    isPlaying: Boolean,
    onOpen: () -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit
) {
    Surface(tonalElevation = 4.dp, modifier = Modifier.clickable(onClick = onOpen)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f).padding(8.dp)) {
                Text(title, maxLines = 1)
                Text(artist, maxLines = 1, style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onPrevious) { Text("Prev") }
            TextButton(onClick = onPlayPause) { Text(if (isPlaying) "Pause" else "Play") }
            TextButton(onClick = onNext) { Text("Next") }
        }
    }
}

@Composable
private fun NowPlayingScreen(
    song: SongEntity,
    queue: List<SongEntity>,
    currentIndex: Int,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    onBack: () -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onSelect: (Int) -> Unit
) {
    var sliderPosition by remember(positionMs) { mutableFloatStateOf(positionMs.toFloat()) }
    val max = durationMs.coerceAtLeast(1L).toFloat()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Now Playing") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(20.dp)) {
            Spacer(Modifier.height(32.dp))
            Text(song.title, style = MaterialTheme.typography.headlineMedium)
            Text(song.artist, style = MaterialTheme.typography.titleMedium)
            Text(song.album, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(28.dp))

            Slider(
                value = sliderPosition.coerceIn(0f, max),
                onValueChange = { sliderPosition = it },
                onValueChangeFinished = { onSeek(sliderPosition.toLong()) },
                valueRange = 0f..max
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatTime(positionMs))
                Text(formatTime(durationMs))
            }

            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                TextButton(onClick = onPrevious) { Text("Previous") }
                Button(onClick = onPlayPause) { Text(if (isPlaying) "Pause" else "Play") }
                TextButton(onClick = onNext) { Text("Next") }
            }

            Spacer(Modifier.height(24.dp))
            Text("Queue", style = MaterialTheme.typography.titleLarge)
            LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                itemsIndexed(queue, key = { _, item -> item.mediaStoreId }) { index, item ->
                    ListItem(
                        headlineContent = { Text(item.title) },
                        supportingContent = { Text(item.artist + " • " + item.album) },
                        trailingContent = { if (index == currentIndex) Text("Playing") },
                        modifier = Modifier.clickable { onSelect(index) }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

@Composable
private fun SongList(songs: List<SongEntity>, onPlay: (Int) -> Unit) {
    if (songs.isEmpty()) Text("No local music found.", Modifier.padding(16.dp))
    LazyColumn {
        itemsIndexed(songs, key = { _, song -> song.mediaStoreId }) { index, song ->
            ListItem(
                headlineContent = { Text(song.title) },
                supportingContent = { Text(song.artist + " • " + song.album) },
                modifier = Modifier.clickable { onPlay(index) }
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun EntityList(items: List<SongEntity>, label: (SongEntity) -> String) {
    if (items.isEmpty()) Text("Nothing found.", Modifier.padding(16.dp))
    LazyColumn {
        itemsIndexed(items, key = { _, item -> item.mediaStoreId }) { _, item ->
            ListItem(headlineContent = { Text(label(item)) })
        }
    }
}
