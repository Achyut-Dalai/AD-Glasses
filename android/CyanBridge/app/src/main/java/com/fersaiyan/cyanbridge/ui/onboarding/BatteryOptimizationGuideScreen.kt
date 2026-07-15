package com.fersaiyan.cyanbridge.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatteryOptimizationGuideScreen(
    optimizationIgnored: Boolean,
    onDisableOptimization: () -> Unit,
    onOpenAppInfo: () -> Unit,
    onOpenOptimizationList: () -> Unit,
    onDone: () -> Unit,
    onSkip: () -> Unit,
) {
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = { TopAppBar(title = { Text("Battery optimization") }) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Keep CyanBridge running", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "Android battery optimization can interrupt Bluetooth, media sync, local automation, and meeting capture.",
                style = MaterialTheme.typography.bodyLarge,
            )
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = if (optimizationIgnored) "Battery optimization is disabled for CyanBridge." else "Battery optimization is currently enabled for CyanBridge.",
                    modifier = Modifier.padding(16.dp),
                    color = if (optimizationIgnored) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalButton(onClick = onDisableOptimization, modifier = Modifier.fillMaxWidth()) {
                Text("Disable battery optimization")
            }
            OutlinedButton(onClick = onOpenAppInfo, modifier = Modifier.fillMaxWidth()) {
                Text("Open app info")
            }
            OutlinedButton(onClick = onOpenOptimizationList, modifier = Modifier.fillMaxWidth()) {
                Text("Open battery optimization list")
            }
            Spacer(Modifier.weight(1f))
            FilledTonalButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Done") }
            OutlinedButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) { Text("Skip for now") }
        }
    }
}
