package com.ad_glasses.ui.adglasses

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.MaterialTheme
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
import com.ad_glasses.shared.glasses.GlassesDashboardUiState

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
    var conversationRequest by remember { mutableStateOf<ADNavigationRequest?>(null) }
    var onboardingComplete by remember(context) { mutableStateOf(ADWelcomePreferences.isComplete(context)) }

    val route = routeStack.last()
    val navigateTo: (ADRoute) -> Unit = { destination ->
        if (destination != routeStack.last()) routeStack = routeStack + destination
    }
    val navigateBack = { if (routeStack.size > 1) routeStack = routeStack.dropLast(1) }

    LaunchedEffect(externalRequest?.id) {
        val request = externalRequest ?: return@LaunchedEffect
        when (request.destination) {
            ADExternalDestination.CONVERSATIONS -> {
                routeStack = listOf(ADRoute.MAIN)
                selectedTab = ADTab.AI
                conversationRequest = request
            }
            ADExternalDestination.SETTINGS -> routeStack = listOf(ADRoute.MAIN, ADRoute.SETTINGS)
            ADExternalDestination.AI -> {
                routeStack = listOf(ADRoute.MAIN, ADRoute.DEVICE_CENTER)
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

    BackHandler(enabled = onboardingComplete && route != ADRoute.MAIN) { navigateBack() }

    ADGlassesTheme {
        if (!onboardingComplete) {
            ADWelcomeScreen(
                onStartSetup = {
                    ADWelcomePreferences.markComplete(context)
                    onboardingComplete = true
                    host.onOpenDeviceSetup()
                },
                onExplore = {
                    ADWelcomePreferences.markComplete(context)
                    onboardingComplete = true
                },
            )
            return@ADGlassesTheme
        }

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
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
                        )
                        ADTab.AI -> ADNativeConversationScreen(
                            navigationRequest = conversationRequest,
                            onNavigationRequestApplied = { requestId ->
                                if (conversationRequest?.id == requestId) conversationRequest = null
                            },
                        )
                        ADTab.LIBRARY -> ADExpressiveLibraryHome(
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
                        onCloudSettings = { navigateTo(ADRoute.AI_CLOUD) },
                        onLocalSettings = { navigateTo(ADRoute.AI_LOCAL) },
                    )
                    ADRoute.SYNC -> ADSyncScreen(dashboardState, host, navigateBack)
                    ADRoute.SETTINGS -> ADNativeSettingsHubScreen(
                        state = dashboardState,
                        onBack = navigateBack,
                        onDevice = { navigateTo(ADRoute.DEVICE_CENTER) },
                        onPrivacy = { navigateTo(ADRoute.PRIVACY) },
                        onStorage = { navigateTo(ADRoute.STORAGE) },
                        onLanguage = {
                            if (Build.VERSION.SDK_INT >= 33) {
                                val opened = runCatching {
                                    context.startActivity(
                                        Intent(Settings.ACTION_APP_LOCALE_SETTINGS, Uri.parse("package:${context.packageName}")),
                                    )
                                }.isSuccess
                                if (!opened) navigateTo(ADRoute.LANGUAGE)
                            } else navigateTo(ADRoute.LANGUAGE)
                        },
                        onPermissions = { navigateTo(ADRoute.PERMISSIONS) },
                        onAbout = { navigateTo(ADRoute.ABOUT) },
                    )
                    ADRoute.AI_CLOUD -> ADNativeCloudAiSettingsScreen(navigateBack)
                    ADRoute.AI_LOCAL -> ADNativeLocalAiSettingsScreen(navigateBack)
                    ADRoute.PRIVACY -> ADPrivacyCenterScreen(navigateBack)
                    ADRoute.STORAGE -> ADStorageScreen(navigateBack)
                    ADRoute.LANGUAGE -> ADLanguageScreen(navigateBack)
                    ADRoute.PERMISSIONS -> ADPermissionsScreen(navigateBack)
                    ADRoute.ABOUT -> ADMinimalAboutScreen(navigateBack)
                    ADRoute.FIRMWARE -> ADFirmwareScreen(dashboardState, host, navigateBack)
                    ADRoute.LIBRARY_CAPTURES -> ADNativeCapturesScreen(
                        onBack = navigateBack,
                        onOpenSync = { navigateTo(ADRoute.SYNC) },
                        onAnalyzeMedia = host.onAnalyzeMedia,
                    )
                    ADRoute.LIBRARY_RECORDINGS -> ADNativeRecordingsScreen(navigateBack)
                    ADRoute.LIBRARY_NOTES -> ADNativeNotesScreen(navigateBack)
                }
            }
        }
    }
}
