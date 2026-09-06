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
    val glowAlpha = when {
        strong && dark -> 0.075f
        strong -> 0.045f
        dark -> 0.055f
        else -> 0.025f
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .size(if (strong) 370.dp else 300.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.onBackground.copy(alpha = glowAlpha),
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
    val background = MaterialTheme.colorScheme.surface.copy(alpha = if (dark) 0.86f else 0.78f)
    val border = if (dark) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.72f)
    val shape = RoundedCornerShape(cornerRadius)
    val shadow = if (dark) 14.dp else 7.dp

    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier.shadow(shadow, shape),
            shape = shape,
            color = background,
            border = BorderStroke(0.75.dp, border),
            content = content,
        )
    } else {
        Surface(
            modifier = modifier.shadow(shadow, shape),
            shape = shape,
            color = background,
            border = BorderStroke(0.75.dp, border),
            content = content,
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
