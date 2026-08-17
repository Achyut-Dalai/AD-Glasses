package com.fersaiyan.cyanbridge.ui.adglasses

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.KeyboardArrowRight
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
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.fersaiyan.cyanbridge.MainActivity
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
        context.sendBroadcast(Intent(MainActivity.aiEventAction(context.packageName)).apply {
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

    ADPageLayout("Assistant apps", onBack) {
        ADCard {
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier.size(46.dp).background(ADColors.SurfaceSubtle, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.Apps, contentDescription = null, tint = ADColors.Ink)
                }
                Column(Modifier.padding(start = 13.dp).weight(1f)) {
                    Text("Optional app handoff", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.size(4.dp))
                    Text(
                        "Use an installed Gemini or ChatGPT app for selected glasses requests. Your configured AI stays the normal route unless you turn this on.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ADColors.Muted,
                    )
                }
            }
        }

        ADSectionTitle("Current assistant")
        ADCard {
            ADAssistantAppStatusRow(
                icon = Icons.Outlined.Android,
                title = "Android assistant",
                detail = targetName,
                ready = capability.target != ImageAutomationTarget.NONE && capability.targetPackage != null,
                onClick = ::chooseDefaultAssistant,
            )
            HorizontalDivider(Modifier.padding(start = 49.dp), color = ADColors.Separator)
            ADAssistantAppStatusRow(
                icon = Icons.Outlined.PhoneAndroid,
                title = "Voice handoff",
                detail = if (voiceReady) "Ready" else "Advanced bridge setup needed",
                ready = voiceReady,
            )
            HorizontalDivider(Modifier.padding(start = 49.dp), color = ADColors.Separator)
            ADAssistantAppStatusRow(
                icon = Icons.Outlined.Apps,
                title = "Image handoff",
                detail = if (imageReady) "Ready" else "Advanced bridge and accessibility setup needed",
                ready = imageReady,
            )
        }

        ADSectionTitle("Route")
        ADCard {
            Text(
                if (selectedMode == GlassesAssistantMode.PHONE_ASSISTANT) {
                    "$targetName is currently the assistant-app route."
                } else {
                    "Glasses questions currently use your configured AI."
                },
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.size(14.dp))
            if (selectedMode == GlassesAssistantMode.PHONE_ASSISTANT) {
                OutlinedButton(
                    onClick = {
                        LocalAgentPrefs.setGlassesAssistantMode(context, GlassesAssistantMode.CUSTOM_AI_PROVIDER)
                        refresh()
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text("Use AI") }
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

        ADSectionTitle("Advanced handoff setup")
        ADCard {
            ADAssistantSetupAction(
                title = "Choose Android assistant",
                detail = "Select Gemini or ChatGPT as the phone assistant",
                onClick = ::chooseDefaultAssistant,
            )
            HorizontalDivider(color = ADColors.Separator)
            ADAssistantSetupAction(
                title = "Import automation bridge",
                detail = if (capability.profileCompatible) "Profile already verified" else "Import the matching profile for the selected assistant",
                onClick = ::importProfile,
            )
            HorizontalDivider(color = ADColors.Separator)
            ADAssistantSetupAction(
                title = if (verifying) "Verifying…" else "Verify automation bridge",
                detail = if (capability.profileCompatible) "Verified" else "Confirm the imported profile responds to AD Glasses",
                onClick = { if (!verifying) verifyProfile() },
            )
            HorizontalDivider(color = ADColors.Separator)
            ADAssistantSetupAction(
                title = "Accessibility",
                detail = if (capability.autoInputAccessibilityEnabled) "Image handoff access is enabled" else "Required only for external image-prompt automation",
                onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
            )
        }
    }
}

@Composable
private fun ADAssistantAppStatusRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    detail: String,
    ready: Boolean,
    onClick: (() -> Unit)? = null,
) {
    val clickModifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Row(
        modifier = Modifier.fillMaxWidth().then(clickModifier).padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(38.dp).background(ADColors.SurfaceSubtle, RoundedCornerShape(11.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = ADColors.Ink, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.padding(start = 11.dp).weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = ADColors.Muted)
        }
        when {
            ready -> Icon(Icons.Outlined.CheckCircle, contentDescription = "Ready", tint = ADColors.Success, modifier = Modifier.size(21.dp))
            onClick != null -> Icon(Icons.Rounded.KeyboardArrowRight, contentDescription = null, tint = ADColors.Muted, modifier = Modifier.size(22.dp))
        }
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
            modifier = Modifier.size(38.dp).background(ADColors.SurfaceSubtle, RoundedCornerShape(11.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Settings, contentDescription = null, tint = ADColors.Ink, modifier = Modifier.size(19.dp))
        }
        Column(Modifier.padding(start = 11.dp).weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = ADColors.Muted)
        }
        Icon(Icons.Rounded.KeyboardArrowRight, contentDescription = null, tint = ADColors.Muted, modifier = Modifier.size(22.dp))
    }
}
