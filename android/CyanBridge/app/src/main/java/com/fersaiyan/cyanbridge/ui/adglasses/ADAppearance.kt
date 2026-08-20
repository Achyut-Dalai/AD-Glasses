package com.fersaiyan.cyanbridge.ui.adglasses

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

enum class ADWallpaperStyle(val label: String) {
    SOLID("Solid"),
    DOT_GRID("Dots"),
    ORBIT("Orbit"),
    LINES("Lines"),
}

internal object ADAppearancePrefs {
    private const val PREFS = "ad_appearance"
    private const val KEY_WALLPAPER = "wallpaper"

    fun wallpaper(context: Context): ADWallpaperStyle {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_WALLPAPER, null)
        return raw?.let { runCatching { ADWallpaperStyle.valueOf(it) }.getOrNull() }
            ?: ADWallpaperStyle.DOT_GRID
    }

    fun setWallpaper(context: Context, style: ADWallpaperStyle) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_WALLPAPER, style.name)
            .apply()
    }
}

@Composable
internal fun ADWallpaperBackground(
    style: ADWallpaperStyle,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ADColors.Background),
    ) {
        ADWallpaperCanvas(style)
        content()
    }
}

@Composable
internal fun ADWallpaperCanvas(style: ADWallpaperStyle, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        when (style) {
            ADWallpaperStyle.SOLID -> Unit
            ADWallpaperStyle.DOT_GRID -> {
                val step = 22.dp.toPx()
                val radius = 0.85.dp.toPx()
                var y = 14.dp.toPx()
                while (y < size.height) {
                    var x = 14.dp.toPx()
                    while (x < size.width) {
                        drawCircle(
                            color = Color.White.copy(alpha = 0.055f),
                            radius = radius,
                            center = Offset(x, y),
                        )
                        x += step
                    }
                    y += step
                }
            }
            ADWallpaperStyle.ORBIT -> {
                val center = Offset(size.width * 0.78f, size.height * 0.18f)
                listOf(120.dp, 180.dp, 250.dp).forEachIndexed { index, radius ->
                    drawCircle(
                        color = Color.White.copy(alpha = 0.035f + index * 0.008f),
                        radius = radius.toPx(),
                        center = center,
                        style = Stroke(width = 1.dp.toPx()),
                    )
                }
                drawCircle(
                    color = ADColors.Red.copy(alpha = 0.70f),
                    radius = 2.5.dp.toPx(),
                    center = Offset(center.x - 83.dp.toPx(), center.y + 20.dp.toPx()),
                )
            }
            ADWallpaperStyle.LINES -> {
                val gap = 28.dp.toPx()
                var offset = -size.height
                while (offset < size.width) {
                    drawLine(
                        color = Color.White.copy(alpha = 0.035f),
                        start = Offset(offset, 0f),
                        end = Offset(offset + size.height, size.height),
                        strokeWidth = 1.dp.toPx(),
                    )
                    offset += gap
                }
            }
        }
    }
}
