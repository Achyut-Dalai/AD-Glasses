package com.adglasses.app.ui

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CenterFocusWeak
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adglasses.app.core.model.ConnectionPhase
import java.util.Locale

private data class HomeFeature(
    val title: String,
    val detail: String,
    val icon: ImageVector,
    val tint: Color,
    val action: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    padding: PaddingValues,
    vm: ADViewModel,
    openAssistant: () -> Unit,
    openDeviceCenter: () -> Unit,
    openTranslation: () -> Unit,
    openLens: () -> Unit,
    openSoundbite: () -> Unit,
    openSettings: () -> Unit,
) {
    val connection by vm.glasses.collectAsStateWithLifecycle()
    var voiceActive by remember { mutableStateOf(false) }
    val speechLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        voiceActive = false
        if (result.resultCode == Activity.RESULT_OK) {
            val text = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                .orEmpty()
            if (text.isNotBlank()) {
                vm.sendPhoneVoiceMessage(text)
                openAssistant()
            }
        }
    }
    val askByVoice = {
        voiceActive = true
        speechLauncher.launch(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Ask AD")
            },
        )
    }

    val features = listOf(
        HomeFeature(
            "Ask",
            if (voiceActive) "Listening · sends automatically" else "Ask by voice",
            Icons.Filled.Mic,
            if (voiceActive) ADAccent.Red else ADAccent.Indigo,
            askByVoice,
        ),
        HomeFeature("Photo", "Take a photo", Icons.Filled.CameraAlt, ADAccent.Teal, vm::takePhoto),
        HomeFeature("Video", "Record from glasses", Icons.Filled.Videocam, ADAccent.Pink, vm::startVideo),
        HomeFeature("Translate", "Live conversation", Icons.Filled.Translate, ADAccent.Indigo, openTranslation),
        HomeFeature("Soundbites", "Turn speech into notes", Icons.Filled.FormatQuote, ADAccent.Orange, openSoundbite),
        HomeFeature("Audio", "Record from glasses", Icons.Filled.GraphicEq, ADAccent.Red, vm::startAudio),
    )

    Scaffold(
        modifier = Modifier.padding(padding),
        containerColor = Color.Transparent,
        topBar = {
            CenterAlignedTopAppBar(
                title = { ADBrandWordmark() },
                actions = {
                    IconButton(onClick = openSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                ),
            )
        },
    ) { inner ->
        ADAmbientBackground(Modifier.padding(inner)) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val fontScale = LocalDensity.current.fontScale
                val largeText = fontScale >= 1.15f
                val columnCount = when {
                    largeText -> 1
                    maxWidth < 350.dp -> 1
                    maxWidth < 620.dp -> 2
                    else -> 3
                }
                val compactLens = maxWidth < 410.dp || largeText
                val stackedLens = maxWidth < 350.dp || fontScale >= 1.30f

                LazyVerticalGrid(
                    columns = GridCells.Fixed(columnCount),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .widthIn(max = 720.dp)
                        .fillMaxWidth()
                        .fillMaxHeight(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 8.dp,
                        bottom = 84.dp,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        LensTile(
                            onClick = openLens,
                            compact = compactLens,
                            stacked = stackedLens,
                        )
                    }
                    items(features, key = { it.title }) { feature ->
                        HomeFeatureTile(feature)
                    }
                }
            }

            ConnectionPill(
                connection = connection,
                onClick = openDeviceCenter,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun ADBrandWordmark() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text("AD", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
        Text(
            "GLASSES",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.7.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LensTile(onClick: () -> Unit, compact: Boolean, stacked: Boolean) {
    ADGlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (stacked) 190.dp else if (compact) 142.dp else 148.dp),
        cornerRadius = 26.dp,
        onClick = onClick,
    ) {
        if (stacked) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                LensCopy()
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    LensVisionField(compact = true)
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (compact) 16.dp else 18.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 16.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    LensCopy()
                }
                LensVisionField(compact = compact)
            }
        }
    }
}

@Composable
private fun LensCopy() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Look. Ask. Understand.",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            "Explore what’s in front of you.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LensVisionField(compact: Boolean) {
    val transition = rememberInfiniteTransition(label = "lens-scan")
    val scanExtent = if (compact) 20f else 26f
    val scanOffset by transition.animateFloat(
        initialValue = -scanExtent,
        targetValue = scanExtent,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1750),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "lens-scan-offset",
    )

    val fieldWidth = if (compact) 100.dp else 126.dp
    val fieldHeight = if (compact) 92.dp else 112.dp
    val halo = if (compact) 96.dp else 124.dp
    val focus = if (compact) 60.dp else 76.dp
    val center = if (compact) 46.dp else 58.dp
    val camera = if (compact) 25.dp else 31.dp
    val scanWidth = if (compact) 62.dp else 78.dp

    Box(
        modifier = Modifier.size(width = fieldWidth, height = fieldHeight),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(halo)
                .background(
                    Brush.radialGradient(
                        listOf(
                            ADAccent.Indigo.copy(alpha = 0.24f),
                            ADAccent.Purple.copy(alpha = 0.12f),
                            Color.Transparent,
                        ),
                    ),
                    CircleShape,
                ),
        )
        Icon(
            Icons.Filled.CenterFocusWeak,
            contentDescription = null,
            tint = ADAccent.Indigo.copy(alpha = 0.34f),
            modifier = Modifier.size(focus),
        )
        Surface(
            modifier = Modifier.size(center),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.24f),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
            ),
        ) {}
        Icon(
            Icons.Filled.CameraAlt,
            contentDescription = null,
            modifier = Modifier.size(camera),
        )
        Box(
            Modifier
                .offset(y = scanOffset.dp)
                .size(width = scanWidth, height = 2.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, ADAccent.Indigo.copy(alpha = 0.72f), Color.Transparent),
                    ),
                    RoundedCornerShape(1.dp),
                ),
        )
    }
}

@Composable
private fun HomeFeatureTile(feature: HomeFeature) {
    ADGlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 112.dp),
        cornerRadius = 20.dp,
        onClick = feature.action,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(11.dp),
                color = feature.tint.copy(alpha = 0.10f),
                modifier = Modifier.size(38.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        feature.icon,
                        contentDescription = null,
                        tint = feature.tint,
                        modifier = Modifier.size(21.dp),
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    feature.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    feature.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                )
            }
        }
    }
}

@Composable
private fun ConnectionPill(
    connection: com.adglasses.app.core.model.GlassesConnectionState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ADGlassSurface(
        onClick = onClick,
        modifier = modifier.widthIn(max = 340.dp),
        cornerRadius = 100.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            when (connection.phase) {
                ConnectionPhase.Ready -> {
                    Box(Modifier.size(8.dp).background(ADAccent.Green, CircleShape))
                    Text("Connected", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                    connection.batteryPercent?.let { battery ->
                        Text("·", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Icon(Icons.Outlined.BatteryFull, null, Modifier.size(16.dp))
                        Text("$battery%", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                    }
                }
                ConnectionPhase.Scanning -> {
                    CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 1.8.dp)
                    Text("Finding glasses", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                }
                ConnectionPhase.Connecting,
                ConnectionPhase.Discovering,
                ConnectionPhase.Initializing -> {
                    CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 1.8.dp)
                    Text("Connecting", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                }
                ConnectionPhase.Disconnected,
                ConnectionPhase.Error -> {
                    Box(Modifier.size(8.dp).background(ADAccent.Red, CircleShape))
                    Text("Connect", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
