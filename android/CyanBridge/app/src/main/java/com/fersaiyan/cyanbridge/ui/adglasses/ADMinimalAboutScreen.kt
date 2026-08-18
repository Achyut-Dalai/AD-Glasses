package com.fersaiyan.cyanbridge.ui.adglasses

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.fersaiyan.cyanbridge.BuildConfig

@Composable
internal fun ADMinimalAboutScreen(onBack: () -> Unit) {
    ADPageLayout("About", onBack) {
        Text(
            text = "version ${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.titleMedium,
        )
    }
}
