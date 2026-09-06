package com.adglasses.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CenterFocusWeak
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProductPreviewDialog(title: String, message: String, dismiss: () -> Unit) {
    val isLens = title.equals("Lens", ignoreCase = true)
    val accent = if (isLens) ADAccent.Indigo else ADAccent.Orange
    val secondary = if (isLens) ADAccent.Cyan else ADAccent.Pink
    val headline = if (isLens) "Look. Ask. Understand." else "Capture the thought."
    val detailTitle = if (isLens) "From your glasses" else "A thought worth keeping"
    val detailText = if (isLens) {
        "A captured still becomes visual context for AD, without turning this into a permanent live camera feed."
    } else {
        "A short spoken capture becomes a note you can revisit without turning the Home screen into a recorder dashboard."
    }
    val icon = if (isLens) Icons.Filled.CenterFocusWeak else Icons.Filled.FormatQuote

    Dialog(
        onDismissRequest = dismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Scaffold(
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                topBar = {
                    CenterAlignedTopAppBar(
                        title = {
                            Text(
                                title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = dismiss) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                            containerColor = Color.Transparent,
                            scrolledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                        ),
                    )
                },
            ) { inner ->
                ADAmbientBackground(Modifier.padding(inner), strong = true) {
                    BoxWithConstraints(Modifier.fillMaxSize()) {
                        val compact = maxHeight < 720.dp || maxWidth < 390.dp
                        val heroHeight = if (compact) 152.dp else 176.dp
                        val outerVisual = if (compact) 92.dp else 106.dp
                        val innerVisual = if (compact) 64.dp else 72.dp
                        val iconSize = if (compact) 28.dp else 32.dp

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                horizontal = 16.dp,
                                vertical = if (compact) 10.dp else 14.dp,
                            ),
                            verticalArrangement = Arrangement.spacedBy(if (compact) 14.dp else 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            item {
                                ADGlassSurface(
                                    modifier = Modifier
                                        .widthIn(max = 640.dp)
                                        .fillMaxWidth()
                                        .height(heroHeight),
                                    cornerRadius = if (compact) 22.dp else 24.dp,
                                ) {
                                    Box(Modifier.fillMaxSize()) {
                                        Box(
                                            Modifier
                                                .align(Alignment.TopEnd)
                                                .size(if (compact) 150.dp else 180.dp)
                                                .background(
                                                    Brush.radialGradient(
                                                        listOf(
                                                            accent.copy(alpha = 0.18f),
                                                            secondary.copy(alpha = 0.08f),
                                                            Color.Transparent,
                                                        ),
                                                    ),
                                                    CircleShape,
                                                ),
                                        )
                                        Box(
                                            Modifier
                                                .align(Alignment.Center)
                                                .size(outerVisual)
                                                .background(accent.copy(alpha = 0.08f), CircleShape),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Surface(
                                                modifier = Modifier.size(innerVisual),
                                                shape = CircleShape,
                                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.66f),
                                                border = androidx.compose.foundation.BorderStroke(
                                                    1.dp,
                                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f),
                                                ),
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        icon,
                                                        contentDescription = null,
                                                        tint = accent,
                                                        modifier = Modifier.size(iconSize),
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            item {
                                Column(
                                    modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(7.dp),
                                ) {
                                    Text(
                                        headline,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        message,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }

                            item {
                                ADGroupedCard(
                                    modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
                                    cornerRadius = 16.dp,
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(11.dp),
                                    ) {
                                        Surface(
                                            modifier = Modifier.size(34.dp),
                                            shape = RoundedCornerShape(10.dp),
                                            color = accent.copy(alpha = 0.10f),
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(icon, null, tint = accent, modifier = Modifier.size(19.dp))
                                            }
                                        }
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(2.dp),
                                        ) {
                                            Text(
                                                detailTitle,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                            Text(
                                                detailText,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }

                            item { Spacer(Modifier.height(12.dp)) }
                        }
                    }
                }
            }
        }
    }
}
