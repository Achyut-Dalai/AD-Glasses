package com.fersaiyan.cyanbridge.plugins.errandbrain

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fersaiyan.cyanbridge.R
import com.fersaiyan.cyanbridge.plugins.PluginVoicePermissions
import com.fersaiyan.cyanbridge.shared.plugins.NativePluginIds
import com.fersaiyan.cyanbridge.ui.CommunityPluginPrefs
import com.fersaiyan.cyanbridge.ui.installComposeHostWithLegacyAdapter
import com.fersaiyan.cyanbridge.ui.NativePluginShortcutPreference
import com.fersaiyan.cyanbridge.ui.setThemedComposeContent

class ErrandBrainSettingsActivity : AppCompatActivity() {

    private lateinit var composeView: ComposeView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        composeView = installComposeHostWithLegacyAdapter(R.layout.activity_errand_brain_settings)

        setThemedComposeContent(composeView) {
            ErrandBrainSettingsScreen(
                onBack = ::finish,
                onStartService = {
                    PluginVoicePermissions.ensure(this) { ErrandBrainService.start(this) }
                },
                onStopService = { ErrandBrainService.stop(this) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ErrandBrainSettingsScreen(
    onBack: () -> Unit,
    onStartService: () -> Unit,
    onStopService: () -> Unit,
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var enabled by remember { mutableStateOf(ErrandBrainPreferences.isEnabled(context)) }
    var autoCreateTasks by remember { mutableStateOf(ErrandBrainPreferences.isAutoCreateTasks(context)) }
    var voiceCommands by remember { mutableStateOf(ErrandBrainPreferences.isVoiceCommands(context)) }
    var reminderEnabled by remember { mutableStateOf(ErrandBrainPreferences.isReminderEnabled(context)) }
    var defaultPriority by remember { mutableStateOf(ErrandBrainPreferences.getDefaultPriority(context)) }
    var defaultCategory by remember { mutableStateOf(ErrandBrainPreferences.getDefaultCategory(context)) }
    var maxHistory by remember { mutableIntStateOf(ErrandBrainPreferences.getMaxHistory(context)) }
    var customPrompt by remember { mutableStateOf(ErrandBrainPreferences.getCustomPrompt(context)) }

    val priorityOptions = listOf("low", "medium", "high", "urgent")
    val categoryOptions = listOf("personal", "work", "shopping", "health", "finance")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Errand Brain Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Section: General
            SectionTitle("General")
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Errand Brain enabled", modifier = Modifier.weight(1f))
                        Switch(
                            checked = enabled,
                            onCheckedChange = { newValue ->
                                enabled = newValue
                                ErrandBrainPreferences.setEnabled(context, newValue)
                                CommunityPluginPrefs.setNativePluginEnabled(
                                    context,
                                    NativePluginIds.ERRAND_BRAIN,
                                    newValue,
                                )
                                if (newValue) {
                                    onStartService()
                                } else {
                                    onStopService()
                                }
                            },
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Auto-create tasks from voice", modifier = Modifier.weight(1f))
                        Switch(
                            checked = autoCreateTasks,
                            onCheckedChange = { newValue ->
                                autoCreateTasks = newValue
                                ErrandBrainPreferences.setAutoCreateTasks(context, newValue)
                            },
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Voice commands", modifier = Modifier.weight(1f))
                        Switch(
                            checked = voiceCommands,
                            onCheckedChange = { newValue ->
                                voiceCommands = newValue
                                ErrandBrainPreferences.setVoiceCommands(context, newValue)
                            },
                        )
                    }
                }
            }

            SectionTitle("Glasses tab")
            NativePluginShortcutPreference(
                pluginId = NativePluginIds.ERRAND_BRAIN,
                pluginTitle = "Errand Brain",
            )

            // Section: Reminders
            SectionTitle("Reminders")
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Reminders enabled", modifier = Modifier.weight(1f))
                        Switch(
                            checked = reminderEnabled,
                            onCheckedChange = { newValue ->
                                reminderEnabled = newValue
                                ErrandBrainPreferences.setReminderEnabled(context, newValue)
                            },
                        )
                    }

                    if (reminderEnabled) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "Say “remind me in 10 minutes to call Sam” while the plugin is enabled. The reminder is delivered as a phone notification.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Section: Defaults
            SectionTitle("Defaults")
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Default priority",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        priorityOptions.forEach { priority ->
                            FilterChip(
                                selected = defaultPriority == priority,
                                onClick = {
                                    defaultPriority = priority
                                    ErrandBrainPreferences.setDefaultPriority(context, priority)
                                },
                                label = { Text(priority.capitalize()) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                ),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        "Default category",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        categoryOptions.take(5).forEach { category ->
                            FilterChip(
                                selected = defaultCategory == category,
                                onClick = {
                                    defaultCategory = category
                                    ErrandBrainPreferences.setDefaultCategory(context, category)
                                },
                                label = { Text(category.capitalize()) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                ),
                            )
                        }
                    }
                }
            }

            // Section: Custom Instructions
            SectionTitle("Custom Instructions")
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Additional instructions for task parsing.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextField(
                        value = customPrompt,
                        onValueChange = { newValue ->
                            if (newValue.length <= 1000) {
                                customPrompt = newValue
                                ErrandBrainPreferences.setCustomPrompt(context, newValue)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Task parsing instructions") },
                        placeholder = { Text("e.g. Always set due dates. Categorize by project.") },
                        minLines = 2,
                        maxLines = 4,
                        supportingText = { Text("${customPrompt.length}/1000") },
                    )
                }
            }

            // Section: History
            SectionTitle("Task History")
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Max stored tasks: ${maxHistory}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Slider(
                        value = maxHistory.toFloat(),
                        onValueChange = { newValue ->
                            maxHistory = newValue.toInt()
                            ErrandBrainPreferences.setMaxHistory(context, newValue.toInt())
                        },
                        valueRange = 50f..1000f,
                        steps = 94,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            ErrandBrainStore().clear(context)
                            Toast.makeText(context, "History cleared", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Clear History")
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp),
    )
}
