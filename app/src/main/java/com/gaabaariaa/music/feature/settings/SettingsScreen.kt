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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gaabaariaa.music.R
import com.gaabaariaa.music.core.designsystem.DynamicColorSupported
import com.gaabaariaa.music.core.designsystem.accentSwatch
import com.gaabaariaa.music.core.designsystem.isDarkTheme
import com.gaabaariaa.music.domain.model.Accent
import com.gaabaariaa.music.domain.model.ThemeMode

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val dynamicActive = settings.dynamicColor && DynamicColorSupported
    val dark = isDarkTheme(settings)

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_title)) }) },
        contentWindowInsets = WindowInsets(0.dp)
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .verticalScroll(rememberScrollState())
        ) {
            SectionTitle(stringResource(R.string.settings_appearance))

            Text(
                stringResource(R.string.theme_mode),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            ThemeMode.entries.forEach { mode ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = settings.themeMode == mode,
                            role = Role.RadioButton,
                            onClick = { viewModel.setThemeMode(mode) }
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = settings.themeMode == mode, onClick = null)
                    Text(
                        stringResource(mode.label()),
                        modifier = Modifier.padding(start = 16.dp)
                    )
                }
            }

            SwitchRow(
                title = stringResource(R.string.dynamic_color),
                summary = stringResource(
                    if (DynamicColorSupported) R.string.dynamic_color_summary
                    else R.string.dynamic_color_unsupported
                ),
                checked = settings.dynamicColor && DynamicColorSupported,
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

            Text(
                stringResource(R.string.accent_color),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
            )
            Row(
                Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .alpha(if (dynamicActive) 0.38f else 1f),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Accent.entries.forEach { accent ->
                    val selected = settings.accent == accent
                    val color = accentSwatch(accent, dark)
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(color)
                            .then(
                                if (selected) Modifier.border(
                                    BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface),
                                    CircleShape
                                ) else Modifier
                            )
                            .clickable(enabled = !dynamicActive, role = Role.RadioButton) {
                                viewModel.setAccent(accent)
                            },
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
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
    )
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
        modifier = Modifier.toggleable(
            value = checked,
            enabled = enabled,
            role = Role.Switch,
            onValueChange = onChange
        )
    )
}

private fun ThemeMode.label(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
}

private fun Accent.label(): Int = when (this) {
    Accent.PURPLE -> R.string.accent_purple
    Accent.BLUE -> R.string.accent_blue
    Accent.GREEN -> R.string.accent_green
    Accent.ORANGE -> R.string.accent_orange
    Accent.RED -> R.string.accent_red
}
