package com.fersaiyan.cyanbridge.shared.ui.notes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fersaiyan.cyanbridge.shared.notes.NoteSummary
import com.fersaiyan.cyanbridge.shared.generated.resources.*
import com.fersaiyan.cyanbridge.shared.icons.AppIcon
import com.fersaiyan.cyanbridge.shared.icons.imageVector
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class, ExperimentalResourceApi::class)
@Composable
fun NotesListScreen(
    notes: List<NoteSummary>,
    showCreateDialog: Boolean,
    formatTimestamp: (Long) -> String,
    onOpenNote: (NoteSummary) -> Unit,
    onShowCreateDialog: () -> Unit,
    onDismissCreateDialog: () -> Unit,
    onCreateFromTranscript: (title: String, transcript: String) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.local_agent_notes)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(AppIcon.Back.imageVector(), contentDescription = stringResource(Res.string.action_back))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onShowCreateDialog) {
                Icon(AppIcon.Add.imageVector(), contentDescription = stringResource(Res.string.local_agent_new_note))
            }
        },
    ) { innerPadding ->
        if (notes.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(stringResource(Res.string.local_agent_no_notes), style = MaterialTheme.typography.titleMedium)
                Text(
                    text = stringResource(Res.string.local_agent_no_notes_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(notes, key = { it.id }) { note ->
                    Card(onClick = { onOpenNote(note) }, modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(note.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = formatTimestamp(note.createdAt),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = note.summary,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 3,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateNoteDialog(
            onDismiss = onDismissCreateDialog,
            onCreate = onCreateFromTranscript,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteDetailScreen(
    title: String,
    summary: String,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(AppIcon.Back.imageVector(), contentDescription = stringResource(Res.string.action_back))
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .padding(16.dp),
        ) {
            SelectionContainer(modifier = Modifier.weight(1f)) {
                Text(summary, style = MaterialTheme.typography.bodyLarge)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onCopy) { Text(stringResource(Res.string.local_agent_copy)) }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onShare) { Text(stringResource(Res.string.action_share)) }
            }
        }
    }
}

@Composable
private fun CreateNoteDialog(
    onDismiss: () -> Unit,
    onCreate: (title: String, transcript: String) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var transcript by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.local_agent_new_note_from_transcript)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(Res.string.local_agent_title_optional)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = transcript,
                    onValueChange = { transcript = it },
                    label = { Text(stringResource(Res.string.local_agent_paste_transcript)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 5,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(title, transcript) }) { Text(stringResource(Res.string.local_agent_create)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) }
        },
    )
}
