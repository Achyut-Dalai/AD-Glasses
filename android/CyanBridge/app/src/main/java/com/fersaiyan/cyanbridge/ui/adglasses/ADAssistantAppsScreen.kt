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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.fersaiyan.cyanbridge.agent.LocalAgentPrefs
import com.fersaiyan.cyanbridge.ai.image.ExternalAssistantAutomationInspector
import com.fersaiyan.cyanbridge.ai.image.ExternalAssistantAutomationPolicy
import com.fersaiyan.cyanbridge.ai.image.ImageAutomationTarget
import com.fersaiyan.cyanbridge.shared.glasses.GlassesAssistantMode

/** Direct handoff to the phone's selected Gemini or ChatGPT assistant. */
@Composable
internal fun ADAssistantAppsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var capability by remember { mutableStateOf(ExternalAssistantAutomationInspector.inspect(context)) }
    var selectedMode by remember { mutableStateOf(LocalAgentPrefs.getGlassesAssistantMode(context)) }

    fun refresh() {
        capability = ExternalAssistantAutomationInspector.inspect(context)
        selectedMode = LocalAgentPrefs.getGlassesAssistantMode(context)
    }

    fun show(message: String) = Toast.makeText(context, message, Toast.LENGTH_LONG).show()

    fun chooseDefaultAssistant() {
        runCatching { context.startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)) }
            .onFailure { context.startActivity(Intent(Settings.ACTION_SETTINGS)) }
    }

    val voiceReady = ExternalAssistantAutomationPolicy.voiceBlockingReason(capability) == null
    val imageReady = ExternalAssistantAutomationPolicy.imageBlockingReason(capability) == null
    val targetName = capability.target.takeUnless { it == ImageAutomationTarget.NONE }?.label ?: "Not selected"

    ADPageLayout("Assistant apps", onBack) {
        ADCard {
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier.size(38.dp).background(ADColors.SurfaceSubtle, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.Apps, contentDescription = null, tint = ADColors.Ink, modifier = Modifier.size(19.dp))
                }
                Column(Modifier.padding(start = 10.dp).weight(1f)) {
                    Text("Direct assistant handoff", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.size(2.dp))
                    Text(
                        "Gemini is recommended for direct handoff. Gemini or ChatGPT owns the answer, voice and conversation; AD only launches it.",
                        style = MaterialTheme.typography.bodySmall,
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
            HorizontalDivider(Modifier.padding(start = 43.dp), color = ADColors.Separator)
            ADAssistantAppStatusRow(
                icon = Icons.Outlined.PhoneAndroid,
                title = "Voice handoff",
                detail = if (voiceReady) "Ready" else "Choose an installed assistant",
                ready = voiceReady,
            )
            HorizontalDivider(Modifier.padding(start = 43.dp), color = ADColors.Separator)
            ADAssistantAppStatusRow(
                icon = Icons.Outlined.Apps,
                title = "Image handoff",
                detail = if (imageReady) "Ready" else "AD Glasses accessibility access needed",
                ready = imageReady,
            )
        }

        ADSectionTitle("Route")
        ADCard {
            Text(
                if (selectedMode == GlassesAssistantMode.PHONE_ASSISTANT) {
                    "$targetName is currently the glasses handoff app. It speaks its own replies."
                } else {
                    "Glasses questions currently use the selected local or cloud AI."
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.size(9.dp))
            if (selectedMode == GlassesAssistantMode.PHONE_ASSISTANT) {
                OutlinedButton(
                    onClick = {
                        LocalAgentPrefs.setGlassesAssistantMode(context, GlassesAssistantMode.CUSTOM_AI_PROVIDER)
                        refresh()
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp),
                ) { Text("Use local or cloud AI") }
            } else {
                Button(
                    onClick = {
                        val reason = ExternalAssistantAutomationPolicy.voiceBlockingReason(capability)
                        if (reason != null) show(reason) else {
                            LocalAgentPrefs.setGlassesAssistantMode(context, GlassesAssistantMode.PHONE_ASSISTANT)
                            refresh()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ADColors.Ink),
                ) { Text("Use Gemini / phone assistant") }
            }
        }

        ADSectionTitle("Handoff setup")
        ADCard {
            ADAssistantSetupAction(
                title = "Choose Android assistant",
                detail = "Gemini is recommended; either app speaks its own replies",
                onClick = ::chooseDefaultAssistant,
            )
            HorizontalDivider(color = ADColors.Separator)
            ADAssistantSetupAction(
                title = "AD Glasses accessibility",
                detail = if (capability.adAccessibilityConnected) "Enabled for image handoff" else "Enable it to submit image questions",
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
        modifier = Modifier.fillMaxWidth().then(clickModifier).padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(34.dp).background(ADColors.SurfaceSubtle, RoundedCornerShape(9.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = ADColors.Ink, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.padding(start = 9.dp).weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = ADColors.Muted)
        }
        when {
            ready -> Icon(Icons.Outlined.CheckCircle, contentDescription = "Ready", tint = ADColors.Success, modifier = Modifier.size(19.dp))
            onClick != null -> Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = ADColors.Muted, modifier = Modifier.size(19.dp))
        }
    }
}

@Composable
private fun ADAssistantSetupAction(title: String, detail: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(34.dp).background(ADColors.SurfaceSubtle, RoundedCornerShape(9.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Settings, contentDescription = null, tint = ADColors.Ink, modifier = Modifier.size(17.dp))
        }
        Column(Modifier.padding(start = 9.dp).weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = ADColors.Muted)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = ADColors.Muted, modifier = Modifier.size(19.dp))
    }
}
