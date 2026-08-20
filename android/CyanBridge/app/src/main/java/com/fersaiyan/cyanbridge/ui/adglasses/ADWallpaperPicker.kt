package com.fersaiyan.cyanbridge.ui.adglasses

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun ADWallpaperPicker() {
    val context = LocalContext.current
    var selected by remember(context) { mutableStateOf(ADWallpaperPreferences.get(context)) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ADSectionTitle("Wallpaper")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ADWallpaperStyle.entries.forEach { style ->
                ADWallpaperOption(
                    style = style,
                    selected = style == selected,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        selected = style
                        ADWallpaperPreferences.set(context, style)
                    },
                )
            }
        }
    }
}

@Composable
private fun ADWallpaperOption(
    style: ADWallpaperStyle,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(112.dp),
        shape = RoundedCornerShape(15.dp),
        color = ADColors.Surface,
        contentColor = Color.White,
        border = BorderStroke(1.dp, if (selected) ADColors.Ink.copy(alpha = .54f) else ADColors.Outline),
    ) {
        Box {
            Image(
                painter = painterResource(style.drawableRes),
                contentDescription = "${style.label} wallpaper",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.30f)))
            Surface(
                modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
                shape = RoundedCornerShape(8.dp),
                color = Color.Black.copy(alpha = .72f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = .12f)),
            ) {
                Text(
                    style.label,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (selected) {
                Surface(
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(23.dp),
                    shape = CircleShape,
                    color = ADColors.Surface.copy(alpha = .90f),
                    border = BorderStroke(1.dp, ADColors.Outline),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        ADMatrixGlyphIcon(ADMatrixGlyph.CHECK, ADColors.Ink, Modifier.size(13.dp))
                    }
                }
            }
        }
    }
}
