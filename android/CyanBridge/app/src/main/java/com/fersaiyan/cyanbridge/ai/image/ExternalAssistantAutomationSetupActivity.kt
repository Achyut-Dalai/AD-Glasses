package com.fersaiyan.cyanbridge.ai.image

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Assistant
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.fersaiyan.cyanbridge.R
import com.fersaiyan.cyanbridge.MainActivity
import com.fersaiyan.cyanbridge.ui.appearance.AppearancePreferences
import com.fersaiyan.cyanbridge.ui.appearance.rememberAppearanceSettings
import com.fersaiyan.cyanbridge.ui.theme.CyanBridgeTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

class ExternalAssistantAutomationSetupActivity : AppCompatActivity() {
    private var uiState by mutableStateOf(ExternalAssistantSetupUiState())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        refreshSetupState()
        val appearancePreferences = AppearancePreferences(this)
        setContent {
            val appearance by rememberAppearanceSettings(appearancePreferences)
            CyanBridgeTheme(appearance) {
                ExternalAssistantAutomationSetupScreen(
                    state = uiState,
                    onBack = ::finish,
                    onChooseDefaultAssistant = ::openDefaultAssistantSettings,
                    onImportProfile = ::importMatchingProfile,
                    onVerifyProfile = ::verifyImportedProfile,
                    onOpenAccessibility = ::openAccessibilitySettings,
                    onTestVoice = ::testTaskerVoiceLaunch,
                    onRefresh = ::refreshSetupState,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshSetupState()
    }

    private fun openDefaultAssistantSettings() {
        runCatching { startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)) }
            .onFailure { startActivity(Intent(Settings.ACTION_SETTINGS)) }
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun importMatchingProfile() {
        val capability = ExternalAssistantAutomationInspector.inspect(this)
        val assetName = when (capability.target) {
            ImageAutomationTarget.GEMINI -> "tasker/CyanBridge_Gemini.xml"
            ImageAutomationTarget.CHATGPT -> "tasker/CyanBridge_ChatGPT.xml"
            ImageAutomationTarget.NONE -> {
                showLongToast("Set Gemini or ChatGPT as the default assistant first.")
                return
            }
        }
        if (!capability.taskerInstalled) {
            showLongToast("Install Tasker before importing the profile.")
            return
        }

        val profileFile = File(cacheDir, assetName.substringAfterLast('/'))
        runCatching {
            assets.open(assetName).use { input ->
                profileFile.outputStream().use(input::copyTo)
            }
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", profileFile)
            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/xml")
                setPackage(ExternalImageAutomationIntents.TASKER_PACKAGE)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/xml"
                setPackage(ExternalImageAutomationIntents.TASKER_PACKAGE)
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val importIntent = listOf(viewIntent, sendIntent).firstOrNull {
                it.resolveActivity(packageManager) != null
            } ?: throw IllegalStateException("Tasker did not expose a profile import activity")
            startActivity(importIntent)
        }.onFailure { error ->
            showLongToast("Could not open the Tasker profile: ${error.message}")
        }
    }

    private fun verifyImportedProfile() {
        val capability = ExternalAssistantAutomationInspector.inspect(this)
        if (capability.target == ImageAutomationTarget.NONE) {
            showLongToast("Set Gemini or ChatGPT as the default assistant first.")
            return
        }
        if (!capability.taskerInstalled) {
            showLongToast("Tasker is not installed.")
            return
        }

        val token = TaskerImageProfileStore.beginVerification(this)
        sendBroadcast(Intent(MainActivity.aiEventAction(packageName)).apply {
            setPackage(ExternalImageAutomationIntents.TASKER_PACKAGE)
            putExtra(ImageQuestionBroadcast.EXTRA_TYPE, "profile_check")
            putExtra(ImageQuestionBroadcast.EXTRA_ASSISTANT, capability.target.label)
            putExtra(ExternalImageAutomationIntents.EXTRA_PROFILE_TOKEN, token)
            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        })
        Toast.makeText(this, "Waiting for the Tasker profile handshake...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            delay(1_500L)
            refreshSetupState()
            val verified = ExternalAssistantAutomationInspector.inspect(this@ExternalAssistantAutomationSetupActivity)
                .profileCompatible
            showLongToast(if (verified) "Tasker profile verified." else "No valid profile response received.")
        }
    }

    private fun testTaskerVoiceLaunch() {
        val capability = ExternalAssistantAutomationInspector.inspect(this)
        val reason = ExternalAssistantAutomationPolicy.voiceBlockingReason(capability)
        if (reason != null) {
            showLongToast(reason)
            return
        }
        sendBroadcast(Intent(MainActivity.aiEventAction(packageName)).apply {
            setPackage(ExternalImageAutomationIntents.TASKER_PACKAGE)
            putExtra(ImageQuestionBroadcast.EXTRA_TYPE, "voice")
            putExtra(ImageQuestionBroadcast.EXTRA_ASSISTANT, capability.target.label)
            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        })
    }

    private fun refreshSetupState() {
        val capability = ExternalAssistantAutomationInspector.inspect(this)
        val importedTarget = TaskerImageProfileStore.target(this)
        val importedVersion = TaskerImageProfileStore.version(this)
        val defaultPackage = DefaultAssistantResolver.packageName(this)
        val checks = listOf(
            AssistantSetupCheck(
                title = "Android default assistant",
                passed = capability.target != ImageAutomationTarget.NONE,
                detail = when (capability.target) {
                    ImageAutomationTarget.NONE -> "${defaultPackage ?: "Not detected"}; choose Gemini or ChatGPT"
                    else -> "${capability.target.label} ($defaultPackage)"
                },
            ),
            AssistantSetupCheck(
                title = "Default assistant app installed",
                passed = capability.targetPackage != null,
                detail = capability.targetPackage ?: "No supported assistant package",
            ),
            AssistantSetupCheck(
                title = "Tasker installed",
                passed = capability.taskerInstalled,
                detail = ExternalImageAutomationIntents.TASKER_PACKAGE,
            ),
            AssistantSetupCheck(
                title = "Matching Tasker profile verified",
                passed = capability.profileCompatible,
                detail = profileDetail(capability.target, importedTarget, importedVersion),
            ),
            AssistantSetupCheck(
                title = "AutoInput installed",
                passed = capability.autoInputInstalled,
                detail = ExternalImageAutomationIntents.AUTO_INPUT_PACKAGE,
            ),
            AssistantSetupCheck(
                title = "AutoInput accessibility enabled",
                passed = capability.autoInputAccessibilityEnabled,
                detail = "Required for filling and sending external image prompts",
            ),
            AssistantSetupCheck(
                title = "Phone unlocked",
                passed = !capability.phoneLocked,
                detail = "External UI automation fails immediately while locked",
            ),
            AssistantSetupCheck(
                title = "Assistant accepts image shares",
                passed = capability.imageShareAvailable,
                detail = capability.targetPackage ?: "No supported assistant selected",
            ),
        )
        val firstFailure = checks.firstOrNull { !it.passed }
        val session = ExternalImageAutomationStore.current(this)
        uiState = ExternalAssistantSetupUiState(
            targetLabel = capability.target.takeUnless { it == ImageAutomationTarget.NONE }?.label
                ?: "Unsupported default",
            ready = firstFailure == null,
            nextStep = firstFailure?.let { "${it.title}: ${it.detail}" }
                ?: "Voice and image automation are ready for ${capability.target.label}.",
            checks = checks,
            canImportProfile = capability.target != ImageAutomationTarget.NONE && capability.taskerInstalled,
            canVerifyProfile = capability.target != ImageAutomationTarget.NONE && capability.taskerInstalled,
            canTestVoice = ExternalAssistantAutomationPolicy.voiceBlockingReason(capability) == null,
            lastImageState = session?.let {
                buildString {
                    append(it.state.stage.wireName)
                    it.state.error?.takeIf(String::isNotBlank)?.let { error -> append(" ($error)") }
                }
            },
        )
    }

    private fun profileDetail(
        target: ImageAutomationTarget,
        importedTarget: String?,
        importedVersion: String?,
    ): String {
        if (target == ImageAutomationTarget.NONE) return "No supported default assistant"
        val reported = if (importedTarget == null || importedVersion == null) {
            "no verified profile"
        } else {
            "$importedTarget $importedVersion"
        }
        return "reported=$reported; required=${target.wireName} ${target.requiredProfileVersion}"
    }

    private fun showLongToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}

data class ExternalAssistantSetupUiState(
    val targetLabel: String = "Checking...",
    val ready: Boolean = false,
    val nextStep: String = "Checking assistant setup",
    val checks: List<AssistantSetupCheck> = emptyList(),
    val canImportProfile: Boolean = false,
    val canVerifyProfile: Boolean = false,
    val canTestVoice: Boolean = false,
    val lastImageState: String? = null,
)

data class AssistantSetupCheck(
    val title: String,
    val passed: Boolean,
    val detail: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExternalAssistantAutomationSetupScreen(
    state: ExternalAssistantSetupUiState,
    onBack: () -> Unit,
    onChooseDefaultAssistant: () -> Unit,
    onImportProfile: () -> Unit,
    onVerifyProfile: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onTestVoice: () -> Unit,
    onRefresh: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.compose_external_setup_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.compose_external_back))
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.compose_external_refresh_status))
                    }
                },
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SetupSummaryCard(state)
            }
            item {
                Text(
                    text = stringResource(R.string.compose_external_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = onChooseDefaultAssistant,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.compose_external_choose_assistant))
                    }
                    OutlinedButton(
                        onClick = onImportProfile,
                        enabled = state.canImportProfile,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.compose_external_import_profile))
                    }
                    OutlinedButton(
                        onClick = onVerifyProfile,
                        enabled = state.canVerifyProfile,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.compose_external_verify_profile))
                    }
                    OutlinedButton(
                        onClick = onOpenAccessibility,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.compose_external_open_accessibility))
                    }
                    Button(
                        onClick = onTestVoice,
                        enabled = state.canTestVoice,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.compose_external_test_voice))
                    }
                }
            }
            item {
                Text(
                    text = stringResource(R.string.compose_external_setup_checks),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            items(state.checks) { check ->
                SetupCheckCard(check)
            }
            state.lastImageState?.let { lastState ->
                item {
                    HorizontalDivider()
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.compose_external_last_state, lastState),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                TextButton(
                    onClick = onRefresh,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.compose_external_refresh_status))
                }
            }
        }
    }
}

@Composable
private fun SetupSummaryCard(state: ExternalAssistantSetupUiState) {
    val containerColor = if (state.ready) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (state.ready) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor, contentColor = contentColor),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (state.ready) Icons.Default.CheckCircle else Icons.Default.Assistant,
                contentDescription = null,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.targetLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = state.nextStep,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun SetupCheckCard(check: AssistantSetupCheck) {
    val iconColor = if (check.passed) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.error
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = if (check.passed) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                contentDescription = if (check.passed) "Passed" else "Needs attention",
                tint = iconColor,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = check.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = check.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
