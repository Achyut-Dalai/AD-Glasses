package com.fersaiyan.cyanbridge.ui.adglasses

import androidx.compose.runtime.Composable
import com.fersaiyan.cyanbridge.shared.glasses.GlassesDashboardUiState

/**
 * Compatibility boundary for the hidden Android runtime during the React Native migration.
 *
 * MainActivity still owns mature HeyCyan transport, sync, hardware callbacks and OTA state, so it
 * retains its existing callback contract for now. React Native is the only product presentation;
 * this composable intentionally renders nothing and must not grow new UI.
 */
@Composable
fun ADGlassesApp(
    @Suppress("UNUSED_PARAMETER") dashboardState: GlassesDashboardUiState,
    @Suppress("UNUSED_PARAMETER") host: ADHostActions,
) = Unit
