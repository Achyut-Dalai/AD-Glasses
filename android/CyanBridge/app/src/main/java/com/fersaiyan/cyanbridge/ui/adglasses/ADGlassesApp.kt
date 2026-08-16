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
                if (route == ADRoute.MAIN) ADBottomNavigation(selectedTab) { selectedTab = it }
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
                        ADTab.ASSISTANT -> ADNativeConversationScreen(
                            onVoiceQuestion = host.onVoiceQuestion,
                            onImageQuestion = host.onImageQuestion,
                        )
                        ADTab.LIBRARY -> ADNativeLibraryScreen(
                            transferActive = dashboardState.transfer.isVisible,
                            onOpenSync = { navigateTo(ADRoute.SYNC) },
                            onCaptures = { navigateTo(ADRoute.LIBRARY_CAPTURES) },
                            onRecordings = { navigateTo(ADRoute.LIBRARY_RECORDINGS) },
                            onNotes = { navigateTo(ADRoute.LIBRARY_NOTES) },
                        )
                        ADTab.AUTOMATIONS -> ADModesScreen(
                            activeShortcutTitle = dashboardState.nativePluginShortcut
                                ?.takeIf { it.isEnabled }
                                ?.title,
                            onMode = {
                                selectedAutomation = it
                                navigateTo(ADRoute.AUTOMATION_DETAIL)
                            },
                        )
                    }
                    ADRoute.DEVICE_CENTER -> ADGlassesDeviceCenterScreen(
                        state = dashboardState,
                        host = host,
                        onBack = navigateBack,
                        onSync = { navigateTo(ADRoute.SYNC) },
                        onFirmware = { navigateTo(ADRoute.FIRMWARE) },
                        onAdvanced = { navigateTo(ADRoute.ADVANCED) },
                    )
                    ADRoute.SYNC -> ADSyncScreen(dashboardState, host, navigateBack)
                    ADRoute.SETTINGS -> ADSettingsHubScreen(
                        state = dashboardState,
                        onBack = navigateBack,
                        onDevice = { navigateTo(ADRoute.DEVICE_CENTER) },
                        onIntelligence = { navigateTo(ADRoute.AI_SERVICES) },
                        onRouting = { navigateTo(ADRoute.ROUTING) },
                        onPrivacy = { navigateTo(ADRoute.PRIVACY) },
                        onStorage = { navigateTo(ADRoute.STORAGE) },
                        onLanguage = { navigateTo(ADRoute.LANGUAGE) },
                        onPermissions = { navigateTo(ADRoute.PERMISSIONS) },
                        onAdvanced = { navigateTo(ADRoute.ADVANCED) },
                        onAbout = { navigateTo(ADRoute.ABOUT) },
                    )
                    ADRoute.AI_SERVICES -> ADIntelligenceScreen(
                        onBack = navigateBack,
                        onRouting = { navigateTo(ADRoute.ROUTING) },
                    )
                    ADRoute.ROUTING -> ADRoutingScreen(navigateBack)
                    ADRoute.PRIVACY -> ADPrivacyCenterScreen(navigateBack)
                    ADRoute.STORAGE -> ADStorageScreen(navigateBack)
                    ADRoute.LANGUAGE -> ADLanguageScreen(navigateBack)
                    ADRoute.PERMISSIONS -> ADPermissionsScreen(navigateBack)
                    ADRoute.ADVANCED -> ADAdvancedCenterScreen(
                        onBack = navigateBack,
                        onDevice = { navigateTo(ADRoute.DEVICE_CENTER) },
                    )
                    ADRoute.ABOUT -> ADAboutScreen(navigateBack)
                    ADRoute.FIRMWARE -> ADFirmwareScreen(dashboardState, host, navigateBack)
                    ADRoute.AUTOMATION_DETAIL -> ADNativeModeDetailScreen(
                        automation = selectedAutomation,
                        initiallyActive = dashboardState.nativePluginShortcut?.title == selectedAutomation.title &&
                            dashboardState.nativePluginShortcut?.isEnabled == true,
                        onBack = navigateBack,
                    )
                    ADRoute.LIBRARY_CAPTURES -> ADNativeCapturesScreen(
                        onBack = navigateBack,
                        onOpenSync = { navigateTo(ADRoute.SYNC) },
                    )
                    ADRoute.LIBRARY_RECORDINGS -> ADNativeRecordingsScreen(navigateBack)
                    ADRoute.LIBRARY_NOTES -> ADNativeNotesScreen(navigateBack)
                }
            }
        }
    }
}
