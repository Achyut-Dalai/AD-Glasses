package com.fersaiyan.cyanbridge.ui.adglasses

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            showBack -> {
                Surface(
                    onClick = onBack,
                    modifier = Modifier.size(42.dp),
                    shape = CircleShape,
                    color = ADColors.Surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = ADColors.Ink,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            showBrand -> {
                Surface(
                    modifier = Modifier.size(38.dp),
                    shape = RoundedCornerShape(13.dp),
                    color = ADColors.Surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Image(
                            painter = painterResource(R.drawable.ad_glasses_icon_source),
                            contentDescription = "AD Glasses",
                            modifier = Modifier.size(27.dp),
                            contentScale = ContentScale.Fit,
                        )
                    }
                }
            }
        }

        if (showBrand) {
            Column(Modifier.padding(start = 10.dp)) {
                Text(
                    text = "AD GLASSES",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = ADTechFontFamily,
                        letterSpacing = 1.25.sp,
                    ),
                    color = ADColors.Muted,
                )
                Text(
                    text = "Your AI companion",
                    style = MaterialTheme.typography.titleSmall,
                    color = ADColors.Ink,
                )
            }
        } else if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = if (showBack) 12.dp else 0.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.weight(1f))

        if (showSettings) {
            Surface(
                onClick = onSettings,
                modifier = Modifier.size(42.dp),
                shape = CircleShape,
                color = ADColors.Surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Box(contentAlignment = Alignment.Center) {
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ADColors.Background)
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(26.dp),
            color = ADColors.Glass,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shadowElevation = 5.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp),
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
    val indicatorWidth by animateDpAsState(
        targetValue = if (selected) 44.dp else 34.dp,
        label = "nav-indicator-width",
    )
    val indicatorColor by animateColorAsState(
        targetValue = if (selected) ADColors.Ink else Color.Transparent,
        label = "nav-indicator-color",
    )
    val iconTint by animateColorAsState(
        targetValue = if (selected) ADColors.Surface else ADColors.Muted,
        label = "nav-icon-color",
    )
    val labelTint by animateColorAsState(
        targetValue = if (selected) ADColors.Ink else ADColors.Muted,
        label = "nav-label-color",
    )

    Column(
        modifier = modifier
            .heightIn(min = 56.dp)
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
            modifier = Modifier
                .width(indicatorWidth)
                .height(30.dp)
                .background(indicatorColor, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            ADGlyphIcon(glyph, iconTint, Modifier.size(19.dp))
        }
        Spacer(Modifier.height(3.dp))
        Text(
            tab.label,
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
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = ADTechFontFamily,
                    letterSpacing = 0.75.sp,
                ),
                color = ADColors.Muted,
            )
            Spacer(Modifier.height(4.dp))
        }
        Text(title, style = MaterialTheme.typography.headlineLarge, color = ADColors.Ink)
        if (!detail.isNullOrBlank()) {
            Spacer(Modifier.height(5.dp))
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = ADColors.Muted)
        }
    }
}

@Composable
internal fun ADSectionTitle(title: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
    ) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = ADTechFontFamily,
                letterSpacing = 0.70.sp,
            ),
            color = ADColors.Muted,
        )
        Spacer(Modifier.weight(1f))
        if (action != null) {
            Text(
                action,
                style = MaterialTheme.typography.labelMedium,
                color = ADColors.Ink,
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
    Surface(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick ?: {},
        enabled = onClick != null,
        shape = RoundedCornerShape(22.dp),
        color = ADColors.Surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = if (onClick != null) 1.dp else 0.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
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
        modifier = modifier.fillMaxWidth().heightIn(min = 52.dp),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (destructive) ADColors.Error else ADColors.Ink,
            contentColor = ADColors.Surface,
            disabledContainerColor = ADColors.SurfaceSubtle,
            disabledContentColor = ADColors.Muted,
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp, vertical = 12.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
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
        ADStatusTone.INFO -> ADColors.BlueSoft
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
        modifier = Modifier.background(background, CircleShape).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (showCheck) Icon(Icons.Rounded.Check, null, tint = foreground, modifier = Modifier.size(13.dp))
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
    iconTint: Color = ADColors.Ink,
    iconBackground: Color = ADColors.SurfaceSubtle,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(38.dp).background(iconBackground, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = iconTint, modifier = Modifier.size(19.dp))
        }
        Column(Modifier.padding(start = 11.dp, end = 8.dp).weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = ADColors.Muted)
            }
        }
        if (trailing != null) {
            trailing()
        } else {
            Surface(shape = CircleShape, color = ADColors.SurfaceSubtle) {
                Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        null,
                        tint = ADColors.Muted,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}
