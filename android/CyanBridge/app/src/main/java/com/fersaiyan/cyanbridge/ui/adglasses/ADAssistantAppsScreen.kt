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
    val appMode = selectedMode == GlassesAssistantMode.PHONE_ASSISTANT

    ADPageLayout("Assistant apps", onBack) {
        Text(
            "Assistant-app handoff is optional. Your configured AD AI remains the normal path unless you deliberately route glasses requests to Android’s assistant.",
            style = MaterialTheme.typography.bodyMedium,
            color = ADColors.Muted,
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(30.dp),
            color = ADColors.Ink,
            contentColor = ADColors.Surface,
        ) {
            Row(modifier = Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(54.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = ADColors.Surface.copy(alpha = 0.13f),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        ADGlyphIcon(ADGlyph.AI, ADColors.Surface, Modifier.size(30.dp))
                    }
                }
                Column(Modifier.padding(start = 13.dp).weight(1f)) {
                    Text(
                        if (appMode) targetName else "AD Glasses AI",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        if (appMode) "External assistant route is active" else "Configured AI is active",
                        style = MaterialTheme.typography.bodySmall,
                        color = ADColors.Surface.copy(alpha = 0.68f),
                    )
                }
                ADStatusChip(
                    if (appMode) "APP ROUTE" else "AD AI",
                    ADStatusTone.NEUTRAL,
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            ADSectionTitle("Readiness")
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
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
                ADAssistantReadinessCard(
                    icon = Icons.Outlined.Apps,
                    title = "Images",
                    detail = if (imageReady) "Ready" else "Setup needed",
                    ready = imageReady,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            ADSectionTitle("Route")
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = ADColors.Surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(Modifier.padding(15.dp)) {
                    Text(
                        if (appMode) "$targetName currently receives supported assistant-app requests." else "Glasses questions currently use your configured AI.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.size(12.dp))
                    if (appMode) {
                        OutlinedButton(
                            onClick = {
                                LocalAgentPrefs.setGlassesAssistantMode(context, GlassesAssistantMode.CUSTOM_AI_PROVIDER)
                                refresh()
                            },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                            shape = RoundedCornerShape(18.dp),
                        ) { Text("Use AD Glasses AI", style = MaterialTheme.typography.labelLarge) }
                    } else {
                        ADPrimaryButton(
                            text = "Use assistant app",
                            onClick = {
                                if (!voiceReady) {
                                    show(ExternalAssistantAutomationPolicy.voiceBlockingReason(capability) ?: "Assistant app handoff is not ready.")
                                } else {
                                    LocalAgentPrefs.setGlassesAssistantMode(context, GlassesAssistantMode.PHONE_ASSISTANT)
                                    refresh()
                                }
                            },
                        )
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            ADSectionTitle("Advanced setup")
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = ADColors.Surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
                    ADAssistantSetupAction(
                        title = "Choose Android assistant",
                        detail = "Select Gemini or ChatGPT as the phone assistant",
                        onClick = ::chooseDefaultAssistant,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ADAssistantSetupAction(
                        title = "Import automation bridge",
                        detail = if (capability.profileCompatible) "Profile already verified" else "Import the matching profile for the selected assistant",
                        onClick = ::importProfile,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ADAssistantSetupAction(
                        title = if (verifying) "Verifying…" else "Verify automation bridge",
                        detail = if (capability.profileCompatible) "Verified" else "Confirm the imported profile responds to AD Glasses",
                        onClick = { if (!verifying) verifyProfile() },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ADAssistantSetupAction(
                        title = "Accessibility",
                        detail = if (capability.autoInputAccessibilityEnabled) "Image handoff access is enabled" else "Required only for external image-prompt automation",
                        onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                    )
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
        modifier = modifier.heightIn(min = 118.dp).then(clickModifier),
        shape = RoundedCornerShape(22.dp),
        color = ADColors.Surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(13.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).background(if (ready) ADColors.Ink else ADColors.SurfaceSubtle, RoundedCornerShape(13.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, null, tint = if (ready) ADColors.Surface else ADColors.Ink, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.weight(1f))
                if (ready) {
                    Icon(Icons.Outlined.CheckCircle, "Ready", tint = ADColors.Success, modifier = Modifier.size(18.dp))
                }
            }
            Column {
                Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Text(detail, style = MaterialTheme.typography.labelSmall, color = ADColors.Muted, maxLines = 2)
            }
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
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(42.dp).background(ADColors.SurfaceSubtle, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Settings, contentDescription = null, tint = ADColors.Ink, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.padding(start = 11.dp).weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = ADColors.Muted)
        }
        Surface(shape = CircleShape, color = ADColors.SurfaceSubtle) {
            Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) {
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = ADColors.Muted,
                    modifier = Modifier.size(17.dp),
                )
            }
        }
    }
}
