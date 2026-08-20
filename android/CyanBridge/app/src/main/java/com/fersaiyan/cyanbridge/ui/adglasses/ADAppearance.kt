package com.fersaiyan.cyanbridge.ui.adglasses

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.fersaiyan.cyanbridge.R

enum class ADWallpaperStyle(
    val label: String,
    val drawableRes: Int,
) {
    GREY("Grey", R.drawable.ad_wallpaper_grey),
}

internal object ADWallpaperPreferences {
    private const val PREFS = "ad_glasses_appearance"
    private const val KEY_WALLPAPER = "wallpaper"

    fun get(context: Context): ADWallpaperStyle {
        val stored = preferences(context).getString(KEY_WALLPAPER, ADWallpaperStyle.GREY.name)
        return ADWallpaperStyle.entries.firstOrNull { it.name == stored } ?: ADWallpaperStyle.GREY
    }

    fun set(context: Context, style: ADWallpaperStyle) {
        preferences(context).edit().putString(KEY_WALLPAPER, style.name).apply()
    }

    fun preferences(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/**
 * Product backdrop selected from the user-provided wallpaper set.
 *
 * Artwork stays in drawable-nodpi so Android does not density-resample it before Compose
 * lays it out. A light black scrim protects text contrast while keeping the image visible.
 */
@Composable
internal fun ADWallpaperBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val context = LocalContext.current
    var wallpaper by remember(context) { mutableStateOf(ADWallpaperPreferences.get(context)) }

    DisposableEffect(context) {
        val preferences = ADWallpaperPreferences.preferences(context)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            wallpaper = ADWallpaperPreferences.get(context)
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        Image(
            painter = painterResource(wallpaper.drawableRes),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.24f)))
        content()
    }
}
