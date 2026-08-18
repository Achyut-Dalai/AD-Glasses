package com.fersaiyan.cyanbridge.ui.adglasses

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

/** Optional handoff to installed Gemini / ChatGPT apps. Configured AI remains the default path. */
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
            context.assets.open(assetName).use { input ->
                profileFile.outputStream().use(input::copyTo)
            }
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
    val assistantRouteActive = selectedMode == GlassesAssistantMode.PHONE_ASSISTANT

    ADPageLayout("Assistant apps", onBack) {
        ADPageHero(
            icon = Icons.Outlined.Apps,
            title = if (assistantRouteActive) targetName else "Optional app handoff",
            detail = if (assistantRouteActive) {
                "Selected glasses requests can hand off to the Android assistant app instead of your configured AD AI route."
            } else {
                "Keep AD as the normal route, or optionally hand selected requests to an installed Gemini or ChatGPT assistant app."
            },
            status = if (assistantRouteActive) "ACTIVE" else "OPTIONAL",
            statusTone = if (assistantRouteActive) ADStatusTone.INFO else ADStatusTone.NEUTRAL,
        )

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ADSectionEyebrow("Readiness")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ADAssistantReadinessCard(
                    icon = Icons.Outlined.Android,
                    title = "Assistant",
                    detail = targetName,
                    ready = capability.target != ImageAutomationTarget.NONE && capability.targetPackage != null,
                    modifier = Modifier.weight(1f),
                    onClick = ::chooseDefaultAssistant,
                )
                ADAssistantReadinessCard(
                    icon = Icons.Outlined.PhoneAndroid,
                    title = "Voice",
                    detail = if (voiceReady) "Ready" else "Setup needed",
                    ready = voiceReady,
                    modifier = Modifier.weight(1f),
                )
            }
            ADAssistantReadinessCard(
                icon = Icons.Outlined.Apps,
                title = "Image handoff",
                detail = if (imageReady) "Advanced bridge is ready" else "Bridge and accessibility setup may be required",
                ready = imageReady,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            ADSectionEyebrow("Glasses route")
            ADCard {
                Text(
                    if (assistantRouteActive) "$targetName is the current assistant-app route." else "Glasses questions use your configured AD AI.",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    if (assistantRouteActive) {
                        "Switch back any time to keep all supported questions inside AD's configured AI path."
                    } else {
                        "Assistant-app handoff stays off until you explicitly enable it here."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = ADColors.Muted,
                )
                Spacer(Modifier.height(14.dp))
                if (assistantRouteActive) {
                    OutlinedButton(
                        onClick = {
                            LocalAgentPrefs.setGlassesAssistantMode(context, GlassesAssistantMode.CUSTOM_AI_PROVIDER)
                            refresh()
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) { Text("Use AD AI") }
                } else {
                    Button(
                        onClick = {
                            if (!voiceReady) {
                                show(ExternalAssistantAutomationPolicy.voiceBlockingReason(capability) ?: "Assistant app handoff is not ready.")
                            } else {
                                LocalAgentPrefs.setGlassesAssistantMode(context, GlassesAssistantMode.PHONE_ASSISTANT)
                                refresh()
                            }
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ADColors.Ink),
                    ) { Text("Use assistant app") }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            ADSectionEyebrow("Advanced bridge")
            ADCard {
                ADAssistantSetupAction(
                    title = "Choose Android assistant",
                    detail = "Select Gemini or ChatGPT as the phone assistant",
                    onClick = ::chooseDefaultAssistant,
                )
                HorizontalDivider(color = ADColors.Separator)
                ADAssistantSetupAction(
                    title = "Import automation bridge",
                    detail = if (capability.profileCompatible) "Profile already verified" else "Import the matching bridge profile",
                    onClick = ::importProfile,
                )
                HorizontalDivider(color = ADColors.Separator)
                ADAssistantSetupAction(
                    title = if (verifying) "Verifying…" else "Verify automation bridge",
                    detail = if (capability.profileCompatible) "Verified" else "Confirm that the imported profile responds",
                    onClick = { if (!verifying) verifyProfile() },
                )
                HorizontalDivider(color = ADColors.Separator)
                ADAssistantSetupAction(
                    title = "Accessibility",
                    detail = if (capability.autoInputAccessibilityEnabled) "Image handoff access is enabled" else "Needed only for external image-prompt automation",
                    onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                )
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
    val content: @Composable () -> Unit = {
        Column(Modifier.padding(15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(42.dp).background(ADColors.SurfaceSubtle, RoundedCornerShape(13.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = ADColors.Ink, modifier = Modifier.size(21.dp))
                }
                Spacer(Modifier.weight(1f))
                if (ready) Icon(Icons.Outlined.CheckCircle, contentDescription = "Ready", tint = ADColors.Success, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(detail, style = MaterialTheme.typography.bodySmall, color = ADColors.Muted)
        }
    }

    if (onClick != null) {
        androidx.compose.material3.Surface(
            onClick = onClick,
            modifier = modifier.heightIn(min = 126.dp),
            shape = RoundedCornerShape(20.dp),
            color = ADColors.Surface,
            content = content,
        )
    } else {
        androidx.compose.material3.Surface(
            modifier = modifier.heightIn(min = 126.dp),
            shape = RoundedCornerShape(20.dp),
            color = ADColors.Surface,
            content = content,
        )
    }
}

@Composable
private fun ADAssistantSetupAction(
    title: String,
    detail: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(40.dp).background(ADColors.SurfaceSubtle, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Settings, contentDescription = null, tint = ADColors.Ink, modifier = Modifier.size(19.dp))
        }
        Column(Modifier.padding(start = 11.dp).weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = ADColors.Muted)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = ADColors.Muted, modifier = Modifier.size(22.dp))
    }
}
