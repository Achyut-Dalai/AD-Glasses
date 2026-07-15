package com.fersaiyan.cyanbridge.ui.icons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DevicesOther
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.ui.graphics.vector.ImageVector
import com.fersaiyan.cyanbridge.shared.icons.AppIcon

fun AppIcon.imageVector(): ImageVector = when (this) {
    AppIcon.Glasses -> Icons.Outlined.DevicesOther
    AppIcon.Chat -> Icons.Outlined.ChatBubbleOutline
    AppIcon.Recordings -> Icons.Outlined.LibraryMusic
    AppIcon.Settings -> Icons.Outlined.Settings
    AppIcon.Plugins -> Icons.Outlined.Extension
    AppIcon.Camera -> Icons.Outlined.PhotoCamera
    AppIcon.Video -> Icons.Outlined.Videocam
    AppIcon.Microphone -> Icons.Outlined.Mic
    AppIcon.Battery -> Icons.Outlined.BatteryFull
    AppIcon.Sync -> Icons.Outlined.Sync
    AppIcon.Model -> Icons.Outlined.SmartToy
    AppIcon.Send -> Icons.AutoMirrored.Outlined.Send
    AppIcon.Appearance -> Icons.Outlined.Palette
    AppIcon.Add -> Icons.Outlined.Add
    AppIcon.Delete -> Icons.Outlined.DeleteOutline
    AppIcon.Back -> Icons.AutoMirrored.Outlined.ArrowBack
    AppIcon.More -> Icons.Outlined.MoreVert
    AppIcon.Attachment -> Icons.Outlined.AttachFile
    AppIcon.Stop -> Icons.Outlined.Stop
    AppIcon.Close -> Icons.Outlined.Close
}
