@file:OptIn(ExperimentalMaterial3Api::class)

package com.gaabaariaa.music.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gaabaariaa.music.R
import com.gaabaariaa.music.core.designsystem.DynamicColorSupported
import com.gaabaariaa.music.core.designsystem.accentSwatch
import com.gaabaariaa.music.core.designsystem.isDarkTheme
import com.gaabaariaa.music.domain.model.Accent
import com.gaabaariaa.music.domain.model.CardShape
import com.gaabaariaa.music.domain.model.LibraryLayout
import com.gaabaariaa.music.domain.model.LibraryTab
import com.gaabaariaa.music.domain.model.ThemeMode
import com.gaabaariaa.music.feature.home.titleRes

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val dynamicActive = settings.dynamicColor && DynamicColorSupported
    val dark = isDarkTheme(settings)

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_title)) }) },
        contentWindowInsets = WindowInsets(0.dp)
    ) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState())) {
            // ------------------------------------------------------------ appearance
            SectionTitle(stringResource(R.string.settings_appearance))
            Subtitle(stringResource(R.string.theme_mode))
            ThemeMode.entries.forEach { mode ->
                RadioRow(stringResource(mode.label()), settings.themeMode == mode) { viewModel.setThemeMode(mode) }
            }
            SwitchRow(
                title = stringResource(R.string.dynamic_color),
                summary = stringResource(
                    if (DynamicColorSupported) R.string.dynamic_color_summary else R.string.dynamic_color_unsupported
                ),
                checked = dynamicActive,
                enabled = DynamicColorSupported,
                onChange = viewModel::setDynamicColor
            )
            SwitchRow(
                title = stringResource(R.string.amoled),
                summary = stringResource(R.string.amoled_summary),
                checked = settings.amoled,
                enabled = true,
                onChange = viewModel::setAmoled
            )
            Subtitle(stringResource(R.string.accent_color))
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp).alpha(if (dynamicActive) 0.38f else 1f),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Accent.entries.forEach { accent ->
                    val selected = settings.accent == accent
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(accentSwatch(accent, dark))
                            .then(
                                if (selected) Modifier.border(
                                    BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface), CircleShape
                                ) else Modifier
                            )
                            .clickable(enabled = !dynamicActive, role = Role.RadioButton) { viewModel.setAccent(accent) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (selected) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = stringResource(accent.label()),
                                tint = MaterialTheme.colorScheme.surface
                            )
                        }
                    }
                }
            }
            Subtitle(stringResource(R.string.settings_card_shape))
            CardShape.entries.forEach { shape ->
                RadioRow(stringResource(shape.label()), settings.cardShape == shape) { viewModel.setCardShape(shape) }
            }
            SliderRow(
                title = stringResource(R.string.settings_corner_radius),
                valueText = "${settings.cornerRadiusDp} dp",
                value = settings.cornerRadiusDp.toFloat(),
                range = 0f..32f,
                steps = 7,
                onFinished = { viewModel.setCornerRadius(it.toInt()) }
            )
            SliderRow(
                title = stringResource(R.string.settings_text_size),
                valueText = "${(settings.textScale * 100).toInt()}%",
                value = settings.textScale,
                range = 0.8f..1.4f,
                steps = 5,
                onFinished = viewModel::setTextScale
            )
            SwitchRow(
                title = stringResource(R.string.settings_animations),
                summary = stringResource(R.string.settings_animations_summary),
                checked = settings.animationsEnabled,
                enabled = true,
                onChange = viewModel::setAnimations
            )

            // ------------------------------------------------------------ library
            SectionTitle(stringResource(R.string.settings_library))
            LibraryLayout.entries.forEach { layout ->
                RadioRow(stringResource(layout.label()), settings.libraryLayout == layout) {
                    viewModel.setLibraryLayout(layout)
                }
            }
            if (settings.libraryLayout == LibraryLayout.GRID) {
                SliderRow(
                    title = stringResource(R.string.settings_grid_columns),
                    valueText = settings.gridColumns.toString(),
                    value = settings.gridColumns.toFloat(),
                    range = 2f..4f,
                    steps = 1,
                    onFinished = { viewModel.setGridColumns(it.toInt()) }
                )
            }
            SwitchRow(
                title = stringResource(R.string.settings_show_artwork),
                summary = stringResource(R.string.settings_show_artwork_summary),
                checked = settings.showArtwork,
                enabled = true,
                onChange = viewModel::setShowArtwork
            )
            Subtitle(stringResource(R.string.settings_tab_order))
            settings.libraryTabs.forEachIndexed { index, tab ->
                ReorderRow(
                    title = stringResource(tab.titleRes()),
                    canMoveUp = index > 0,
                    canMoveDown = index < settings.libraryTabs.lastIndex,
                    onUp = { viewModel.moveLibraryTab(tab, -1) },
                    onDown = { viewModel.moveLibraryTab(tab, 1) }
                )
            }

            // ------------------------------------------------------------ home
            SectionTitle(stringResource(R.string.settings_home))
            Text(
                stringResource(R.string.settings_home_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            settings.homeSections.forEachIndexed { index, config ->
                ReorderRow(
                    title = stringResource(config.section.titleRes()),
                    checked = config.enabled,
                    onChecked = { viewModel.setHomeSectionEnabled(config.section, it) },
                    canMoveUp = index > 0,
                    canMoveDown = index < settings.homeSections.lastIndex,
                    onUp = { viewModel.moveHomeSection(config.section, -1) },
                    onDown = { viewModel.moveHomeSection(config.section, 1) }
                )
            }

            // ------------------------------------------------------------ player
            SectionTitle(stringResource(R.string.settings_player))
            SwitchRow(
                title = stringResource(R.string.settings_mini_player),
                summary = stringResource(R.string.settings_mini_player_summary),
                checked = settings.miniPlayerEnabled,
                enabled = true,
                onChange = viewModel::setMiniPlayer
            )
            SwitchRow(
                title = stringResource(R.string.settings_keep_screen_on),
                summary = stringResource(R.string.settings_keep_screen_on_summary),
                checked = settings.keepScreenOn,
                enabled = true,
                onChange = viewModel::setKeepScreenOn
            )
            SwitchRow(
                title = stringResource(R.string.settings_lyrics_first),
                summary = stringResource(R.string.settings_lyrics_first_summary),
                checked = settings.lyricsFirst,
                enabled = true,
                onChange = viewModel::setLyricsFirst
            )

            // ------------------------------------------------------------ downloads
            SectionTitle(stringResource(R.string.settings_downloads))
            SwitchRow(
                title = stringResource(R.string.settings_wifi_only),
                summary = stringResource(R.string.settings_wifi_only_summary),
                checked = settings.downloadWifiOnly,
                enabled = true,
                onChange = viewModel::setDownloadWifiOnly
            )
            var jamendoId by remember { mutableStateOf(settings.jamendoClientId) }
            OutlinedTextField(
                value = jamendoId,
                onValueChange = {
                    jamendoId = it
                    viewModel.setJamendoClientId(it)
                },
                singleLine = true,
                label = { Text(stringResource(R.string.settings_jamendo)) },
                supportingText = { Text(stringResource(R.string.settings_jamendo_summary)) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp)
    )
}

@Composable
private fun Subtitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)
    )
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, modifier = Modifier.padding(start = 16.dp))
    }
}

