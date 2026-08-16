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
            bottomBar = {
                if (route == ADRoute.MAIN) {
                    ADBottomNavigation(selectedTab) { selectedTab = it }
                }
            },
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding),
            ) {
                when (route) {
                    ADRoute.MAIN -> when (selectedTab) {
                        ADTab.HOME -> ADHomeScreen(
                            state = dashboardState,
                            host = host,
                            onOpenDevice = { navigateTo(ADRoute.DEVICE_CENTER) },
                            onOpenSync = { navigateTo(ADRoute.SYNC) },
                            onOpenSettings = { navigateTo(ADRoute.SETTINGS) },
                            onOpenAssistant = { selectedTab = ADTab.ASSISTANT },
                            onOpenLibrary = { selectedTab = ADTab.LIBRARY },
                        )
                        ADTab.ASSISTANT -> ADAssistantScreen(host = host)
                        ADTab.LIBRARY -> ADLibraryScreen(
                            host = host,
                            transferActive = dashboardState.transfer.isVisible,
                            onOpenSync = { navigateTo(ADRoute.SYNC) },
                        )
                        ADTab.AUTOMATIONS -> ADAutomationsScreen(
                            activeShortcutTitle = dashboardState.nativePluginShortcut
                                ?.takeIf { it.isEnabled }
                                ?.title,
                            onAutomation = {
                                selectedAutomation = it
                                navigateTo(ADRoute.AUTOMATION_DETAIL)
                            },
                        )
                    }
                    ADRoute.DEVICE_CENTER -> ADDeviceCenterScreen(
                        state = dashboardState,
                        host = host,
                        onBack = navigateBack,
                        onSync = { navigateTo(ADRoute.SYNC) },
                        onFirmware = { navigateTo(ADRoute.FIRMWARE) },
                        onAdvanced = { navigateTo(ADRoute.ADVANCED) },
                    )
                    ADRoute.SYNC -> ADSyncScreen(dashboardState, host, navigateBack)
                    ADRoute.SETTINGS -> ADSettingsScreen(
                        state = dashboardState,
                        onBack = navigateBack,
                        onDevice = { navigateTo(ADRoute.DEVICE_CENTER) },
                        onAi = { navigateTo(ADRoute.AI_SERVICES) },
                        onPrivacy = { navigateTo(ADRoute.PRIVACY) },
                        onAdvanced = { navigateTo(ADRoute.ADVANCED) },
                        onLegacySettings = host.onOpenLegacySettings,
                    )
                    ADRoute.AI_SERVICES -> ADAiServicesScreen(navigateBack, host)
                    ADRoute.PRIVACY -> ADPrivacyScreen(navigateBack, host)
                    ADRoute.ADVANCED -> ADAdvancedScreen(navigateBack, host)
                    ADRoute.FIRMWARE -> ADFirmwareScreen(dashboardState, host, navigateBack)
                    ADRoute.AUTOMATION_DETAIL -> ADAutomationDetailScreen(
                        automation = selectedAutomation,
                        isActive = dashboardState.nativePluginShortcut?.title == selectedAutomation.title &&
                            dashboardState.nativePluginShortcut?.isEnabled == true,
                        onBack = navigateBack,
                        onConfigure = { host.onOpenAutomationSettings(selectedAutomation) },
                    )
                }
            }
        }
    }
}
