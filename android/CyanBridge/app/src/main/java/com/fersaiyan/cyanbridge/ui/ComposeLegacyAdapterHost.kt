package com.fersaiyan.cyanbridge.ui

import android.view.View
import android.widget.FrameLayout
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import com.fersaiyan.cyanbridge.ui.appearance.AppearancePreferences
import com.fersaiyan.cyanbridge.ui.appearance.rememberAppearanceSettings
import com.fersaiyan.cyanbridge.ui.theme.CyanBridgeTheme

/**
 * Hosts a Compose screen while preserving a hidden View tree for mature Android handlers.
 * The adapter is never part of the visible UI; it only supplies the existing state and effects.
 */
internal fun AppCompatActivity.installComposeHostWithLegacyAdapter(
    @LayoutRes legacyAdapterLayout: Int,
): ComposeView {
    val root = FrameLayout(this)
    val legacyAdapter = layoutInflater.inflate(legacyAdapterLayout, root, false).apply {
        visibility = View.GONE
    }
    root.addView(
        legacyAdapter,
        FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
        ),
    )

    return ComposeView(this).also { composeView ->
        root.addView(
            composeView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        setContentView(root)
    }
}

internal fun AppCompatActivity.setThemedComposeContent(
    composeView: ComposeView,
    content: @Composable () -> Unit,
) {
    val appearancePreferences = AppearancePreferences(this)
    composeView.setContent {
        val appearance by rememberAppearanceSettings(appearancePreferences)
        CyanBridgeTheme(appearance, content)
    }
}