@Composable
private fun SwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null, enabled = enabled) },
        modifier = Modifier.toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
    )
}

/** Moves smoothly while dragging and only saves when the finger is lifted. */
@Composable
private fun SliderRow(
    title: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onFinished: (Float) -> Unit
) {
    var local by remember(value) { mutableFloatStateOf(value) }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title)
            Text(valueText, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = local,
            onValueChange = { local = it },
            onValueChangeFinished = { onFinished(local) },
            valueRange = range,
            steps = steps
        )
    }
}

@Composable
private fun ReorderRow(
    title: String,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onUp: () -> Unit,
    onDown: () -> Unit,
    checked: Boolean? = null,
    onChecked: (Boolean) -> Unit = {}
) {
    Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (checked != null) Checkbox(checked = checked, onCheckedChange = onChecked)
        Text(title, modifier = Modifier.weight(1f).padding(start = if (checked == null) 8.dp else 0.dp))
        IconButton(onClick = onUp, enabled = canMoveUp) {
            Icon(Icons.Default.ArrowUpward, stringResource(R.string.move_up))
        }
        IconButton(onClick = onDown, enabled = canMoveDown) {
            Icon(Icons.Default.ArrowDownward, stringResource(R.string.move_down))
        }
    }
    HorizontalDivider()
}

private fun LibraryTab.titleRes(): Int = when (this) {
    LibraryTab.SONGS -> R.string.tab_songs
    LibraryTab.ARTISTS -> R.string.tab_artists
    LibraryTab.ALBUMS -> R.string.tab_albums
    LibraryTab.GENRES -> R.string.tab_genres
    LibraryTab.FOLDERS -> R.string.tab_folders
}

private fun ThemeMode.label(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
}

private fun CardShape.label(): Int = when (this) {
    CardShape.ROUNDED -> R.string.card_shape_rounded
    CardShape.CUT -> R.string.card_shape_cut
}

private fun LibraryLayout.label(): Int = when (this) {
    LibraryLayout.LIST -> R.string.layout_list
    LibraryLayout.GRID -> R.string.layout_grid
}

private fun Accent.label(): Int = when (this) {
    Accent.PURPLE -> R.string.accent_purple
    Accent.BLUE -> R.string.accent_blue
    Accent.GREEN -> R.string.accent_green
    Accent.ORANGE -> R.string.accent_orange
    Accent.RED -> R.string.accent_red
}
