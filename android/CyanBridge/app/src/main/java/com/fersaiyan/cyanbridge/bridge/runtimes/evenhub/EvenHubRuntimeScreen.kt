package com.fersaiyan.cyanbridge.bridge.runtimes.evenhub

import android.webkit.WebView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EvenHubRuntimeScreen(
    url: String,
    logs: String,
    onUrlChange: (String) -> Unit,
    onLoad: () -> Unit,
    onStop: () -> Unit,
    onWebViewCreated: (WebView) -> Unit,
) {
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = { TopAppBar(title = { Text("EvenHub runtime") }) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = url,
                onValueChange = onUrlChange,
                label = { Text("EvenHub app URL") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Row {
                FilledTonalButton(onClick = onLoad, modifier = Modifier.weight(1f)) { Text("Load") }
                Spacer(Modifier.padding(horizontal = 4.dp))
                OutlinedButton(onClick = onStop, modifier = Modifier.weight(1f)) { Text("Stop") }
            }
            AndroidView(
                factory = { context -> WebView(context).also(onWebViewCreated) },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = logs.ifBlank { "Runtime log will appear here." },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp),
                )
            }
        }
    }
}
