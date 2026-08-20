package com.fersaiyan.cyanbridge.ui.adglasses

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.fersaiyan.cyanbridge.agent.LocalAgentPrefs
import com.fersaiyan.cyanbridge.ai.image.ExternalAssistantAutomationInspector
import com.fersaiyan.cyanbridge.ai.image.ExternalAssistantAutomationPolicy
import com.fersaiyan.cyanbridge.ai.image.ExternalImageAutomationIntents
import com.fersaiyan.cyanbridge.ai.image.ImageAutomationTarget
import com.fersaiyan.cyanbridge.ai.image.ImageQuestionBroadcast
import com.fersaiyan.cyanbridge.ai.image.TaskerImageProfileStore
import com.fersaiyan.cyanbridge.shared.glasses.GlassesAssistantMode
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Optional handoff to installed Gemini / ChatGPT apps. */
@Composable
internal fun ADAssistantAppsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var capability by remember { mutableStateOf(ExternalAssistantAutomationInspector.inspect(context)) }
    var selectedMode by remember { mutableStateOf(LocalAgentPrefs.getGlassesAssistantMode(context)) }
    var verifying by remember { mutableStateOf(false) }

    fun refresh() {
        capability = ExternalAssistantAutomationInspector.inspect(context)
        selectedMode = LocalAgentPrefs.getGlassesAssistantMode(context)
    }

    fun show(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    fun chooseDefaultAssistant() {
        runCatching { context.startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)) }
            .onFailure { context.startActivity(Intent(Settings.ACTION_SETTINGS)) }
    }

    fun importProfile() {
        val current = ExternalAssistantAutomationInspector.inspect(context)
        val assetName = when (current.target) {
            ImageAutomationTarget.GEMINI -> "tasker/CyanBridge_Gemini.xml"
            ImageAutomationTarget.CHATGPT -> "tasker/CyanBridge_ChatGPT.xml"
            ImageAutomationTarget.NONE -> {
                show("Choose Gemini or ChatGPT as Android's assistant first.")
                return
            }
        }
        if (!current.taskerInstalled) {
            show("The advanced assistant-app bridge is not installed on this phone.")
            return
        }

        val profileFile = File(context.cacheDir, assetName.substringAfterLast('/'))
        runCatching {
            context.assets.open(assetName).use { input -> profileFile.outputStream().use(input::copyTo) }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", profileFile)
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
            val intent = listOf(viewIntent, sendIntent).firstOrNull {
                it.resolveActivity(context.packageManager) != null
            } ?: error("No profile import target is available")
            context.startActivity(intent)
        }.onFailure { show("Could not open the automation profile: ${it.message}") }
    }

    fun verifyProfile() {
        val current = ExternalAssistantAutomationInspector.inspect(context)
        if (current.target == ImageAutomationTarget.NONE || !current.taskerInstalled) {
            show("Choose a supported assistant and finish the advanced bridge setup first.")
            return
        }
        verifying = true
        val token = TaskerImageProfileStore.beginVerification(context)
        context.sendBroadcast(Intent(ExternalImageAutomationIntents.assistantEventAction(context.packageName)).apply {
            setPackage(ExternalImageAutomationIntents.TASKER_PACKAGE)
            putExtra(ImageQuestionBroadcast.EXTRA_TYPE, "profile_check")
            putExtra(ImageQuestionBroadcast.EXTRA_ASSISTANT, current.target.label)
            putExtra(ExternalImageAutomationIntents.EXTRA_PROFILE_TOKEN, token)
            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        })
        scope.launch {
            delay(1_500L)
            verifying = false
            refresh()
            show(if (capability.profileCompatible) "Assistant-app bridge verified." else "No verified bridge response received.")
        }
    }

    val voiceReady = ExternalAssistantAutomationPolicy.voiceBlockingReason(capability) == null
    val imageReady = ExternalAssistantAutomationPolicy.imageBlockingReason(capability) == null
    val targetName = capability.target.takeUnless { it == ImageAutomationTarget.NONE }?.label ?: "Not selected"
    val appMode = selectedMode == GlassesAssistantMode.PHONE_ASSISTANT

    ADPageLayout("Assistant apps", onBack) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = Color.Black.copy(alpha = 0.40f),
            border = BorderStroke(1.dp, ADColors.Outline),
        ) {
            Row(Modifier.padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
                ADGlyphIcon(ADGlyph.AI, ADColors.Ink, Modifier.size(22.dp), accent = if (appMode) ADColors.Red else null)
                Column(Modifier.padding(start = 9.dp).weight(1f)) {
                    Text("ROUTE", style = MaterialTheme.typography.labelSmall, color = ADColors.Muted)
                    Text(if (appMode) targetName else "AD Glasses AI", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                }
                Box(Modifier.size(6.dp).background(if (appMode) ADColors.Red else ADColors.Ink, CircleShape))
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            ADSectionTitle("Readiness")
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                ADAssistantReadinessCard(Icons.Outlined.Android, "Assistant", targetName, capability.target != ImageAutomationTarget.NONE && capability.targetPackage != null, Modifier.weight(1f), ::chooseDefaultAssistant)
                ADAssistantReadinessCard(Icons.Outlined.PhoneAndroid, "Voice", if (voiceReady) "Ready" else "Setup", voiceReady, Modifier.weight(1f))
                ADAssistantReadinessCard(Icons.Outlined.Apps, "Images", if (imageReady) "Ready" else "Setup", imageReady, Modifier.weight(1f))
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = ADColors.Surface.copy(alpha = 0.88f),
            border = BorderStroke(1.dp, ADColors.Outline),
        ) {
            Column(Modifier.padding(10.dp)) {
                Text(
                    if (appMode) "$targetName receives supported assistant-app requests." else "Glasses questions use your configured AD AI.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.size(8.dp))
                if (appMode) {
                    OutlinedButton(
                        onClick = {
                            LocalAgentPrefs.setGlassesAssistantMode(context, GlassesAssistantMode.CUSTOM_AI_PROVIDER)
                            refresh()
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 42.dp),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, ADColors.Outline),
                    ) { Text("Use AD Glasses AI", style = MaterialTheme.typography.labelLarge, color = ADColors.Ink) }
                } else {
                    Surface(
                        onClick = {
                            if (!voiceReady) {
                                show(ExternalAssistantAutomationPolicy.voiceBlockingReason(capability) ?: "Assistant app handoff is not ready.")
                            } else {
                                LocalAgentPrefs.setGlassesAssistantMode(context, GlassesAssistantMode.PHONE_ASSISTANT)
                                refresh()
                            }
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                        shape = RoundedCornerShape(11.dp),
                        color = ADColors.Red,
                        contentColor = Color.White,
                    ) {
                        Box(contentAlignment = Alignment.Center) { Text("Use assistant app", style = MaterialTheme.typography.labelLarge) }
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            ADSectionTitle("Advanced setup")
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(13.dp),
                color = ADColors.Surface.copy(alpha = 0.88f),
                border = BorderStroke(1.dp, ADColors.Outline),
            ) {
                Column(Modifier.padding(horizontal = 10.dp)) {
                    ADAssistantSetupAction("Choose Android assistant", "Select Gemini or ChatGPT", ::chooseDefaultAssistant)
                    HorizontalDivider(color = ADColors.Separator)
                    ADAssistantSetupAction("Import automation bridge", if (capability.profileCompatible) "Profile verified" else "Import matching profile", ::importProfile)
                    HorizontalDivider(color = ADColors.Separator)
                    ADAssistantSetupAction(if (verifying) "Verifying…" else "Verify automation bridge", if (capability.profileCompatible) "Verified" else "Check bridge response") { if (!verifying) verifyProfile() }
                    HorizontalDivider(color = ADColors.Separator)
                    ADAssistantSetupAction("Accessibility", if (capability.autoInputAccessibilityEnabled) "Image handoff enabled" else "Only needed for external image automation") {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }
                }
            }
        }
    }
}

@Composable
private fun ADAssistantReadinessCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    detail: String,
    ready: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val clickModifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Surface(
        modifier = modifier.heightIn(min = 80.dp).then(clickModifier),
        shape = RoundedCornerShape(12.dp),
        color = ADColors.Surface.copy(alpha = 0.88f),
        border = BorderStroke(1.dp, ADColors.Outline),
    ) {
        Column(modifier = Modifier.padding(9.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = ADColors.Ink, modifier = Modifier.size(16.dp))
                Spacer(Modifier.weight(1f))
                if (ready) Icon(Icons.Outlined.CheckCircle, "Ready", tint = ADColors.Ink, modifier = Modifier.size(14.dp))
                else Box(Modifier.size(4.dp).background(ADColors.Red, CircleShape))
            }
            Column {
                Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Text(detail, style = MaterialTheme.typography.labelSmall, color = ADColors.Muted, maxLines = 1)
            }
        }
    }
}

@Composable
private fun ADAssistantSetupAction(title: String, detail: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Settings, null, tint = ADColors.Ink, modifier = Modifier.size(16.dp))
        Column(Modifier.padding(start = 9.dp).weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = ADColors.Muted, maxLines = 1)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = ADColors.Muted, modifier = Modifier.size(15.dp))
    }
}
