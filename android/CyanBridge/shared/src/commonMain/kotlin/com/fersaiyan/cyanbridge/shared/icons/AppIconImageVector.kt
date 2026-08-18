package com.fersaiyan.cyanbridge.shared.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Small, product-owned icon set used by shared Compose UI.
 *
 * Keeping these vectors here avoids depending on the frozen Material-icons
 * artifact and keeps Android/iOS icon semantics identical without pulling a
 * legacy icon library into shared code.
 */
private object ADSharedIcons {
    private fun outlined(name: String, draw: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathBuilder = draw,
            )
        }.build()

    val Glasses = outlined("AD.Glasses") {
        moveTo(3f, 12f); lineTo(5f, 8f); lineTo(10f, 8f); lineTo(11f, 12f)
        moveTo(13f, 12f); lineTo(14f, 8f); lineTo(19f, 8f); lineTo(21f, 12f)
        moveTo(11f, 10f); lineTo(13f, 10f)
        moveTo(3f, 12f); curveTo(3f, 15f, 5f, 17f, 8f, 17f); curveTo(11f, 17f, 12f, 15f, 12f, 12f)
        moveTo(12f, 12f); curveTo(12f, 15f, 13f, 17f, 16f, 17f); curveTo(19f, 17f, 21f, 15f, 21f, 12f)
    }

    val Chat = outlined("AD.Chat") {
        moveTo(4f, 5f); lineTo(20f, 5f); lineTo(20f, 16f); lineTo(11f, 16f); lineTo(7f, 20f); lineTo(7f, 16f); lineTo(4f, 16f); close()
        moveTo(8f, 9f); lineTo(16f, 9f)
        moveTo(8f, 12f); lineTo(14f, 12f)
    }

    val Recordings = outlined("AD.Recordings") {
        moveTo(6f, 4f); lineTo(18f, 4f); lineTo(18f, 20f); lineTo(6f, 20f); close()
        moveTo(9f, 8f); lineTo(15f, 8f)
        moveTo(9f, 12f); lineTo(15f, 12f)
        moveTo(9f, 16f); lineTo(13f, 16f)
    }

    val Settings = outlined("AD.Settings") {
        moveTo(12f, 3.5f); lineTo(12f, 6f)
        moveTo(12f, 18f); lineTo(12f, 20.5f)
        moveTo(3.5f, 12f); lineTo(6f, 12f)
        moveTo(18f, 12f); lineTo(20.5f, 12f)
        moveTo(6f, 6f); lineTo(7.8f, 7.8f)
        moveTo(16.2f, 16.2f); lineTo(18f, 18f)
        moveTo(18f, 6f); lineTo(16.2f, 7.8f)
        moveTo(7.8f, 16.2f); lineTo(6f, 18f)
        moveTo(12f, 8f); curveTo(14.2f, 8f, 16f, 9.8f, 16f, 12f); curveTo(16f, 14.2f, 14.2f, 16f, 12f, 16f); curveTo(9.8f, 16f, 8f, 14.2f, 8f, 12f); curveTo(8f, 9.8f, 9.8f, 8f, 12f, 8f); close()
    }

    val Plugins = outlined("AD.Plugins") {
        moveTo(8f, 4f); lineTo(12f, 4f); lineTo(12f, 8f); lineTo(16f, 8f); lineTo(16f, 12f); lineTo(20f, 12f); lineTo(20f, 16f); lineTo(16f, 16f); lineTo(16f, 20f); lineTo(12f, 20f); lineTo(12f, 16f); lineTo(8f, 16f); lineTo(8f, 12f); lineTo(4f, 12f); lineTo(4f, 8f); lineTo(8f, 8f); close()
    }

    val Camera = outlined("AD.Camera") {
        moveTo(4f, 8f); lineTo(8f, 8f); lineTo(9.5f, 6f); lineTo(14.5f, 6f); lineTo(16f, 8f); lineTo(20f, 8f); lineTo(20f, 18f); lineTo(4f, 18f); close()
        moveTo(12f, 10f); curveTo(14.2f, 10f, 16f, 11.8f, 16f, 14f); curveTo(16f, 16.2f, 14.2f, 18f, 12f, 18f)
        moveTo(12f, 10f); curveTo(9.8f, 10f, 8f, 11.8f, 8f, 14f); curveTo(8f, 16.2f, 9.8f, 18f, 12f, 18f)
    }

    val Image = outlined("AD.Image") {
        moveTo(4f, 5f); lineTo(20f, 5f); lineTo(20f, 19f); lineTo(4f, 19f); close()
        moveTo(7f, 15f); lineTo(10.5f, 11f); lineTo(13f, 14f); lineTo(15f, 12f); lineTo(19f, 17f)
        moveTo(8f, 8.5f); lineTo(8f, 8.7f)
    }

    val Video = outlined("AD.Video") {
        moveTo(4f, 7f); lineTo(15f, 7f); lineTo(15f, 17f); lineTo(4f, 17f); close()
        moveTo(15f, 10f); lineTo(20f, 7f); lineTo(20f, 17f); lineTo(15f, 14f)
    }

    val Microphone = outlined("AD.Microphone") {
        moveTo(9f, 5f); curveTo(9f, 3.5f, 10.3f, 3f, 12f, 3f); curveTo(13.7f, 3f, 15f, 3.5f, 15f, 5f); lineTo(15f, 12f); curveTo(15f, 13.7f, 13.7f, 15f, 12f, 15f); curveTo(10.3f, 15f, 9f, 13.7f, 9f, 12f); close()
        moveTo(6f, 11f); curveTo(6f, 15f, 8.5f, 18f, 12f, 18f); curveTo(15.5f, 18f, 18f, 15f, 18f, 11f)
        moveTo(12f, 18f); lineTo(12f, 21f); moveTo(9f, 21f); lineTo(15f, 21f)
    }

    val Battery = outlined("AD.Battery") {
        moveTo(4f, 8f); lineTo(18f, 8f); lineTo(18f, 16f); lineTo(4f, 16f); close()
        moveTo(18f, 10f); lineTo(20f, 10f); lineTo(20f, 14f); lineTo(18f, 14f)
        moveTo(7f, 11f); lineTo(14f, 11f); lineTo(14f, 13f); lineTo(7f, 13f); close()
    }

    val Sync = outlined("AD.Sync") {
        moveTo(4f, 9f); curveTo(6f, 5f, 10f, 4f, 14f, 5f); lineTo(18f, 7f); moveTo(18f, 7f); lineTo(15f, 3.5f); moveTo(18f, 7f); lineTo(14f, 8f)
        moveTo(20f, 15f); curveTo(18f, 19f, 14f, 20f, 10f, 19f); lineTo(6f, 17f); moveTo(6f, 17f); lineTo(9f, 20.5f); moveTo(6f, 17f); lineTo(10f, 16f)
    }

    val Model = outlined("AD.Model") {
        moveTo(7f, 7f); lineTo(17f, 7f); lineTo(17f, 17f); lineTo(7f, 17f); close()
        moveTo(9f, 3f); lineTo(9f, 7f); moveTo(15f, 3f); lineTo(15f, 7f)
        moveTo(9f, 17f); lineTo(9f, 21f); moveTo(15f, 17f); lineTo(15f, 21f)
        moveTo(3f, 9f); lineTo(7f, 9f); moveTo(3f, 15f); lineTo(7f, 15f)
        moveTo(17f, 9f); lineTo(21f, 9f); moveTo(17f, 15f); lineTo(21f, 15f)
        moveTo(10f, 11f); lineTo(14f, 11f); moveTo(10f, 14f); lineTo(14f, 14f)
    }

    val Send = outlined("AD.Send") { moveTo(4f, 12f); lineTo(20f, 4f); lineTo(15f, 20f); lineTo(11.5f, 13f); close(); moveTo(11.5f, 13f); lineTo(20f, 4f) }
    val Appearance = outlined("AD.Appearance") { moveTo(4f, 12f); curveTo(4f, 7.5f, 7.5f, 4f, 12f, 4f); curveTo(16.5f, 4f, 20f, 7.5f, 20f, 12f); curveTo(20f, 16.5f, 16.5f, 20f, 12f, 20f); curveTo(10f, 20f, 9f, 19f, 9f, 17.5f); curveTo(9f, 16f, 10f, 15f, 12f, 15f); lineTo(14f, 15f); curveTo(16f, 15f, 17f, 13.5f, 17f, 12f); curveTo(17f, 10.5f, 16f, 9f, 14f, 9f); lineTo(12f, 9f) }
    val Add = outlined("AD.Add") { moveTo(12f, 5f); lineTo(12f, 19f); moveTo(5f, 12f); lineTo(19f, 12f) }
    val Delete = outlined("AD.Delete") { moveTo(7f, 7f); lineTo(17f, 7f); moveTo(9f, 7f); lineTo(9f, 5f); lineTo(15f, 5f); lineTo(15f, 7f); moveTo(8f, 9f); lineTo(9f, 20f); lineTo(15f, 20f); lineTo(16f, 9f); moveTo(11f, 10f); lineTo(11f, 17f); moveTo(13f, 10f); lineTo(13f, 17f) }
    val Back = outlined("AD.Back") { moveTo(19f, 12f); lineTo(5f, 12f); moveTo(5f, 12f); lineTo(11f, 6f); moveTo(5f, 12f); lineTo(11f, 18f) }
    val More = outlined("AD.More") { moveTo(12f, 5f); lineTo(12f, 5.2f); moveTo(12f, 11.9f); lineTo(12f, 12.1f); moveTo(12f, 18.8f); lineTo(12f, 19f) }
    val Attachment = outlined("AD.Attachment") { moveTo(8f, 12f); lineTo(13.5f, 6.5f); curveTo(15f, 5f, 17.5f, 5f, 19f, 6.5f); curveTo(20.5f, 8f, 20.5f, 10.5f, 19f, 12f); lineTo(11f, 20f); curveTo(8.5f, 22.5f, 4.5f, 22f, 3f, 19.5f); curveTo(1.5f, 17f, 2f, 14.5f, 4f, 12.5f); lineTo(12f, 4.5f) }
    val Play = outlined("AD.Play") { moveTo(9f, 6f); lineTo(18f, 12f); lineTo(9f, 18f); close() }
    val Pause = outlined("AD.Pause") { moveTo(9f, 6f); lineTo(9f, 18f); moveTo(15f, 6f); lineTo(15f, 18f) }
    val Stop = outlined("AD.Stop") { moveTo(7f, 7f); lineTo(17f, 7f); lineTo(17f, 17f); lineTo(7f, 17f); close() }
    val Close = outlined("AD.Close") { moveTo(6f, 6f); lineTo(18f, 18f); moveTo(18f, 6f); lineTo(6f, 18f) }
    val Bluetooth = outlined("AD.Bluetooth") {
        moveTo(12f, 3f); lineTo(17f, 8f); lineTo(7f, 16f); lineTo(12f, 21f); close()
        moveTo(12f, 3f); lineTo(12f, 21f)
        moveTo(7f, 8f); lineTo(17f, 16f)
    }
    val BluetoothSearching = outlined("AD.BluetoothSearching") {
        moveTo(10f, 4f); lineTo(15f, 8f); lineTo(7f, 15f); lineTo(10f, 19f); close()
        moveTo(10f, 4f); lineTo(10f, 19f)
        moveTo(7f, 8f); lineTo(15f, 15f)
        moveTo(17f, 7f); curveTo(19f, 9f, 19f, 14f, 17f, 16f)
        moveTo(19f, 4f); curveTo(23f, 8f, 23f, 15f, 19f, 19f)
    }
    val ChevronRight = outlined("AD.ChevronRight") { moveTo(9f, 5f); lineTo(16f, 12f); lineTo(9f, 19f) }
    val ExpandLess = outlined("AD.ExpandLess") { moveTo(6f, 15f); lineTo(12f, 9f); lineTo(18f, 15f) }
    val ExpandMore = outlined("AD.ExpandMore") { moveTo(6f, 9f); lineTo(12f, 15f); lineTo(18f, 9f) }
}

