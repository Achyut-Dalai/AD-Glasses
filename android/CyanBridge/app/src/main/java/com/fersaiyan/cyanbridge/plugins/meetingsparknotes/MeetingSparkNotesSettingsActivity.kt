package com.fersaiyan.cyanbridge.plugins.meetingsparknotes

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

class MeetingSparkNotesSettingsActivity : AppCompatActivity() {

    private lateinit var composeView: ComposeView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        composeView = installComposeHostWithLegacyAdapter(R.layout.activity_meeting_spark_notes_settings)

        setThemedComposeContent(composeView) {
            MeetingSparkNotesSettingsScreen(
                onBack = ::finish,
                onStartService = {
                    PluginVoicePermissions.ensure(this) { MeetingSparkNotesService.start(this) }
                },
                onStopService = { MeetingSparkNotesService.stop(this) },
                onSummarize = { MeetingSparkNotesService.summarize(this) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeetingSparkNotesSettingsScreen(
    onBack: () -> Unit,
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    onSummarize: () -> Unit,
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var enabled by remember { mutableStateOf(MeetingSparkNotesPreferences.isEnabled(context)) }
    var summaryStyle by remember { mutableStateOf(MeetingSparkNotesPreferences.getSummaryStyle(context)) }
    var includeParticipants by remember { mutableStateOf(MeetingSparkNotesPreferences.isIncludeParticipants(context)) }
    var includeActionItems by remember { mutableStateOf(MeetingSparkNotesPreferences.isIncludeActionItems(context)) }
    var maxHistory by remember { mutableIntStateOf(MeetingSparkNotesPreferences.getMaxHistory(context)) }
    var customPrompt by remember { mutableStateOf(MeetingSparkNotesPreferences.getCustomPrompt(context)) }

    val styleOptions = listOf("concise", "detailed", "action_focused")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Meeting Spark Notes Settings") },
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
                        Text("Meeting Spark Notes enabled", modifier = Modifier.weight(1f))
                        Switch(
                            checked = enabled,
                            onCheckedChange = { newValue ->
                                enabled = newValue
                                MeetingSparkNotesPreferences.setEnabled(context, newValue)
                                CommunityPluginPrefs.setNativePluginEnabled(
                                    context,
                                    NativePluginIds.MEETING_SPARK_NOTES,
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

                }
            }

            SectionTitle("Glasses tab")
            NativePluginShortcutPreference(
                pluginId = NativePluginIds.MEETING_SPARK_NOTES,
                pluginTitle = "Meeting Spark Notes",
            )

            // Section: Summary Style
            SectionTitle("Summary Style")
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Choose how meeting summaries are generated",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        styleOptions.forEach { style ->
                            FilterChip(
                                selected = summaryStyle == style,
                                onClick = {
                                    summaryStyle = style
                                    MeetingSparkNotesPreferences.setSummaryStyle(context, style)
                                },
                                label = { Text(style.replace("_", " ").capitalize()) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                ),
                            )
                        }
                    }
                }
            }

            // Section: Content Options
            SectionTitle("Content Options")
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
                        Text("Include participants", modifier = Modifier.weight(1f))
                        Switch(
                            checked = includeParticipants,
                            onCheckedChange = { newValue ->
                                includeParticipants = newValue
                                MeetingSparkNotesPreferences.setIncludeParticipants(context, newValue)
                            },
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Include action items", modifier = Modifier.weight(1f))
                        Switch(
                            checked = includeActionItems,
                            onCheckedChange = { newValue ->
                                includeActionItems = newValue
                                MeetingSparkNotesPreferences.setIncludeActionItems(context, newValue)
                            },
                        )
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
                        "Additional instructions for meeting summarization.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextField(
                        value = customPrompt,
                        onValueChange = { newValue ->
                            if (newValue.length <= 1500) {
                                customPrompt = newValue
                                MeetingSparkNotesPreferences.setCustomPrompt(context, newValue)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Meeting instructions") },
                        placeholder = { Text("e.g. Focus on decisions made. Always include deadlines.") },
                        minLines = 3,
                        maxLines = 6,
                        supportingText = { Text("${customPrompt.length}/1500") },
                    )
                }
            }

            // Section: History
            SectionTitle("Meeting History")
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Max stored summaries: ${maxHistory}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Slider(
                        value = maxHistory.toFloat(),
                        onValueChange = { newValue ->
                            maxHistory = newValue.toInt()
                            MeetingSparkNotesPreferences.setMaxHistory(context, newValue.toInt())
                        },
                        valueRange = 10f..200f,
                        steps = 18,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            MeetingSparkNotesStore().clear(context)
                            Toast.makeText(context, "History cleared", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Clear History")
                    }
                }
            }

            // Section: Actions
            SectionTitle("Actions")
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Button(
                        onClick = onSummarize,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Summarize Current Meeting")
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
