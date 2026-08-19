package com.fersaiyan.cyanbridge.ui.adglasses

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fersaiyan.cyanbridge.R

@Composable
internal fun ADTopBar(
    title: String? = null,
    showBrand: Boolean = false,
    showBack: Boolean = false,
    onBack: () -> Unit = {},
    showSettings: Boolean = false,
    onSettings: () -> Unit = {},
) {
    if (showBrand) {
        Box(
            modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp).padding(horizontal = 16.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.ad_glasses_icon_source),
                contentDescription = "AD Glasses",
                modifier = Modifier.size(36.dp).align(Alignment.CenterStart),
                contentScale = ContentScale.Fit,
            )
            Text(
                text = "AD GLASSES",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 2.2.sp,
                modifier = Modifier.align(Alignment.Center),
            )
            if (showSettings) {
                IconButton(
                    onClick = onSettings,
                    modifier = Modifier
                        .size(42.dp)
                        .align(Alignment.CenterEnd),
                ) {
                    Box(
                        modifier = Modifier.size(34.dp).background(ADColors.Surface, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Rounded.Settings,
                            contentDescription = "Settings",
                            tint = ADColors.Ink,
                            modifier = Modifier.size(19.dp),
                        )
                    }
                }
            }
        }
        return
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showBack) {
            IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = ADColors.Blue)
            }
        }
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = if (showBack) 4.dp else 0.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.weight(1f))
        if (showSettings) {
            IconButton(onClick = onSettings, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Rounded.Settings, contentDescription = "Settings", tint = ADColors.Ink)
            }
        }
    }
}

@Composable
internal fun ADGlassesMark(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.ad_glasses_icon_source),
        contentDescription = null,
        modifier = modifier,
        contentScale = ContentScale.Fit,
    )
}

@Composable
internal fun ADBottomNavigation(selected: ADTab, onSelected: (ADTab) -> Unit) {
    Surface(
        color = ADColors.Surface.copy(alpha = 0.98f),
        shadowElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 10.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            ADTab.entries.forEach { tab ->
                val icon = when (tab) {
                    ADTab.HOME -> Icons.Rounded.Home
                    ADTab.CHATS -> Icons.Outlined.Terminal
                    ADTab.AI -> Icons.Rounded.AutoAwesome
                    ADTab.LIBRARY -> Icons.Rounded.PhotoLibrary
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
    val tint = if (selected) ADColors.Blue else ADColors.Muted
    Column(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clickable(
                role = Role.Tab,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier.size(width = 38.dp, height = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = tab.label, tint = tint, modifier = Modifier.size(20.dp))
        }
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
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.weight(1f))
        if (action != null) {
            Text(
                action,
                style = MaterialTheme.typography.labelLarge,
                color = ADColors.Blue,
                modifier = Modifier
                    .clickable(onClick = onAction)
                    .padding(7.dp),
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
            .clip(RoundedCornerShape(18.dp))
            .then(clickableModifier)
            .background(ADColors.Surface, RoundedCornerShape(18.dp))
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
        ADStatusTone.NEUTRAL -> ADColors.SurfaceSubtle
        ADStatusTone.INFO -> ADColors.BlueSoft
        ADStatusTone.SUCCESS -> ADColors.SuccessSoft
        ADStatusTone.WARNING -> ADColors.WarningSoft
        ADStatusTone.ERROR -> ADColors.ErrorSoft
    }
    val foreground = when (tone) {
        ADStatusTone.NEUTRAL -> ADColors.Muted
        ADStatusTone.INFO -> ADColors.Blue
        ADStatusTone.SUCCESS -> ADColors.Success
        ADStatusTone.WARNING -> ADColors.Warning
        ADStatusTone.ERROR -> ADColors.Error
    }
    Row(
        modifier = Modifier.background(background, CircleShape).padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (showCheck) Icon(Icons.Rounded.Check, null, tint = foreground, modifier = Modifier.size(14.dp))
        Text(text, color = foreground, style = MaterialTheme.typography.labelMedium)
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
    iconTint: Color = ADColors.Blue,
    iconBackground: Color = ADColors.BlueSoft,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(34.dp).background(iconBackground, RoundedCornerShape(9.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = iconTint, modifier = Modifier.size(19.dp))
        }
        Column(Modifier.padding(start = 11.dp, end = 8.dp).weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = ADColors.Muted)
            }
        }
        if (trailing != null) {
            trailing()
        } else {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = ADColors.Muted, modifier = Modifier.size(20.dp))
        }
    }
}
