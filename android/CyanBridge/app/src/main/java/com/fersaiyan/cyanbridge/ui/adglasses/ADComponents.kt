package com.fersaiyan.cyanbridge.ui.adglasses

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
            .heightIn(min = 50.dp)
            .padding(horizontal = 14.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            showBack -> {
                Surface(
                    onClick = onBack,
                    modifier = Modifier.size(36.dp),
                    shape = CircleShape,
                    color = ADColors.Surface.copy(alpha = 0.82f),
                    border = BorderStroke(1.dp, ADColors.Outline),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = ADColors.Ink,
                            modifier = Modifier.size(17.dp),
                        )
                    }
                }
            }

            showBrand -> {
                Box(
                    modifier = Modifier.size(39.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    ADGlyphIcon(
                        glyph = ADGlyph.DEVICE,
                        tint = ADColors.Ink,
                        modifier = Modifier.size(34.dp),
                        accent = ADColors.Red,
                    )
                }
            }
        }

        if (showBrand) {
            Column(Modifier.padding(start = 8.dp)) {
                Text(
                    text = "AD GLASSES",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.1.sp),
                    color = ADColors.Muted,
                )
                Text(
                    text = "AI eyewear",
                    style = MaterialTheme.typography.titleSmall,
                    color = ADColors.Ink,
                )
            }
        } else if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = if (showBack) 10.dp else 0.dp),
                color = ADColors.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.weight(1f))

        if (showSettings) {
            Surface(
                onClick = onSettings,
                modifier = Modifier.size(36.dp),
                shape = CircleShape,
                color = ADColors.Surface.copy(alpha = 0.82f),
                border = BorderStroke(1.dp, ADColors.Outline),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.Settings,
                        contentDescription = "Settings",
                        tint = ADColors.Ink,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
internal fun ADGlassesMark(modifier: Modifier = Modifier) {
    ADGlyphIcon(
        glyph = ADGlyph.DEVICE,
        tint = ADColors.Ink,
        modifier = modifier,
        accent = ADColors.Red,
    )
}

@Composable
internal fun ADBottomNavigation(selected: ADTab, onSelected: (ADTab) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = ADColors.Glass,
            border = BorderStroke(1.dp, ADColors.Outline),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.SpaceAround,
            ) {
                ADTab.entries.forEach { tab ->
                    val glyph = when (tab) {
                        ADTab.HOME -> ADGlyph.HOME
                        ADTab.CHATS -> ADGlyph.PROMPT
                        ADTab.AI -> ADGlyph.AI
                        ADTab.LIBRARY -> ADGlyph.LIBRARY
                    }
                    ADBottomNavigationItem(
                        tab = tab,
                        glyph = glyph,
                        selected = selected == tab,
                        modifier = Modifier.weight(1f),
                    ) { onSelected(tab) }
                }
            }
        }
    }
}

@Composable
private fun ADBottomNavigationItem(
    tab: ADTab,
    glyph: ADGlyph,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val iconTint by animateColorAsState(
        targetValue = if (selected) ADColors.Ink else ADColors.Muted,
        label = "nav-icon-color",
    )
    val labelTint by animateColorAsState(
        targetValue = if (selected) ADColors.Ink else ADColors.Muted,
        label = "nav-label-color",
    )

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
        Box(contentAlignment = Alignment.Center) {
            ADGlyphIcon(
                glyph = glyph,
                tint = iconTint,
                modifier = Modifier.size(18.dp),
                accent = if (selected && glyph == ADGlyph.AI) ADColors.Red else null,
            )
            if (selected) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .size(3.5.dp)
                        .background(ADColors.Red, CircleShape),
                )
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(
            tab.label.uppercase(),
            color = labelTint,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

@Composable
internal fun ADScreenIntro(
    eyebrow: String? = null,
    title: String,
    detail: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (!eyebrow.isNullOrBlank()) {
            Text(
                eyebrow.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.9.sp),
                color = ADColors.Muted,
            )
            Spacer(Modifier.height(3.dp))
        }
        Text(title, style = MaterialTheme.typography.headlineLarge, color = ADColors.Ink)
        if (!detail.isNullOrBlank()) {
            Spacer(Modifier.height(3.dp))
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = ADColors.Muted)
        }
    }
}

@Composable
internal fun ADSectionTitle(title: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 1.dp),
    ) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.8.sp),
            color = ADColors.Muted,
        )
        Spacer(Modifier.weight(1f))
        if (action != null) {
            Text(
                action.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = ADColors.Ink,
                modifier = Modifier.clickable(onClick = onAction).padding(5.dp),
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
    Surface(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick ?: {},
        enabled = onClick != null,
        shape = RoundedCornerShape(14.dp),
        color = ADColors.Surface.copy(alpha = 0.88f),
        border = BorderStroke(1.dp, ADColors.Outline),
    ) {
        Column(modifier = Modifier.padding(12.dp), content = content)
    }
}

@Composable
internal fun ADPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    destructive: Boolean = false,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = 46.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (destructive) ADColors.Red else ADColors.Ink,
            contentColor = if (destructive) Color.White else Color.Black,
            disabledContainerColor = ADColors.SurfaceSubtle,
            disabledContentColor = ADColors.Muted,
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 9.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(7.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
internal fun ADStatusChip(
    text: String,
    tone: ADStatusTone = ADStatusTone.NEUTRAL,
    showCheck: Boolean = false,
) {
    val background = when (tone) {
        ADStatusTone.NEUTRAL -> ADColors.SurfaceSubtle
        ADStatusTone.INFO -> ADColors.SurfaceSubtle
        ADStatusTone.SUCCESS -> ADColors.SuccessSoft
        ADStatusTone.WARNING -> ADColors.WarningSoft
        ADStatusTone.ERROR -> ADColors.ErrorSoft
    }
    val foreground = when (tone) {
        ADStatusTone.NEUTRAL -> ADColors.Muted
        ADStatusTone.INFO -> ADColors.Ink
        ADStatusTone.SUCCESS -> ADColors.Success
        ADStatusTone.WARNING -> ADColors.Warning
        ADStatusTone.ERROR -> ADColors.Error
    }
    Row(
        modifier = Modifier.background(background, CircleShape).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (showCheck) Icon(Icons.Rounded.Check, null, tint = foreground, modifier = Modifier.size(11.dp))
        Text(text.uppercase(), color = foreground, style = MaterialTheme.typography.labelSmall)
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
    iconTint: Color = ADColors.Ink,
    iconBackground: Color = ADColors.SurfaceSubtle,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(32.dp).background(iconBackground, RoundedCornerShape(9.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = iconTint, modifier = Modifier.size(16.dp))
        }
        Column(Modifier.padding(start = 9.dp, end = 7.dp).weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = ADColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (trailing != null) {
            trailing()
        } else {
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                null,
                tint = ADColors.Muted,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
