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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.fersaiyan.cyanbridge.shared.glasses.GlassesDashboardUiState

@Composable
fun ADGlassesApp(
    dashboardState: GlassesDashboardUiState,
    host: ADHostActions,
) {
    val context = LocalContext.current
    val navigationRequests = remember(context) { ADNavigationRequestStore.observe(context) }
    val externalRequest by navigationRequests.collectAsState()

    var selectedTab by remember { mutableStateOf(ADTab.HOME) }
    var routeStack by remember { mutableStateOf(listOf(ADRoute.MAIN)) }
    var selectedAutomation by remember { mutableStateOf(ADAutomation.TRANSLATOR) }
    var conversationRequest by remember { mutableStateOf<ADNavigationRequest?>(null) }

    val route = routeStack.last()
    val navigateTo: (ADRoute) -> Unit = { destination ->
        if (destination != routeStack.last()) routeStack = routeStack + destination
    }
    val navigateBack = {
        if (routeStack.size > 1) routeStack = routeStack.dropLast(1)
    }
    val showMainTab: (ADTab) -> Unit = { tab ->
        routeStack = listOf(ADRoute.MAIN)
        selectedTab = tab
    }
    val openCapability: (ADAutomation) -> Unit = { automation ->
        selectedAutomation = automation
        navigateTo(ADRoute.TASK_DETAIL)
    }

    LaunchedEffect(externalRequest?.id) {
        val request = externalRequest ?: return@LaunchedEffect
        when (request.destination) {
            ADExternalDestination.CONVERSATIONS -> {
                routeStack = listOf(ADRoute.MAIN)
                selectedTab = ADTab.CHATS
                conversationRequest = request
            }
            ADExternalDestination.SETTINGS -> {
                routeStack = listOf(ADRoute.MAIN, ADRoute.SETTINGS)
            }
            ADExternalDestination.MODES -> {
                routeStack = listOf(ADRoute.MAIN)
                selectedTab = ADTab.AI
            }
            ADExternalDestination.LIBRARY_CAPTURES -> {
                selectedTab = ADTab.LIBRARY
                routeStack = listOf(ADRoute.MAIN, ADRoute.LIBRARY_CAPTURES)
            }
            ADExternalDestination.LIBRARY_RECORDINGS -> {
                selectedTab = ADTab.LIBRARY
                routeStack = listOf(ADRoute.MAIN, ADRoute.LIBRARY_RECORDINGS)
            }
            ADExternalDestination.LIBRARY_NOTES -> {
                selectedTab = ADTab.LIBRARY
                routeStack = listOf(ADRoute.MAIN, ADRoute.LIBRARY_NOTES)
            }
        }
        ADNavigationRequestStore.consume(context, request.id)
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
                            onOpenTranslator = { openCapability(ADAutomation.TRANSLATOR) },
                            onOpenWebSearch = {
                                conversationRequest = ADNavigationRequest(
                                    id = System.currentTimeMillis(),
                                    destination = ADExternalDestination.CONVERSATIONS,
                                    prefill = "Search the web for ",
                                    webSearchRequested = true,
                                )
                                showMainTab(ADTab.CHATS)
                            },
                            onOpenPhoneControl = { showMainTab(ADTab.AI) },
                            onOpenTasks = { showMainTab(ADTab.AI) },
                        )
                        ADTab.CHATS -> ADNativeConversationScreen(
                            navigationRequest = conversationRequest,
                            onNavigationRequestApplied = { requestId ->
                                if (conversationRequest?.id == requestId) conversationRequest = null
                            },
                        )
                        ADTab.AI -> ADNativeAiScreen(
                            onRelaySettings = { navigateTo(ADRoute.AI_RELAY) },
                            onLocalSettings = { navigateTo(ADRoute.AI_LOCAL) },
                            onOpenCapability = openCapability,
                        )
                        ADTab.LIBRARY -> ADNativeLibraryScreen(
                            transferActive = dashboardState.transfer.isVisible,
                            onOpenSync = { navigateTo(ADRoute.SYNC) },
                            onCaptures = { navigateTo(ADRoute.LIBRARY_CAPTURES) },
                            onRecordings = { navigateTo(ADRoute.LIBRARY_RECORDINGS) },
                            onNotes = { navigateTo(ADRoute.LIBRARY_NOTES) },
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
                    ADRoute.SETTINGS -> ADNativeSettingsHubScreen(
                        state = dashboardState,
                        onBack = navigateBack,
                        onDevice = { navigateTo(ADRoute.DEVICE_CENTER) },
                        onPrivacy = { navigateTo(ADRoute.PRIVACY) },
                        onStorage = { navigateTo(ADRoute.STORAGE) },
                        onLanguage = { navigateTo(ADRoute.LANGUAGE) },
                        onPermissions = { navigateTo(ADRoute.PERMISSIONS) },
                        onAdvanced = { navigateTo(ADRoute.ADVANCED) },
                        onAbout = { navigateTo(ADRoute.ABOUT) },
                    )
                    ADRoute.AI_RELAY -> ADNativeRelaySettingsScreen(navigateBack)
                    ADRoute.AI_LOCAL -> ADNativeLocalAiSettingsScreen(navigateBack)
                    ADRoute.PRIVACY -> ADPrivacyCenterScreen(navigateBack)
                    ADRoute.STORAGE -> ADStorageScreen(navigateBack)
                    ADRoute.LANGUAGE -> ADLanguageScreen(navigateBack)
                    ADRoute.PERMISSIONS -> ADPermissionsScreen(navigateBack)
                    ADRoute.ADVANCED -> ADAdvancedScreen(
                        onBack = navigateBack,
                        onDevice = { navigateTo(ADRoute.DEVICE_CENTER) },
                    )
                    ADRoute.ABOUT -> ADAboutScreen(navigateBack)
                    ADRoute.FIRMWARE -> ADFirmwareScreen(dashboardState, host, navigateBack)
                    ADRoute.TASK_DETAIL -> ADNativeTaskDetailScreen(
                        automation = selectedAutomation,
                        initiallyActive = dashboardState.nativePluginShortcut?.title == selectedAutomation.runtimeTitle &&
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
