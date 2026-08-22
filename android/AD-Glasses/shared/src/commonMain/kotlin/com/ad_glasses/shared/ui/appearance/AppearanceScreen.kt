package com.ad_glasses.shared.ui.appearance

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ad_glasses.shared.icons.AppIcon
import com.ad_glasses.shared.icons.imageVector
import com.ad_glasses.shared.appearance.AccentProfile
import com.ad_glasses.shared.appearance.AccentProfiles
import com.ad_glasses.shared.appearance.AppearanceSettings
import com.ad_glasses.shared.appearance.ThemeMode
import com.ad_glasses.shared.generated.resources.Res
import com.ad_glasses.shared.generated.resources.action_back
import com.ad_glasses.shared.generated.resources.appearance_accessibility
import com.ad_glasses.shared.generated.resources.appearance_accent_profile
import com.ad_glasses.shared.generated.resources.appearance_dynamic_color
import com.ad_glasses.shared.generated.resources.appearance_dynamic_color_description
import com.ad_glasses.shared.generated.resources.appearance_dynamic_color_requires
import com.ad_glasses.shared.generated.resources.appearance_high_contrast
import com.ad_glasses.shared.generated.resources.appearance_high_contrast_description
import com.ad_glasses.shared.generated.resources.appearance_live_preview
import com.ad_glasses.shared.generated.resources.appearance_palette_description
import com.ad_glasses.shared.generated.resources.appearance_preview_description
import com.ad_glasses.shared.generated.resources.appearance_primary
import com.ad_glasses.shared.generated.resources.appearance_reset
import com.ad_glasses.shared.generated.resources.appearance_secondary
import com.ad_glasses.shared.generated.resources.appearance_tertiary
import com.ad_glasses.shared.generated.resources.appearance_theme
import com.ad_glasses.shared.generated.resources.appearance_title
import com.ad_glasses.shared.ui.localizedAccentProfile
import com.ad_glasses.shared.ui.localizedThemeMode
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class, ExperimentalResourceApi::class)
@Composable
fun AppearanceScreen(
    settings: AppearanceSettings,
    dynamicColorAvailable: Boolean,
    onSettingsChange: (AppearanceSettings) -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.appearance_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = AppIcon.Back.imageVector(),
                            contentDescription = stringResource(Res.string.action_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                SettingsSection(title = stringResource(Res.string.appearance_theme)) {
                    ThemeMode.entries.forEach { mode ->
                        SelectionRow(
                            label = localizedThemeMode(mode),
                            selected = settings.themeMode == mode,
                            onClick = { onSettingsChange(settings.copy(themeMode = mode)) },
                        )
                    }
                }
            }

            item {
                SettingsSection(title = stringResource(Res.string.appearance_accent_profile)) {
                    Text(
                        text = stringResource(Res.string.appearance_palette_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    AccentProfiles.all.forEach { profile ->
                        AccentRow(
                            profile = profile,
                            selected = settings.accentProfileId == profile.id,
                            enabled = !settings.useDynamicColor,
                            onClick = {
                                onSettingsChange(
                                    settings.copy(
                                        accentProfileId = profile.id,
                                        useDynamicColor = false,
                                    ),
                                )
                            },
                        )
                    }
                }
            }

            item {
                SettingsSection(title = stringResource(Res.string.appearance_accessibility)) {
                    SwitchRow(
                        title = stringResource(Res.string.appearance_high_contrast),
                        description = stringResource(Res.string.appearance_high_contrast_description),
                        checked = settings.highContrast,
                        onCheckedChange = { onSettingsChange(settings.copy(highContrast = it)) },
                    )
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    SwitchRow(
                        title = stringResource(Res.string.appearance_dynamic_color),
                        description = if (dynamicColorAvailable) {
                            stringResource(Res.string.appearance_dynamic_color_description)
                        } else {
                            stringResource(Res.string.appearance_dynamic_color_requires)
                        },
                        checked = settings.useDynamicColor && dynamicColorAvailable,
                        enabled = dynamicColorAvailable,
                        onCheckedChange = { onSettingsChange(settings.copy(useDynamicColor = it)) },
                    )
                }
            }

            item {
                PreviewCard()
            }

            item {
                OutlinedButton(
                    onClick = onReset,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(Res.string.appearance_reset))
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                content = content,
            )
        }
    }
}

@Composable
private fun SelectionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { this.selected = selected }
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(
            text = label,
            modifier = Modifier.padding(start = 8.dp),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@OptIn(ExperimentalResourceApi::class)
@Composable
private fun AccentRow(
    profile: AccentProfile,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { this.selected = selected }
            .clickable(enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(Color(profile.lightPrimaryArgb)),
        )
        Text(
            text = localizedAccentProfile(profile),
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
        )
        RadioButton(
            selected = selected,
            enabled = enabled,
            onClick = null,
        )
    }
}

@Composable
private fun SwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, role = Role.Switch) { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = null,
        )
    }
}

@OptIn(ExperimentalResourceApi::class)
@Composable
private fun PreviewCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(Res.string.appearance_live_preview), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(Res.string.appearance_preview_description),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text(stringResource(Res.string.appearance_primary), modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp))
                }
                Surface(
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text(stringResource(Res.string.appearance_secondary), modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp))
                }
                Surface(
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text(stringResource(Res.string.appearance_tertiary), modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp))
                }
            }
        }
    }
}
