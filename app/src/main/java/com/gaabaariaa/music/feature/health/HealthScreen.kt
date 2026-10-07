@file:OptIn(ExperimentalMaterial3Api::class)

package com.gaabaariaa.music.feature.health

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.gaabaariaa.music.R
import com.gaabaariaa.music.domain.model.AuditState
import com.gaabaariaa.music.domain.model.HealthIssue
import com.gaabaariaa.music.domain.model.LibraryHealth
import com.gaabaariaa.music.domain.repository.HealthRepository
import com.gaabaariaa.music.domain.repository.LibraryAuditor
import com.gaabaariaa.music.feature.library.DetailType
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class HealthViewModel @Inject constructor(
    repository: HealthRepository,
    private val auditor: LibraryAuditor
) : ViewModel() {
    val health: StateFlow<LibraryHealth> = repository.observeHealth()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryHealth())
    val audit: StateFlow<AuditState> = auditor.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AuditState())

    fun runAudit() = auditor.start()
}

@Composable
fun HealthScreen(
    onBack: () -> Unit,
    onOpenDetail: (DetailType, String) -> Unit,
    onOpenDuplicates: () -> Unit,
    viewModel: HealthViewModel = hiltViewModel()
) {
    val health by viewModel.health.collectAsStateWithLifecycle()
    val audit by viewModel.audit.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.health_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                }
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState())) {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { health.score / 100f },
                    modifier = Modifier.size(140.dp),
                    strokeWidth = 12.dp
                )
                Text(
                    stringResource(R.string.health_score, health.score),
                    style = MaterialTheme.typography.headlineLarge
                )
            }
            Text(
                LocalContext.current.resources.getQuantityString(R.plurals.songs_count, health.total, health.total),
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                stringResource(R.string.health_how),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Card(Modifier.fillMaxWidth().padding(16.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.health_audit_progress, health.audited, health.total),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (audit.running) {
                        LinearProgressIndicator(
                            progress = { if (audit.total > 0) audit.done / audit.total.toFloat() else 0f },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            stringResource(R.string.health_audit_running, audit.done, audit.total),
                            style = MaterialTheme.typography.labelMedium
                        )
                    } else {
                        Button(onClick = viewModel::runAudit) { Text(stringResource(R.string.health_run_audit)) }
                    }
                }
            }

            IssueRow(HealthIssue.MISSING_ARTWORK, health.missingArtwork, onOpenDetail)
            IssueRow(HealthIssue.MISSING_LYRICS, health.missingLyrics, onOpenDetail)
            IssueRow(HealthIssue.MISSING_TITLE, health.missingTitle, onOpenDetail)
            IssueRow(HealthIssue.MISSING_ARTIST, health.missingArtist, onOpenDetail)
            IssueRow(HealthIssue.MISSING_ALBUM, health.missingAlbum, onOpenDetail)
            IssueRow(HealthIssue.UNRECOGNIZED, health.unrecognized, onOpenDetail)
            if (health.extendedInfoSupported) {
                IssueRow(HealthIssue.MISSING_GENRE, health.missingGenre, onOpenDetail)
                IssueRow(HealthIssue.LOW_QUALITY, health.lowQuality, onOpenDetail)
            } else {
                Text(
                    stringResource(R.string.health_needs_android_11),
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            ListItem(
                headlineContent = { Text(stringResource(R.string.issue_duplicates)) },
                trailingContent = { Text(health.duplicateGroups.toString()) },
                modifier = Modifier.clickable(enabled = health.duplicateGroups > 0, onClick = onOpenDuplicates)
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun IssueRow(issue: HealthIssue, count: Int, onOpenDetail: (DetailType, String) -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(issue.label())) },
        trailingContent = { Text(count.toString()) },
        modifier = Modifier.clickable(enabled = count > 0) { onOpenDetail(DetailType.ISSUE, issue.name) }
    )
    HorizontalDivider()
}
