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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(ADColors.Graphite, RoundedCornerShape(20.dp))
                .padding(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(46.dp).background(Color.White.copy(alpha = 0.10f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.Apps, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Text(
                        if (assistantRouteActive) "Assistant app route" else "Configured AI route",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                    )
                    Text(
                        if (assistantRouteActive) targetName else "Glasses questions use the AI selected on the AI tab",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.66f),
                    )
                }
                Box(Modifier.size(8.dp).background(if (assistantRouteActive) ADColors.Cyan else Color.White.copy(alpha = 0.38f), RoundedCornerShape(4.dp)))
            }

            Spacer(Modifier.size(14.dp))
            if (assistantRouteActive) {
                OutlinedButton(
                    onClick = {
                        LocalAgentPrefs.setGlassesAssistantMode(context, GlassesAssistantMode.CUSTOM_AI_PROVIDER)
                        refresh()
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp),
                    shape = RoundedCornerShape(13.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.30f)),
                ) { Text("Use configured AI") }
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
                    modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp),
                    shape = RoundedCornerShape(13.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = ADColors.Ink),
                ) { Text("Use assistant app") }
            }
        }

        ADSectionTitle("Assistant readiness")
        ADCard {
            ADAssistantAppStatusRow(
                icon = Icons.Outlined.Android,
                title = "Android assistant",
                detail = targetName,
                ready = capability.target != ImageAutomationTarget.NONE && capability.targetPackage != null,
                onClick = ::chooseDefaultAssistant,
            )
            HorizontalDivider(Modifier.padding(start = 52.dp), color = ADColors.Separator)
            ADAssistantAppStatusRow(
                icon = Icons.Outlined.PhoneAndroid,
                title = "Voice handoff",
                detail = if (voiceReady) "Ready" else "Bridge setup needed",
                ready = voiceReady,
            )
            HorizontalDivider(Modifier.padding(start = 52.dp), color = ADColors.Separator)
            ADAssistantAppStatusRow(
                icon = Icons.Outlined.Apps,
                title = "Image handoff",
                detail = if (imageReady) "Ready" else "Bridge and accessibility setup needed",
                ready = imageReady,
            )
        }

        Column {
            ADSectionTitle("Advanced bridge")
            Spacer(Modifier.size(4.dp))
            Text(
                "Only needed when you want AD Glasses to automate an installed assistant app instead of using the configured AI directly.",
                style = MaterialTheme.typography.bodySmall,
                color = ADColors.Muted,
                modifier = Modifier.padding(horizontal = 2.dp),
            )
        }

        ADCard {
            ADAssistantSetupAction(
                title = "Choose Android assistant",
                detail = "Select Gemini or ChatGPT as the phone assistant",
                onClick = ::chooseDefaultAssistant,
            )
            HorizontalDivider(Modifier.padding(start = 52.dp), color = ADColors.Separator)
            ADAssistantSetupAction(
                title = "Import automation bridge",
                detail = if (capability.profileCompatible) "Profile already verified" else "Import the matching profile for the selected assistant",
                onClick = ::importProfile,
            )
            HorizontalDivider(Modifier.padding(start = 52.dp), color = ADColors.Separator)
            ADAssistantSetupAction(
                title = if (verifying) "Verifying…" else "Verify automation bridge",
                detail = if (capability.profileCompatible) "Verified" else "Confirm the imported profile responds to AD Glasses",
                onClick = { if (!verifying) verifyProfile() },
            )
            HorizontalDivider(Modifier.padding(start = 52.dp), color = ADColors.Separator)
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
        modifier = Modifier.fillMaxWidth().then(clickModifier).padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(if (ready) ADColors.SuccessSoft else ADColors.SurfaceSubtle, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (ready) ADColors.Success else ADColors.Ink,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = ADColors.Muted)
        }
        when {
            ready -> Icon(Icons.Outlined.CheckCircle, contentDescription = "Ready", tint = ADColors.Success, modifier = Modifier.size(21.dp))
            onClick != null -> Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = ADColors.Muted,
                modifier = Modifier.size(22.dp),
            )
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
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(40.dp).background(ADColors.SurfaceSubtle, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Settings, contentDescription = null, tint = ADColors.Ink, modifier = Modifier.size(19.dp))
        }
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = ADColors.Muted)
        }
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = ADColors.Muted,
            modifier = Modifier.size(22.dp),
        )
    }
}