fun AppIcon.imageVector(): ImageVector = when (this) {
    AppIcon.Glasses -> ADSharedIcons.Glasses
    AppIcon.Chat -> ADSharedIcons.Chat
    AppIcon.Recordings -> ADSharedIcons.Recordings
    AppIcon.Settings -> ADSharedIcons.Settings
    AppIcon.Plugins -> ADSharedIcons.Plugins
    AppIcon.Camera -> ADSharedIcons.Camera
    AppIcon.Image -> ADSharedIcons.Image
    AppIcon.Video -> ADSharedIcons.Video
    AppIcon.Microphone -> ADSharedIcons.Microphone
    AppIcon.Battery -> ADSharedIcons.Battery
    AppIcon.Sync -> ADSharedIcons.Sync
    AppIcon.Model -> ADSharedIcons.Model
    AppIcon.Send -> ADSharedIcons.Send
    AppIcon.Appearance -> ADSharedIcons.Appearance
    AppIcon.Add -> ADSharedIcons.Add
    AppIcon.Delete -> ADSharedIcons.Delete
    AppIcon.Back -> ADSharedIcons.Back
    AppIcon.More -> ADSharedIcons.More
    AppIcon.Attachment -> ADSharedIcons.Attachment
    AppIcon.Play -> ADSharedIcons.Play
    AppIcon.Pause -> ADSharedIcons.Pause
    AppIcon.Stop -> ADSharedIcons.Stop
    AppIcon.Close -> ADSharedIcons.Close
    AppIcon.Bluetooth -> ADSharedIcons.Bluetooth
    AppIcon.BluetoothSearching -> ADSharedIcons.BluetoothSearching
    AppIcon.ChevronRight -> ADSharedIcons.ChevronRight
    AppIcon.ExpandLess -> ADSharedIcons.ExpandLess
    AppIcon.ExpandMore -> ADSharedIcons.ExpandMore
}
