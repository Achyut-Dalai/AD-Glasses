package com.fersaiyan.cyanbridge.shared.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.fersaiyan.cyanbridge.shared.generated.resources.Res
import com.fersaiyan.cyanbridge.shared.generated.resources.onboarding_battery_body
import com.fersaiyan.cyanbridge.shared.generated.resources.onboarding_battery_disabled
import com.fersaiyan.cyanbridge.shared.generated.resources.onboarding_battery_enabled
import com.fersaiyan.cyanbridge.shared.generated.resources.onboarding_battery_headline
import com.fersaiyan.cyanbridge.shared.generated.resources.onboarding_battery_list
import com.fersaiyan.cyanbridge.shared.generated.resources.onboarding_battery_title
import com.fersaiyan.cyanbridge.shared.generated.resources.onboarding_continue
import com.fersaiyan.cyanbridge.shared.generated.resources.onboarding_disable_battery
import com.fersaiyan.cyanbridge.shared.generated.resources.onboarding_dont_show_again
import com.fersaiyan.cyanbridge.shared.generated.resources.onboarding_lock_recents_body
import com.fersaiyan.cyanbridge.shared.generated.resources.onboarding_lock_recents_title
import com.fersaiyan.cyanbridge.shared.generated.resources.onboarding_not_now
import com.fersaiyan.cyanbridge.shared.generated.resources.onboarding_open_app_info
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class, ExperimentalResourceApi::class)
@Composable
fun BatteryOptimizationGuideScreen(
    optimizationIgnored: Boolean,
    onDisableOptimization: () -> Unit,
    onOpenAppInfo: () -> Unit,
    onOpenOptimizationList: () -> Unit,
    onContinue: () -> Unit,
    onRemindLater: () -> Unit,
    onDontShowAgain: () -> Unit,
) {
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = { TopAppBar(title = { Text(stringResource(Res.string.onboarding_battery_title)) }) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(stringResource(Res.string.onboarding_battery_headline), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                stringResource(Res.string.onboarding_battery_body),
                style = MaterialTheme.typography.bodyLarge,
            )
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = if (optimizationIgnored) {
                        stringResource(Res.string.onboarding_battery_disabled)
                    } else {
                        stringResource(Res.string.onboarding_battery_enabled)
                    },
                    modifier = Modifier.padding(16.dp),
                    color = if (optimizationIgnored) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalButton(onClick = onDisableOptimization, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.onboarding_disable_battery))
            }
            OutlinedButton(onClick = onOpenOptimizationList, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.onboarding_battery_list))
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(stringResource(Res.string.onboarding_lock_recents_title), style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(Res.string.onboarding_lock_recents_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = onOpenAppInfo, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(Res.string.onboarding_open_app_info))
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            FilledTonalButton(onClick = onContinue, modifier = Modifier.fillMaxWidth()) { Text(stringResource(Res.string.onboarding_continue)) }
            OutlinedButton(onClick = onRemindLater, modifier = Modifier.fillMaxWidth()) { Text(stringResource(Res.string.onboarding_not_now)) }
            OutlinedButton(onClick = onDontShowAgain, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.onboarding_dont_show_again))
            }
        }
    }
}
