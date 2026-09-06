package com.adglasses.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
                        title = { Text(title, fontWeight = FontWeight.SemiBold) },
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
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        item {
                            ADGlassSurface(
                                modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth().height(230.dp),
                                cornerRadius = 26.dp,
                            ) {
                                Box(Modifier.fillMaxSize()) {
                                    Box(
                                        Modifier
                                            .align(Alignment.TopEnd)
                                            .size(210.dp)
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
                                            .size(124.dp)
                                            .background(accent.copy(alpha = 0.08f), CircleShape),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Surface(
                                            modifier = Modifier.size(86.dp),
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.58f),
                                            border = androidx.compose.foundation.BorderStroke(
                                                1.dp,
                                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
                                            ),
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    icon,
                                                    contentDescription = null,
                                                    tint = accent,
                                                    modifier = Modifier.size(38.dp),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Column(
                                modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    headline,
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    message,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        item {
                            ADGroupedCard(
                                modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(),
                                cornerRadius = 18.dp,
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Surface(
                                        modifier = Modifier.size(36.dp),
                                        shape = RoundedCornerShape(11.dp),
                                        color = accent.copy(alpha = 0.10f),
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(icon, null, tint = accent, modifier = Modifier.size(20.dp))
                                        }
                                    }
                                    Column(Modifier.weight(1f)) {
                                        Text("Android preview", fontWeight = FontWeight.SemiBold)
                                        Text(
                                            "The product surface is in place; the remaining hardware workflow is still being validated.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }

                        item { Spacer(Modifier.height(20.dp)) }
                    }
                }
            }
        }
    }
}
