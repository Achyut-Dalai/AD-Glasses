package com.fersaiyan.cyanbridge.shared.ui.glasses

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.fersaiyan.cyanbridge.shared.glasses.GlassesSyncFlow
import com.fersaiyan.cyanbridge.shared.ui.localizedSyncFlowDescription
import com.fersaiyan.cyanbridge.shared.ui.localizedSyncFlowLabel
import com.fersaiyan.cyanbridge.shared.generated.resources.Res
import com.fersaiyan.cyanbridge.shared.generated.resources.action_cancel
import com.fersaiyan.cyanbridge.shared.generated.resources.sync_flow_title
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.stringResource

/** Compose-owned picker for the existing Android media-sync handlers. */
@OptIn(ExperimentalResourceApi::class)
@Composable
fun GlassesSyncFlowPickerDialog(
    onDismissRequest: () -> Unit,
    onFlowSelected: (GlassesSyncFlow) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(Res.string.sync_flow_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassesSyncFlow.entries.forEach { flow ->
                    OutlinedButton(
                        onClick = { onFlowSelected(flow) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("sync_flow_${flow.name}"),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                        ) {
                            Text(localizedSyncFlowLabel(flow), style = MaterialTheme.typography.labelLarge)
                            Text(
                                text = localizedSyncFlowDescription(flow),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(Res.string.action_cancel))
            }
        },
    )
}
