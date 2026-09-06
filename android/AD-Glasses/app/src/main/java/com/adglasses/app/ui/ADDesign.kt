package com.adglasses.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

internal object ADDimens {
    val screenGutter = 16.dp
    val welcomeGutter = 20.dp
    val featureRadius = 20.dp
    val lensRadius = 26.dp
    val assistantHeroRadius = 28.dp
    val floatingRadius = 24.dp
}

internal object ADAccent {
    val Indigo = Color(0xFF4F46E5)
    val Blue = Color(0xFF2563EB)
    val Cyan = Color(0xFF06B6D4)
    val Teal = Color(0xFF0D9488)
    val Pink = Color(0xFFDB2777)
    val Orange = Color(0xFFF97316)
    val Red = Color(0xFFDC2626)
    val Green = Color(0xFF22C55E)
    val Purple = Color(0xFF9333EA)
}

private val LocalADHazeState = compositionLocalOf<HazeState?> { null }

@Composable
internal fun ADAmbientBackground(
    modifier: Modifier = Modifier,
    strong: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val dark = isSystemInDarkTheme()
    val hazeState = rememberHazeState()
    val neutralAlpha = when {
        strong && dark -> 0.10f
        strong -> 0.065f
        dark -> 0.075f
        else -> 0.045f
    }
    val accentAlpha = when {
        strong && dark -> 0.13f
        strong -> 0.085f
        dark -> 0.085f
        else -> 0.060f
    }

    CompositionLocalProvider(LocalADHazeState provides hazeState) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .hazeSource(state = hazeState)
                    .background(MaterialTheme.colorScheme.background),
            ) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .size(if (strong) 430.dp else 340.dp)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    ADAccent.Indigo.copy(alpha = accentAlpha),
                                    MaterialTheme.colorScheme.onBackground.copy(alpha = neutralAlpha),
                                    Color.Transparent,
                                ),
                            ),
                        ),
                )
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .size(if (strong) 360.dp else 280.dp)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    ADAccent.Cyan.copy(alpha = accentAlpha * 0.72f),
                                    Color.Transparent,
                                ),
                            ),
                        ),
                )
            }

            content()
        }
    }
}

@Composable
internal fun ADGlassSurface(
    modifier: Modifier = Modifier,
    cornerRadius: Dp,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val dark = isSystemInDarkTheme()
    val scheme = MaterialTheme.colorScheme
    val hazeState = LocalADHazeState.current
    val shape = RoundedCornerShape(cornerRadius)

    val borderColor = if (dark) {
        Color.White.copy(alpha = 0.16f)
    } else {
        scheme.onSurface.copy(alpha = 0.085f)
    }
    val fallbackColor = if (dark) {
        scheme.surfaceContainer.copy(alpha = 0.94f)
    } else {
        scheme.surface.copy(alpha = 0.94f)
    }
    val hazeStyle = HazeStyle(
        backgroundColor = if (dark) scheme.background else scheme.surfaceContainerLowest,
        tints = listOf(
            HazeTint(
                if (dark) {
                    scheme.surfaceContainer.copy(alpha = 0.54f)
                } else {
                    Color.White.copy(alpha = 0.46f)
                },
            ),
            HazeTint(
                ADAccent.Indigo.copy(alpha = if (dark) 0.045f else 0.022f),
            ),
        ),
        blurRadius = 22.dp,
        noiseFactor = if (dark) 0.08f else 0.05f,
        fallbackTint = HazeTint(fallbackColor),
    )
    val foregroundSheen = if (dark) {
        Brush.linearGradient(
            listOf(
                Color.White.copy(alpha = 0.055f),
                Color.Transparent,
                ADAccent.Indigo.copy(alpha = 0.025f),
            ),
        )
    } else {
        Brush.linearGradient(
            listOf(
                Color.White.copy(alpha = 0.22f),
                Color.White.copy(alpha = 0.08f),
                ADAccent.Indigo.copy(alpha = 0.018f),
            ),
        )
    }

    var outerModifier = modifier
        .shadow(
            elevation = if (dark) 12.dp else 7.dp,
            shape = shape,
            ambientColor = Color.Black.copy(alpha = if (dark) 0.30f else 0.08f),
            spotColor = Color.Black.copy(alpha = if (dark) 0.24f else 0.06f),
        )
        .clip(shape)
        .border(0.75.dp, borderColor, shape)

    if (onClick != null) {
        outerModifier = outerModifier.clickable(onClick = onClick)
    }

    Box(modifier = outerModifier) {
        if (hazeState != null) {
            Box(
                Modifier
                    .matchParentSize()
                    .hazeEffect(state = hazeState, style = hazeStyle),
            )
        } else {
            Box(Modifier.matchParentSize().background(fallbackColor))
        }

        Box(
            Modifier
                .matchParentSize()
                .background(
                    if (dark) {
                        scheme.surface.copy(alpha = 0.20f)
                    } else {
                        Color.White.copy(alpha = 0.16f)
                    },
                )
                .background(foregroundSheen),
        )

        CompositionLocalProvider(LocalContentColor provides scheme.onSurface) {
            Box { content() }
        }
    }
}

@Composable
internal fun ADGroupedCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 18.dp,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(cornerRadius),
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
        content = content,
    )
}
