package com.adglasses.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.adglasses.app.AppGraph
import com.adglasses.app.core.background.CompanionLinkState

private enum class AppTab(val label: String) { Home("Home"), Assistant("Assistant"), Library("Library") }

@Composable
fun ADGlassesRoot(vm: ADViewModel = viewModel()) {
    var tab by remember { mutableStateOf(AppTab.Home) }
    var showDeviceCenter by remember { mutableStateOf(false) }
    var showTranslation by remember { mutableStateOf(false) }
    val notice by vm.notice.collectAsStateWithLifecycle()
    val busy by vm.busyMessage.collectAsStateWithLifecycle()
    val companionLink by AppGraph.companionPresence.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val communicationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }
    val requestCommunicationAccess = {
        val missing = listOf(
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.SEND_SMS,
        ).filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) communicationPermissionLauncher.launch(missing.toTypedArray())
    }

    val companionConsentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) {
        AppGraph.companionPresence.refresh(vm.glasses.value.address)
    }
    val changeBackgroundLink = {
        val address = vm.glasses.value.address
        if (address == null) {
            showDeviceCenter = true
        } else if (companionLink is CompanionLinkState.Linked) {
            AppGraph.companionPresence.disassociate(address)
        } else if (companionLink !is CompanionLinkState.Unsupported) {
            AppGraph.companionPresence.requestAssociation(address) { intentSender ->
                companionConsentLauncher.launch(
                    IntentSenderRequest.Builder(intentSender).build()
                )
            }
        }
    }

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
                requestCommunicationAccess = requestCommunicationAccess,
                companionLink = companionLink,
                changeBackgroundLink = changeBackgroundLink,
            )
            AppTab.Assistant -> AssistantScreen(padding = padding, vm = vm)
            AppTab.Library -> LibraryScreen(padding = padding, vm = vm, openDeviceCenter = { showDeviceCenter = true })
        }
    }

    if (showDeviceCenter) DeviceCenterDialog(vm = vm, dismiss = { showDeviceCenter = false })
    if (showTranslation) TranslationDialog(vm = vm, dismiss = { showTranslation = false })
    if (notice != null || busy != null) StatusOverlay(text = busy ?: notice.orEmpty(), busy = busy != null, dismiss = if (busy == null) vm::clearNotice else null)
}
