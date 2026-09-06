package com.adglasses.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun LibraryScreen(padding: PaddingValues, vm: ADViewModel, openDeviceCenter: () -> Unit) {
    val config by vm.mediaConfig.collectAsStateWithLifecycle()
    val connection by vm.glasses.collectAsStateWithLifecycle()
    Column(
        modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Library", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Original media stays original. Analysis and thumbnails are derivatives.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Outlined.PhotoLibrary, null)
                    Column(Modifier.weight(1f)) {
                        Text("Glasses media", fontWeight = FontWeight.SemiBold)
                        Text(if (connection.isReady) "Ready to sync over the verified local network flow" else "Connect glasses to sync", style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (connection.isReady) Button(onClick = vm::syncMediaConfig, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.CloudDownload, null)
                    Text("  Read media manifest")
                } else OutlinedButton(onClick = openDeviceCenter, modifier = Modifier.fillMaxWidth()) { Text("Connect glasses") }
            }
        }
        config?.let {
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.padding(16.dp)) {
                    Text("media.config", fontWeight = FontWeight.SemiBold)
                    Text(it.take(4_000), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
