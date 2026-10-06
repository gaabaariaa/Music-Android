package com.gaabaariaa.music.feature.lyrics

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gaabaariaa.music.R
import com.gaabaariaa.music.core.util.currentLineIndex
import com.gaabaariaa.music.core.util.parseLyrics
import com.gaabaariaa.music.domain.model.Lyrics
import com.gaabaariaa.music.domain.repository.LyricsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

@HiltViewModel
class LyricsPaneViewModel @Inject constructor(private val repository: LyricsRepository) : ViewModel() {
    fun observe(songId: Long): Flow<Lyrics?> = repository.observe(songId)
}

/** Lyrics of the playing song inside Now Playing: scrolls with the music when they are synced. */
@Composable
fun LyricsPane(
    mediaId: String,
    positionMs: Long,
    onSeek: (Long) -> Unit,
    onOpenEditor: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LyricsPaneViewModel = hiltViewModel()
) {
    val songId = mediaId.toLongOrNull()
    val flow = remember(songId) { if (songId == null) flowOf(null) else viewModel.observe(songId) }
    val lyrics by flow.collectAsStateWithLifecycle(initialValue = null)
    val parsed = remember(lyrics?.content) { lyrics?.let { parseLyrics(it.content) } }

    if (songId == null) return
    if (parsed == null || parsed.lines.isEmpty()) {
        Column(modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.lyrics_none), style = MaterialTheme.typography.bodyLarge)
            OutlinedButton(onClick = { onOpenEditor(songId) }, modifier = Modifier.padding(top = 12.dp)) {
                Text(stringResource(R.string.lyrics_find))
            }
        }
        return
    }

    val listState = rememberLazyListState()
    val currentIndex = if (parsed.synced) currentLineIndex(parsed.lines, positionMs) else -1
    LaunchedEffect(currentIndex) {
        if (currentIndex >= 0) listState.animateScrollToItem((currentIndex - 2).coerceAtLeast(0))
    }

    Column(modifier.fillMaxSize()) {
        TextButton(onClick = { onOpenEditor(songId) }) { Text(stringResource(R.string.lyrics_edit)) }
        LazyColumn(Modifier.fillMaxWidth().weight(1f), state = listState) {
            itemsIndexed(parsed.lines) { index, line ->
                val current = index == currentIndex
                Text(
                    text = line.text.ifBlank { "♪" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
                    color = when {
                        !parsed.synced -> MaterialTheme.colorScheme.onSurface
                        current -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (parsed.synced) Modifier.clickable { onSeek(line.timeMs) } else Modifier)
                        .padding(vertical = 6.dp)
                )
            }
        }
    }
}
