package com.fersaiyan.cyanbridge.ui.adglasses

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.fersaiyan.cyanbridge.shared.glasses.GlassesDashboardUiState

@Composable
fun ADGlassesApp(
    dashboardState: GlassesDashboardUiState,
    host: ADHostActions,
) {
    var selectedTab by remember { mutableStateOf(ADTab.HOME) }
    var routeStack by remember { mutableStateOf(listOf(ADRoute.MAIN)) }
    var selectedAutomation by remember { mutableStateOf(ADAutomation.LOCAL_AGENT) }

    val route = routeStack.last()
    val navigateTo: (ADRoute) -> Unit = { destination ->
        if (destination != routeStack.last()) routeStack = routeStack + destination
    }
    val navigateBack = {
        if (routeStack.size > 1) routeStack = routeStack.dropLast(1)
    }

    BackHandler(enabled = route != ADRoute.MAIN) { navigateBack() }

    ADGlassesTheme {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = ADColors.Background,
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = { if (route == ADRoute.MAIN) ADBottomNavigation(selectedTab) { selectedTab = it } },
        ) { innerPadding ->
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding).consumeWindowInsets(innerPadding),
            ) {
                when (route) {
                    ADRoute.MAIN -> when (selectedTab) {
                        ADTab.HOME -> ADHomeSurface(
                            state = dashboardState,
                            host = host,
                            onOpenDevice = { navigateTo(ADRoute.DEVICE_CENTER) },
                            onOpenSync = { navigateTo(ADRoute.SYNC) },
                            onOpenSettings = { navigateTo(ADRoute.SETTINGS) },
                            onOpenConversations = { selectedTab = ADTab.ASSISTANT },
                            onOpenLibrary = { selectedTab = ADTab.LIBRARY },
                            onOpenModes = { selectedTab = ADTab.AUTOMATIONS },
                        )
                        ADTab.ASSISTANT -> ADNativeConversationScreen(host = host)
                        ADTab.LIBRARY -> ADNativeLibraryScreen(
                            transferActive = dashboardState.transfer.isVisible,
                            onOpenSync = { navigateTo(ADRoute.SYNC) },
                            onCaptures = { navigateTo(ADRoute.LIBRARY_CAPTURES) },
                            onRecordings = { navigateTo(ADRoute.LIBRARY_RECORDINGS) },
                            onNotes = { navigateTo(ADRoute.LIBRARY_NOTES) },
                        )
                        ADTab.AUTOMATIONS -> ADModesScreen(
                            activeShortcutTitle = dashboardState.nativePluginShortcut?.takeIf { it.isEnabled }?.title,
                            onMode = { selectedAutomation = it; navigateTo(ADRoute.AUTOMATION_DETAIL) },
                        )
                    }
                    ADRoute.LIBRARY_CAPTURES -> ADNativeCapturesScreen(navigateBack) { navigateTo(ADRoute.SYNC) }
                    ADRoute.LIBRARY_RECORDINGS -> ADNativeRecordingsScreen(navigateBack)
                    ADRoute.LIBRARY_NOTES -> ADNativeNotesScreen(navigateBack)
                    else -> ADLegacyRouteContent(
                        route = route,
                        dashboardState = dashboardState,
                        host = host,
                        navigateTo = navigateTo,
                        navigateBack = navigateBack,
                        selectedAutomation = selectedAutomation,
                    )
                }
            }
        }
    }
}
