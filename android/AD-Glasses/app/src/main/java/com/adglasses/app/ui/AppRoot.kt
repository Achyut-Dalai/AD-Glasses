package com.adglasses.app.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

private enum class AppTab(val label: String) {
    Home("Home"),
    Assistant("Assistant"),
    Library("Library"),
}

@Composable
fun ADGlassesRoot(vm: ADViewModel = viewModel()) {
    var showWelcome by remember { mutableStateOf(true) }
    var tab by remember { mutableStateOf(AppTab.Home) }
    var showDeviceCenter by remember { mutableStateOf(false) }
    var showTranslation by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showLens by remember { mutableStateOf(false) }
    var showSoundbite by remember { mutableStateOf(false) }
    val notice by vm.notice.collectAsStateWithLifecycle()
    val busy by vm.busyMessage.collectAsStateWithLifecycle()

    if (showWelcome) {
        WelcomeScreen(
            vm = vm,
            connected = { showWelcome = false },
            connectManually = {
                showWelcome = false
                showDeviceCenter = true
            },
            continueWithoutGlasses = { showWelcome = false },
        )
        return
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                tonalElevation = 0.dp,
                shadowElevation = 8.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .height(58.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppTab.entries.forEach { item ->
                        val icon = when (item) {
                            AppTab.Home -> Icons.Filled.Home
                            AppTab.Assistant -> Icons.Filled.AutoAwesome
                            AppTab.Library -> Icons.Filled.ViewAgenda
                        }
                        NavigationBarItem(
                            selected = tab == item,
                            onClick = { tab = item },
                            icon = {
                                Icon(
                                    icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(21.dp),
                                )
                            },
                            label = {
                                Text(item.label, style = MaterialTheme.typography.labelSmall)
                            },
                            alwaysShowLabel = true,
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = Color.Transparent,
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        when (tab) {
            AppTab.Home -> HomeScreen(
                padding = padding,
                vm = vm,
                openAssistant = { tab = AppTab.Assistant },
                openDeviceCenter = { showDeviceCenter = true },
                openTranslation = { showTranslation = true },
                openLens = { showLens = true },
                openSoundbite = { showSoundbite = true },
                openSettings = { showSettings = true },
            )
            AppTab.Assistant -> AssistantScreen(
                padding = padding,
                vm = vm,
                openSettings = { showSettings = true },
            )
            AppTab.Library -> LibraryScreen(
                padding = padding,
                vm = vm,
                openDeviceCenter = { showDeviceCenter = true },
            )
        }
    }

    if (showDeviceCenter) DeviceCenterDialog(vm = vm, dismiss = { showDeviceCenter = false })
    if (showTranslation) TranslationDialog(vm = vm, dismiss = { showTranslation = false })
    if (showSettings) AISettingsDialog(vm = vm, dismiss = { showSettings = false })
    if (showLens) ProductPreviewDialog(
        title = "Lens",
        message = "Use a still from your glasses to ask what you’re looking at, extract visible text, and understand the scene.",
        dismiss = { showLens = false },
    )
    if (showSoundbite) ProductPreviewDialog(
        title = "Soundbite",
        message = "Capture a spoken thought from your glasses and keep it as a local note you can revisit later.",
        dismiss = { showSoundbite = false },
    )
    if (notice != null || busy != null) {
        StatusOverlay(
            text = busy ?: notice.orEmpty(),
            busy = busy != null,
            dismiss = if (busy == null) vm::clearNotice else null,
        )
    }
}
