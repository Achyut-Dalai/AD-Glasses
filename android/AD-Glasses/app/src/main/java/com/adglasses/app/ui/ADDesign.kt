package com.adglasses.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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

@Composable
internal fun ADAmbientBackground(
    modifier: Modifier = Modifier,
    strong: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val dark = isSystemInDarkTheme()
    val neutralAlpha = when {
        strong && dark -> 0.08f
        strong -> 0.05f
        dark -> 0.055f
        else -> 0.026f
    }
    val accentAlpha = when {
        strong && dark -> 0.09f
        strong -> 0.055f
        dark -> 0.055f
        else -> 0.032f
    }

    Box(
        modifier = modifier
            .fillMaxSize()
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
                            ADAccent.Cyan.copy(alpha = accentAlpha * 0.55f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )
        content()
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
    val shape = RoundedCornerShape(cornerRadius)
    val shadow = if (dark) 16.dp else 10.dp
    val border = if (dark) {
        Color.White.copy(alpha = 0.13f)
    } else {
        Color.White.copy(alpha = 0.90f)
    }
    val glassFill = if (dark) {
        Brush.linearGradient(
            listOf(
                scheme.surface.copy(alpha = 0.80f),
                scheme.surfaceContainer.copy(alpha = 0.64f),
                scheme.surface.copy(alpha = 0.72f),
            ),
        )
    } else {
        Brush.linearGradient(
            listOf(
                Color.White.copy(alpha = 0.74f),
                scheme.surface.copy(alpha = 0.56f),
                scheme.surfaceContainer.copy(alpha = 0.46f),
            ),
        )
    }
    val sheen = Brush.linearGradient(
        listOf(
            Color.White.copy(alpha = if (dark) 0.08f else 0.24f),
            Color.Transparent,
            ADAccent.Indigo.copy(alpha = if (dark) 0.035f else 0.022f),
        ),
    )

    val surfaceModifier = modifier.shadow(
        elevation = shadow,
        shape = shape,
        ambientColor = Color.Black.copy(alpha = if (dark) 0.24f else 0.08f),
        spotColor = Color.Black.copy(alpha = if (dark) 0.20f else 0.06f),
    )

    val glassContent: @Composable () -> Unit = {
        Box(Modifier.background(glassFill)) {
            Box(Modifier.fillMaxSize().background(sheen))
            content()
        }
    }

    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = surfaceModifier,
            shape = shape,
            color = Color.Transparent,
            border = BorderStroke(0.75.dp, border),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            content = glassContent,
        )
    } else {
        Surface(
            modifier = surfaceModifier,
            shape = shape,
            color = Color.Transparent,
            border = BorderStroke(0.75.dp, border),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            content = glassContent,
        )
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
        content = content,
    )
}
