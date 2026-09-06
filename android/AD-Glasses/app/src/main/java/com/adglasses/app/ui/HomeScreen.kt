package com.adglasses.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Lens
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adglasses.app.core.model.ConnectionPhase

private data class HomeAction(val title: String, val detail: String, val icon: ImageVector, val run: () -> Unit)

@Composable
fun HomeScreen(
    padding: PaddingValues,
    vm: ADViewModel,
    openAssistant: () -> Unit,
    openDeviceCenter: () -> Unit,
    openTranslation: () -> Unit,
) {
    val connection by vm.glasses.collectAsStateWithLifecycle()
    val actions = listOf(
        HomeAction("Ask", "Ask AD by text or voice", Icons.Filled.Mic, openAssistant),
        HomeAction("Translate", "On-device translation", Icons.Filled.Translate, openTranslation),
        HomeAction("Photo", "Capture on glasses", Icons.Filled.CameraAlt, vm::takePhoto),
        HomeAction("Video", "Start recording", Icons.Filled.Videocam, vm::startVideo),
        HomeAction("Audio", "Glasses-local recording", Icons.Filled.GraphicEq, vm::startAudio),
        HomeAction("AI photo", "Capture for visual assistance", Icons.Filled.AutoAwesome, vm::aiPhoto),
    )

    Column(
        modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("AD", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Text("  GLASSES", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = vm::openNotificationAccess) { Icon(Icons.Outlined.Settings, contentDescription = "Settings and notification access") }
        }

        Surface(
            modifier = Modifier.fillMaxWidth().clickable { vm.aiPhoto() },
            shape = RoundedCornerShape(28.dp),
            color = Color.Transparent,
        ) {
            Box(
                modifier = Modifier.background(
                    Brush.linearGradient(listOf(Color(0xFF4338CA), Color(0xFF2563EB), Color(0xFF0891B2)))
                ).padding(22.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Lens, null, tint = Color.White, modifier = Modifier.size(38.dp))
                    Spacer(Modifier.height(40.dp))
                    Text("Lens", color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("See through your glasses, capture context, and ask AD about it.", color = Color.White.copy(alpha = .82f))
                }
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(actions) { action -> FeatureTile(action) }
        }

        Surface(
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp).clickable(onClick = openDeviceCenter),
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 2.dp,
        ) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                if (connection.phase in setOf(ConnectionPhase.Connecting, ConnectionPhase.Discovering, ConnectionPhase.Initializing)) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Outlined.Bluetooth, null)
                }
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Text(connection.deviceName ?: if (connection.isReady) "AD Glasses" else "Glasses", fontWeight = FontWeight.SemiBold)
                    Text(connection.detail ?: connection.phase.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                connection.batteryPercent?.let { Text(if (connection.charging) "$it% ⚡" else "$it%", fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

@Composable
private fun FeatureTile(action: HomeAction) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = action.run),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = .11f)) {
                Icon(action.icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(10.dp).size(22.dp))
            }
            Spacer(Modifier.height(6.dp))
            Text(action.title, fontWeight = FontWeight.SemiBold)
            Text(action.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
