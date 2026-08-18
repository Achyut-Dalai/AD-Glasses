package com.fersaiyan.cyanbridge.shared.ui.recordings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fersaiyan.cyanbridge.shared.generated.resources.*
import com.fersaiyan.cyanbridge.shared.icons.AppIcon
import com.fersaiyan.cyanbridge.shared.icons.imageVector
import com.fersaiyan.cyanbridge.shared.recordings.SyncedMediaItem
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class, ExperimentalResourceApi::class)
@Composable
fun SyncedMediaGalleryScreen(mediaItems: List<SyncedMediaItem>, isLoading: Boolean, folderHint: String, loadThumbnail: suspend (String) -> ImageBitmap?, onNavigateBack: () -> Unit, onRefresh: () -> Unit, onOpenMedia: (SyncedMediaItem) -> Unit, onShareItems: (List<SyncedMediaItem>) -> Unit, onDeleteItems: (List<SyncedMediaItem>) -> Unit) {
    var selectedItems by remember { mutableStateOf<Set<SyncedMediaItem>>(emptySet()) }
    if (selectedItems.isNotEmpty()) {
        ModalBottomSheet(onDismissRequest = { selectedItems = emptySet() }, sheetState = rememberBottomSheetScaffoldState().bottomSheetState) {
            Column {
                Text(text = stringResource(Res.string.media_selected, selectedItems.size), modifier = Modifier.fillMaxWidth().padding(16.dp), style = MaterialTheme.typography.titleMedium)
                Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface, tonalElevation = 8.dp) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        DropdownMenuItem(text = { Text(stringResource(Res.string.media_share), style = MaterialTheme.typography.bodyLarge) }, onClick = { onShareItems(selectedItems.toList()); selectedItems = emptySet() }, leadingIcon = { Icon(AppIcon.Share.imageVector(), null, Modifier.padding(end = 8.dp)) })
                        DropdownMenuItem(text = { Text(stringResource(Res.string.media_delete), style = MaterialTheme.typography.bodyLarge) }, onClick = { onDeleteItems(selectedItems.toList()); selectedItems = emptySet() }, leadingIcon = { Icon(AppIcon.Delete.imageVector(), null, Modifier.padding(end = 8.dp), tint = MaterialTheme.colorScheme.error) }, trailingIcon = { Text(stringResource(Res.string.media_delete), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error) })
                    }
                }
            }
        }
    }
    Scaffold(contentWindowInsets = WindowInsets.safeDrawing, topBar = {
        TopAppBar(title = { Text(if (selectedItems.isEmpty()) stringResource(Res.string.media_title) else stringResource(Res.string.media_selected, selectedItems.size)) }, navigationIcon = { IconButton(onClick = { if (selectedItems.isNotEmpty()) selectedItems = emptySet() else onNavigateBack() }) { Icon(AppIcon.Back.imageVector(), stringResource(Res.string.media_back)) } }, actions = { if (selectedItems.isEmpty()) IconButton(onClick = onRefresh) { Icon(AppIcon.Sync.imageVector(), stringResource(Res.string.media_refresh)) } else IconButton(onClick = { selectedItems = emptySet() }) { Icon(AppIcon.Back.imageVector(), stringResource(Res.string.media_deselect_all)) } })
    }) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding).consumeWindowInsets(innerPadding)) {
            if (selectedItems.isEmpty()) Text(folderHint, Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            when {
                isLoading -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Companion.Center) { CircularProgressIndicator() }
                mediaItems.isEmpty() -> Box(Modifier.fillMaxWidth().weight(1f).padding(32.dp), contentAlignment = Companion.Center) { Text(stringResource(Res.string.media_empty), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                else -> LazyVerticalGrid(columns = GridCells.Adaptive(minSize = 112.dp), modifier = Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(mediaItems, key = { "${it.id}-${it.isVideo}" }) { item ->
                        var thumbnail by remember(item.contentUriString) { mutableStateOf<ImageBitmap?>(null) }
                        LaunchedEffect(item.contentUriString) { thumbnail = loadThumbnail(item.contentUriString) }
                        SyncedMediaTile(item, thumbnail, selectedItems.contains(item), onClick = { if (selectedItems.isNotEmpty()) selectedItems = if (selectedItems.contains(item)) selectedItems - item else selectedItems + item else onOpenMedia(item) }, onLongClick = { selectedItems = setOf(item) })
                    }
                }
            }
        }
    }
}

@Composable
private fun SyncedMediaTile(item: SyncedMediaItem, thumbnail: ImageBitmap?, isSelected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth().aspectRatio(1f).combinedClickable(onClick = onClick, onLongClick = onLongClick), color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium, tonalElevation = if (isSelected) 4.dp else 0.dp) {
        Box {
            if (thumbnail != null) Image(bitmap = thumbnail, contentDescription = item.displayName, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop) else Icon(AppIcon.Image.imageVector(), item.displayName, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.Center))
            if (item.isVideo) Surface(color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f), contentColor = MaterialTheme.colorScheme.onPrimary, shape = MaterialTheme.shapes.large) { Icon(AppIcon.Play.imageVector(), stringResource(Res.string.media_video), Modifier.padding(8.dp)) }
            if (isSelected) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)))
                Box(Modifier.padding(8.dp).align(Alignment.TopEnd)) { Surface(color = MaterialTheme.colorScheme.primary, shape = MaterialTheme.shapes.small, modifier = Modifier.size(24.dp)) { Box(Modifier.fillMaxSize().padding(4.dp), contentAlignment = Companion.Center) { Icon(AppIcon.Check.imageVector(), stringResource(Res.string.media_selected_content_description), Modifier.fillMaxSize(), tint = MaterialTheme.colorScheme.onPrimary) } } }
            }
        }
    }
}
