package com.ad_glasses.agent

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.ad_glasses.ai.grounding.GroundingPrefs
import com.ad_glasses.ai.grounding.TavilySearchClient
import com.ad_glasses.ai.grounding.TavilySearchDepth
import com.ad_glasses.ai.router.AiProviderPrefs
import com.ad_glasses.ui.setThemedComposeContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Configuration for AD-owned relay plus external Search & Maps grounding services. */
class CloudSettingsActivity : AppCompatActivity() {
    private var relayUrl by mutableStateOf("")
    private var relayToken by mutableStateOf("")
    private var status by mutableStateOf("")
    private var relayUrlError by mutableStateOf<String?>(null)
    private var testing by mutableStateOf(false)

    private var tavilyReplacement by mutableStateOf("")
    private var tavilyConfigured by mutableStateOf(false)
    private var tavilyEnabled by mutableStateOf(true)
    private var nominatimBaseUrl by mutableStateOf("")
    private var overpassEndpoint by mutableStateOf("")
    private var osrmBaseUrl by mutableStateOf("")
    private var groundingStatus by mutableStateOf("")
    private var groundingError by mutableStateOf<String?>(null)
    private var testingTavily by mutableStateOf(false)
    private var locationGranted by mutableStateOf(false)

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        locationGranted = hasLocationPermission()
        groundingStatus = if (locationGranted) {
            "Location access granted. AD can use device location for explicit nearby, self-location, and current-location routing requests."
        } else {
            "Location access was not granted. Named-place searches and place-to-place routing can still work without device GPS."
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        relayUrl = AiProviderPrefs.getRelayBaseUrl(this)
        relayToken = CloudServerPrefs.getApiToken(this)
        status = if (AiProviderPrefs.isRelayConfigured(this)) {
            "Realtime relay configured"
        } else {
            "Realtime relay is not configured"
        }

        val grounding = GroundingPrefs.getConfig(this)
        tavilyConfigured = GroundingPrefs.hasTavilyApiKey(this)
        tavilyEnabled = grounding.tavilyEnabled
        nominatimBaseUrl = grounding.nominatimBaseUrl
        overpassEndpoint = grounding.overpassEndpoint
        osrmBaseUrl = grounding.osrmBaseUrl
        locationGranted = hasLocationPermission()
        groundingStatus = if (tavilyConfigured) "Tavily key saved securely" else "Add a Tavily API key to enable Tavily retrieval"

        setThemedComposeContent {
            CloudServiceSettingsScreen(
                relayUrl = relayUrl,
                relayToken = relayToken,
                status = status,
                relayUrlError = relayUrlError,
                testing = testing,
                tavilyReplacement = tavilyReplacement,
                tavilyConfigured = tavilyConfigured,
                tavilyEnabled = tavilyEnabled,
                locationGranted = locationGranted,
                nominatimBaseUrl = nominatimBaseUrl,
                overpassEndpoint = overpassEndpoint,
                osrmBaseUrl = osrmBaseUrl,
                groundingStatus = groundingStatus,
                groundingError = groundingError,
                testingTavily = testingTavily,
                onBack = ::finish,
                onRelayUrlChange = {
                    relayUrl = it
                    relayUrlError = null
                },
                onRelayTokenChange = { relayToken = it },
                onSaveRelay = { saveRelay(showConfirmation = true) },
                onTestRelay = ::testConnection,
                onTavilyReplacementChange = {
                    tavilyReplacement = it
                    groundingError = null
                },
                onTavilyEnabledChange = {
                    tavilyEnabled = it
                    GroundingPrefs.setTavilyEnabled(this, it)
                },
                onRequestLocation = ::requestLocationPermission,
                onNominatimChange = { nominatimBaseUrl = it; groundingError = null },
                onOverpassChange = { overpassEndpoint = it; groundingError = null },
                onOsrmChange = { osrmBaseUrl = it; groundingError = null },
                onSaveGrounding = { saveGrounding(showConfirmation = true) },
                onTestTavily = ::testTavily,
                onClearTavily = {
                    GroundingPrefs.clearTavilyApiKey(this)
                    tavilyReplacement = ""
                    tavilyConfigured = false
                    groundingStatus = "Tavily key removed"
                },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        locationGranted = hasLocationPermission()
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun requestLocationPermission() {
        if (hasLocationPermission()) {
            locationGranted = true
            groundingStatus = "Location access is already granted."
            return
        }
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ),
        )
    }

    private fun saveRelay(showConfirmation: Boolean): Boolean {
        val url = relayUrl.trim().trimEnd('/')
        if (url.isNotBlank() && !url.startsWith("https://") && !url.startsWith("http://")) {
            relayUrlError = "Use a full http:// or https:// URL"
            return false
        }
        relayUrl = url
        relayUrlError = null
        AiProviderPrefs.setRelayBaseUrl(this, url)
        CloudServerPrefs.setApiToken(this, relayToken)
        status = if (url.isBlank()) "Realtime relay is not configured" else "Realtime relay configured"
        if (showConfirmation) Toast.makeText(this, "Realtime relay saved", Toast.LENGTH_SHORT).show()
        return true
    }

    private fun saveGrounding(showConfirmation: Boolean): Boolean {
        return runCatching {
            GroundingPrefs.saveEndpoints(
                context = this,
                nominatimBaseUrl = nominatimBaseUrl,
                overpassEndpoint = overpassEndpoint,
                osrmBaseUrl = osrmBaseUrl,
            )
            GroundingPrefs.setTavilyEnabled(this, tavilyEnabled)
            tavilyReplacement.trim().takeIf { it.isNotBlank() }?.let {
                GroundingPrefs.replaceTavilyApiKey(this, it)
                tavilyReplacement = ""
            }
            tavilyConfigured = GroundingPrefs.hasTavilyApiKey(this)
            val saved = GroundingPrefs.getConfig(this)
            nominatimBaseUrl = saved.nominatimBaseUrl
            overpassEndpoint = saved.overpassEndpoint
            osrmBaseUrl = saved.osrmBaseUrl
            groundingError = null
            groundingStatus = if (tavilyConfigured) "Search & Maps settings saved; Tavily is configured" else "Map endpoints saved; Tavily key is not configured"
            if (showConfirmation) Toast.makeText(this, "Search & Maps saved", Toast.LENGTH_SHORT).show()
            true
        }.getOrElse { error ->
            groundingError = error.message ?: "Could not save Search & Maps settings"
            false
        }
    }

    private fun testConnection() {
        if (!saveRelay(showConfirmation = false)) return
        testing = true
        status = "Testing relay…"
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                CloudRelayClient.fetchAvailableModels(this@CloudSettingsActivity)
            }
            testing = false
            status = result.fold(
                onSuccess = { models -> "Connected. ${models.size} model(s) reported by relay." },
                onFailure = { error -> CloudRelayClient.relayUnavailableHint(error) ?: error.message ?: "Connection failed" },
            )
        }
    }

    private fun testTavily() {
        if (!saveGrounding(showConfirmation = false)) return
        if (!GroundingPrefs.hasTavilyApiKey(this)) {
            groundingStatus = "Enter and save a Tavily API key first"
            return
        }
        testingTavily = true
        groundingStatus = "Testing Tavily…"
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                TavilySearchClient(this@CloudSettingsActivity).search(
                    query = "OpenStreetMap project",
                    depth = TavilySearchDepth.FAST,
                    maxResults = 1,
                )
            }
            testingTavily = false
            groundingStatus = result.fold(
                onSuccess = { response -> "Tavily connected. ${response.results.size} result(s) returned." },
                onFailure = { error -> error.message ?: "Tavily connection failed" },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@androidx.compose.runtime.Composable
private fun CloudServiceSettingsScreen(
    relayUrl: String,
    relayToken: String,
    status: String,
    relayUrlError: String?,
    testing: Boolean,
    tavilyReplacement: String,
    tavilyConfigured: Boolean,
    tavilyEnabled: Boolean,
    locationGranted: Boolean,
    nominatimBaseUrl: String,
    overpassEndpoint: String,
    osrmBaseUrl: String,
    groundingStatus: String,
    groundingError: String?,
    testingTavily: Boolean,
    onBack: () -> Unit,
    onRelayUrlChange: (String) -> Unit,
    onRelayTokenChange: (String) -> Unit,
    onSaveRelay: () -> Unit,
    onTestRelay: () -> Unit,
    onTavilyReplacementChange: (String) -> Unit,
    onTavilyEnabledChange: (Boolean) -> Unit,
    onRequestLocation: () -> Unit,
    onNominatimChange: (String) -> Unit,
    onOverpassChange: (String) -> Unit,
    onOsrmChange: (String) -> Unit,
    onSaveGrounding: () -> Unit,
    onTestTavily: () -> Unit,
    onClearTavily: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cloud services") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Realtime relay", style = MaterialTheme.typography.titleMedium)
            Text(
                "This relay is optional. AD uses it for short-lived Realtime session credentials such as Gemini Live. Standard REST provider API keys are stored separately.",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(
                value = relayUrl,
                onValueChange = onRelayUrlChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Relay base URL") },
                placeholder = { Text("https://…") },
                singleLine = true,
                isError = relayUrlError != null,
                supportingText = relayUrlError?.let { error -> { Text(error) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )
            OutlinedTextField(
                value = relayToken,
                onValueChange = onRelayTokenChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Relay access token") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
            )
            Button(onClick = onSaveRelay, modifier = Modifier.fillMaxWidth()) { Text("Save relay") }
            OutlinedButton(onClick = onTestRelay, enabled = !testing, modifier = Modifier.fillMaxWidth()) {
                Text(if (testing) "Testing…" else "Save and test relay")
            }
            Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            HorizontalDivider()
            Text("Search & Maps", style = MaterialTheme.typography.titleMedium)
            Text(
                "Tavily grounds live web answers. OpenStreetMap services provide reverse geocoding, nearby POIs, and routing only when the current turn needs location context.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Tavily web grounding", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (tavilyConfigured) "API key saved securely" else "API key required",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = tavilyEnabled, onCheckedChange = onTavilyEnabledChange)
            }
            OutlinedTextField(
                value = tavilyReplacement,
                onValueChange = onTavilyReplacementChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(if (tavilyConfigured) "Replace Tavily API key" else "Tavily API key") },
                placeholder = { Text(if (tavilyConfigured) "Saved · enter only to replace" else "tvly-…") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
            )
            Text(
                "Saved Tavily keys are kept in Android Keystore-backed encrypted preferences and are never displayed again.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (tavilyConfigured) {
                OutlinedButton(onClick = onClearTavily, modifier = Modifier.fillMaxWidth()) { Text("Remove Tavily key") }
            }

            Text("Device location", style = MaterialTheme.typography.bodyLarge)
            Text(
                if (locationGranted) {
                    "Location access is granted. AD reads a location fix only for a high-confidence spatial turn such as ‘near me’, ‘where am I?’, or routing from the current position."
                } else {
                    "Location access is optional. Grant it for ‘near me’, self-location, and current-position routing. Named-place searches and place-to-place routes do not require device GPS."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = onRequestLocation,
                enabled = !locationGranted,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (locationGranted) "Location access granted" else "Grant location access")
            }

            OutlinedTextField(
                value = nominatimBaseUrl,
                onValueChange = onNominatimChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Nominatim base URL") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )
            OutlinedTextField(
                value = overpassEndpoint,
                onValueChange = onOverpassChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Overpass endpoint") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )
            OutlinedTextField(
                value = osrmBaseUrl,
                onValueChange = onOsrmChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("OSRM base URL") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )
            Text(
                "The bundled public OSM endpoints are intended for moderate, user-triggered usage. For a scaled production rollout, point these fields at your proxy/self-hosted services without rebuilding the app.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onSaveGrounding, modifier = Modifier.fillMaxWidth()) { Text("Save Search & Maps") }
            OutlinedButton(onClick = onTestTavily, enabled = !testingTavily, modifier = Modifier.fillMaxWidth()) {
                Text(if (testingTavily) "Testing…" else "Save and test Tavily")
            }
            groundingError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Text(groundingStatus, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
