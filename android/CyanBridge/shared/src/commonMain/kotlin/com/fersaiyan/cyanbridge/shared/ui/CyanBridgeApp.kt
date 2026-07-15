package com.fersaiyan.cyanbridge.shared.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fersaiyan.cyanbridge.shared.notes.RuleBasedSummarizationService
import com.fersaiyan.cyanbridge.shared.notes.StructuredSummary
import com.fersaiyan.cyanbridge.shared.notes.SummarizationRequest
import com.fersaiyan.cyanbridge.shared.notes.SummaryMarkdownFormatter
import com.fersaiyan.cyanbridge.shared.platform.CyanBridgeSharedBootstrap

/**
 * Root composable for the shared CMP UI.
 * Both Android and iOS render from this composable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CyanBridgeApp() {
    MaterialTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(CyanBridgeSharedBootstrap.applicationName()) }
                )
            }
        ) { padding ->
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    Text(
                        text = "CyanBridge KMP",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Shared Compose Multiplatform UI — working on both Android and iOS.",
                        style = MaterialTheme.typography.bodyLarge,
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Accent profile: ${CyanBridgeSharedBootstrap.defaultAccentProfileId()}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Text(
                        text = "Initial destination: ${CyanBridgeSharedBootstrap.defaultDestinationId()}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "Meeting summary preview",
                        style = MaterialTheme.typography.titleMedium,
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = CyanBridgeSharedBootstrap.meetingSummaryPreviewMarkdown(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
