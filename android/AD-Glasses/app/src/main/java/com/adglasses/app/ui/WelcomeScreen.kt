package com.adglasses.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adglasses.app.R
import kotlinx.coroutines.delay

private enum class WelcomePhase { Connecting, Choice }

@Composable
internal fun WelcomeScreen(
    vm: ADViewModel,
    connectManually: () -> Unit,
    continueWithoutGlasses: () -> Unit,
    connected: () -> Unit,
) {
    val connection by vm.glasses.collectAsStateWithLifecycle()
    var phase by remember { mutableStateOf(WelcomePhase.Connecting) }

    LaunchedEffect(Unit) {
        delay(900)
        if (vm.glasses.value.isReady) connected() else phase = WelcomePhase.Choice
    }
    LaunchedEffect(connection.isReady) {
        if (connection.isReady) {
            delay(280)
            connected()
        }
    }

    ADAmbientBackground(strong = true) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val compact = maxHeight < 720.dp || maxWidth < 360.dp
            val heroHeight = if (compact) 205.dp else 245.dp
            val sectionGap = if (compact) 14.dp else 20.dp
            val headlineStyle = if (compact) {
                MaterialTheme.typography.headlineMedium
            } else {
                MaterialTheme.typography.headlineLarge
            }

            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .fillMaxSize()
                    .systemBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = if (compact) 10.dp else 18.dp),
                verticalArrangement = Arrangement.spacedBy(sectionGap),
            ) {
                Image(
                    painter = painterResource(R.drawable.ad_brand_icon),
                    contentDescription = "AD Glasses",
                    modifier = Modifier
                        .size(if (compact) 32.dp else 36.dp)
                        .clip(RoundedCornerShape(10.dp)),
                )

                Column(
                    modifier = Modifier.padding(top = if (compact) 4.dp else 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                        Text(
                            "Your glasses.",
                            style = headlineStyle,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "Your AI.",
                            style = headlineStyle,
                            fontWeight = FontWeight.Bold,
                            color = ADAccent.Blue,
                        )
                        Text(
                            "Your data.",
                            style = headlineStyle,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Text(
                        "See more, remember more, and keep the moments that matter.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                ADGlassSurface(
                    modifier = Modifier.fillMaxWidth().height(heroHeight),
                    cornerRadius = if (compact) 26.dp else 30.dp,
                ) {
                    Box(Modifier.fillMaxSize()) {
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .size(if (compact) 190.dp else 230.dp)
                                .background(
                                    Brush.radialGradient(
                                        listOf(
                                            ADAccent.Indigo.copy(alpha = 0.07f),
                                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.025f),
                                            Color.Transparent,
                                        ),
                                    ),
                                    CircleShape,
                                ),
                        )
                        Image(
                            painter = painterResource(R.drawable.ad_glasses_hero),
                            contentDescription = "Smart glasses",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(
                                    horizontal = if (compact) 12.dp else 10.dp,
                                    vertical = if (compact) 12.dp else 16.dp,
                                ),
                        )
                    }
                }

                when (phase) {
                    WelcomePhase.Connecting -> Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = if (compact) 4.dp else 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.5.dp)
                        Text(
                            "Connecting",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    WelcomePhase.Choice -> Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Button(
                            onClick = connectManually,
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = RoundedCornerShape(15.dp),
                        ) { Text("Connect glasses") }
                        OutlinedButton(
                            onClick = continueWithoutGlasses,
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = RoundedCornerShape(15.dp),
                        ) { Text("Continue without glasses") }
                    }
                }
                Spacer(Modifier.height(if (compact) 6.dp else 12.dp))
            }
        }
    }
}
