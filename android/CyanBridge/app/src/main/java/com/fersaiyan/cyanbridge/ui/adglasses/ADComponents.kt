package com.fersaiyan.cyanbridge.ui.adglasses

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fersaiyan.cyanbridge.R

private val ADCardShape = RoundedCornerShape(15.dp)

@Composable
internal fun ADTopBar(
    title: String? = null,
    showBrand: Boolean = false,
    showBack: Boolean = false,
    onBack: () -> Unit = {},
    showSettings: Boolean = false,
    onSettings: () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .padding(start = if (showBack) 6.dp else 14.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showBack) {
            IconButton(onClick = onBack, modifier = Modifier.size(44.dp)) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        if (showBrand) {
            ADGlassesMark(Modifier.size(width = 34.dp, height = 20.dp))
            Spacer(Modifier.size(8.dp))
            Text(
                text = "AD Glasses",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.1.sp,
                color = MaterialTheme.colorScheme.onBackground,
            )
        } else if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = if (showBack) 0.dp else 2.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.weight(1f))
        if (showSettings) {
            IconButton(onClick = onSettings, modifier = Modifier.size(44.dp)) {
                Surface(
                    modifier = Modifier.size(34.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun ADGlassesMark(modifier: Modifier = Modifier) {
    Icon(
        painter = painterResource(R.drawable.ad_app_mark),
        contentDescription = null,
        modifier = modifier,
        tint = MaterialTheme.colorScheme.primary,
    )
}

@Composable
internal fun ADBottomNavigation(selected: ADTab, onSelected: (ADTab) -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
        shadowElevation = 5.dp,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            ADTab.entries.forEach { tab ->
                val icon = when (tab) {
                    ADTab.HOME -> Icons.Outlined.Home
                    ADTab.CHATS -> Icons.Outlined.Terminal
                    ADTab.AI -> Icons.Outlined.AutoAwesome
                    ADTab.LIBRARY -> Icons.Outlined.PhotoLibrary
                }
                ADBottomNavigationItem(
                    tab = tab,
                    icon = icon,
                    selected = selected == tab,
                    modifier = Modifier.weight(1f),
                ) { onSelected(tab) }
            }
        }
    }
}

@Composable
private fun ADBottomNavigationItem(
    tab: ADTab,
    icon: ImageVector,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = modifier
            .heightIn(min = 50.dp)
            .clip(RoundedCornerShape(13.dp))
            .clickable(role = Role.Tab, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = 38.dp, height = 27.dp)
                .background(
                    if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    RoundedCornerShape(11.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = tab.label, tint = tint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(1.dp))
        Text(
            tab.label,
            color = tint,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
internal fun ADSectionTitle(title: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.weight(1f))
        if (action != null) {
            Text(
                action,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(onClick = onAction).padding(6.dp),
            )
        }
    }
}

@Composable
internal fun ADCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val clickableModifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Column(
        modifier = modifier
            .clip(ADCardShape)
            .background(MaterialTheme.colorScheme.surface, ADCardShape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, ADCardShape)
            .then(clickableModifier)
            .padding(14.dp),
        content = content,
    )
}

@Composable
internal fun ADStatusChip(
    text: String,
    tone: ADStatusTone = ADStatusTone.NEUTRAL,
    showCheck: Boolean = false,
) {
    val background = when (tone) {
        ADStatusTone.NEUTRAL -> MaterialTheme.colorScheme.surfaceVariant
        ADStatusTone.INFO -> MaterialTheme.colorScheme.primaryContainer
        ADStatusTone.SUCCESS -> ADColors.SuccessSoft
        ADStatusTone.WARNING -> ADColors.WarningSoft
        ADStatusTone.ERROR -> ADColors.ErrorSoft
    }
    val foreground = when (tone) {
        ADStatusTone.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
        ADStatusTone.INFO -> MaterialTheme.colorScheme.onPrimaryContainer
        ADStatusTone.SUCCESS -> ADColors.Success
        ADStatusTone.WARNING -> ADColors.Warning
        ADStatusTone.ERROR -> ADColors.Error
    }
    Row(
        modifier = Modifier.background(background, CircleShape).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (showCheck) Icon(Icons.Rounded.Check, null, tint = foreground, modifier = Modifier.size(12.dp))
        Text(text, color = foreground, style = MaterialTheme.typography.labelSmall)
    }
}

internal enum class ADStatusTone { NEUTRAL, INFO, SUCCESS, WARNING, ERROR }

@Composable
internal fun ADSettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    trailing: @Composable (() -> Unit)? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    iconBackground: Color = MaterialTheme.colorScheme.primaryContainer,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(36.dp).background(iconBackground, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = iconTint, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.padding(start = 11.dp, end = 8.dp).weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(1.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            trailing()
        } else {
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
