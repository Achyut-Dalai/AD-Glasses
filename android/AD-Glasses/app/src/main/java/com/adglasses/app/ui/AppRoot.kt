package com.adglasses.app.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

private enum class AppTab(val label: String) { Home("Home"), Assistant("Assistant"), Library("Library") }

@Composable
fun ADGlassesRoot(vm: ADViewModel = viewModel()) {
    var tab by remember { mutableStateOf(AppTab.Home) }
    var showDeviceCenter by remember { mutableStateOf(false) }
    var showTranslation by remember { mutableStateOf(false) }
    val notice by vm.notice.collectAsStateWithLifecycle()
    val busy by vm.busyMessage.collectAsStateWithLifecycle()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == AppTab.Home,
                    onClick = { tab = AppTab.Home },
                    icon = { Icon(Icons.Outlined.Home, null) },
                    label = { Text(AppTab.Home.label) },
                )
                NavigationBarItem(
                    selected = tab == AppTab.Assistant,
                    onClick = { tab = AppTab.Assistant },
                    icon = { Icon(Icons.Outlined.AutoAwesome, null) },
                    label = { Text(AppTab.Assistant.label) },
                )
                NavigationBarItem(
                    selected = tab == AppTab.Library,
                    onClick = { tab = AppTab.Library },
                    icon = { Icon(Icons.Outlined.PhotoLibrary, null) },
                    label = { Text(AppTab.Library.label) },
                )
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
            )
            AppTab.Assistant -> AssistantScreen(padding = padding, vm = vm)
            AppTab.Library -> LibraryScreen(padding = padding, vm = vm, openDeviceCenter = { showDeviceCenter = true })
        }
    }

    if (showDeviceCenter) DeviceCenterDialog(vm = vm, dismiss = { showDeviceCenter = false })
    if (showTranslation) TranslationDialog(vm = vm, dismiss = { showTranslation = false })
    if (notice != null || busy != null) StatusOverlay(text = busy ?: notice.orEmpty(), busy = busy != null, dismiss = if (busy == null) vm::clearNotice else null)
}
