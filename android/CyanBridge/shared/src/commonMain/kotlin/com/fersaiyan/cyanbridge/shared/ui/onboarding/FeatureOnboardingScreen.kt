package com.fersaiyan.cyanbridge.shared.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeatureOnboardingScreen(
    title: String,
    description: String,
    details: String,
    showGlassesConnectionPermission: Boolean,
    glassesConnectionPermissionGranted: Boolean,
    showStoragePermission: Boolean,
    storagePermissionGranted: Boolean,
    showAccessibilityDisclosure: Boolean,
    showOpenSourceContribution: Boolean,
    accessibilityEnabled: Boolean,
    localAgentAutomationEnabled: Boolean,
    backLabel: String,
    nextLabel: String,
    onRequestGlassesConnectionPermission: () -> Unit,
    onRequestStoragePermission: () -> Unit,
    onLocalAgentAutomationChange: (Boolean) -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenSourceRepository: () -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = { TopAppBar(title = { Text("Setup") }) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(description, style = MaterialTheme.typography.bodyLarge)
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = details,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (showGlassesConnectionPermission) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Connect your glasses", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "CyanBridge needs nearby-device access to discover and connect to your glasses. Android may also request location on older phones because it requires it for Bluetooth scanning. Wi-Fi Direct permission is requested later, only when you transfer media.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            if (glassesConnectionPermissionGranted) "Bluetooth access is allowed" else "Bluetooth access has not been granted",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (glassesConnectionPermissionGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedButton(
                            onClick = onRequestGlassesConnectionPermission,
                            enabled = !glassesConnectionPermissionGranted,
                        ) {
                            Text(if (glassesConnectionPermissionGranted) "Permission granted" else "Allow glasses connection")
                        }
                    }
                }
            }
            if (showStoragePermission) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Media and AI files", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "CyanBridge uses file access to save media transferred from your glasses and to manage AI models and related local files. You can continue without allowing it now, but media sync and some AI features will need it later.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            if (storagePermissionGranted) "File access is allowed" else "File access has not been granted",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (storagePermissionGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedButton(
                            onClick = onRequestStoragePermission,
                            enabled = !storagePermissionGranted,
                        ) {
                            Text(if (storagePermissionGranted) "Permission granted" else "Allow media and AI file access")
                        }
                    }
                }
            }
            if (showAccessibilityDisclosure) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Accessibility disclosure", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Local Agent can read on-screen text to perform the actions you explicitly request. It does not run until you enable accessibility access in Android settings.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        ToggleRow(
                            label = "Enable Local Agent automation",
                            checked = localAgentAutomationEnabled,
                            onCheckedChange = onLocalAgentAutomationChange,
                        )
                        Text(
                            text = if (accessibilityEnabled) "Accessibility is currently enabled" else "Accessibility is currently disabled",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (accessibilityEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedButton(onClick = onOpenAccessibilitySettings) {
                            Text("Open accessibility settings")
                        }
                    }
                }
            }
            if (showOpenSourceContribution) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Open source by design", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Inspect how CyanBridge works, report security concerns, suggest improvements, or contribute code. Community review helps make the app safer and better for everyone.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedButton(onClick = onOpenSourceRepository) {
                            Text("Open CyanBridge on GitHub")
                        }
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                OutlinedButton(onClick = onBack) { Text(backLabel) }
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(onClick = onNext) { Text(nextLabel) }
            }
        }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
