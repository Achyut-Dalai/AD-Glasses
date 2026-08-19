package com.fersaiyan.cyanbridge.ui.adglasses

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Compact AD-specific glyph language.
 *
 * The family deliberately uses the same rounded 2px-ish stroke, simple geometry and
 * monochrome treatment everywhere. It keeps the app expressive without assigning a
 * different accent colour to every feature or leaning on a mixture of unrelated icon sets.
 */
internal enum class ADGlyph {
    HOME,
    PROMPT,
    AI,
    LIBRARY,
    ASK,
    PHOTO,
    VIDEO,
    TRANSLATE,
    SOUNDBITES,
    AUDIO,
    PRIVACY,
    STORAGE,
    LANGUAGE,
    PERMISSIONS,
    DEVICE,
    SYNC,
    FIRMWARE,
    LENS,
}

@Composable
internal fun ADGlyphIcon(
    glyph: ADGlyph,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val u = size.minDimension / 24f
        val stroke = Stroke(
            width = 1.9f * u,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )
        when (glyph) {
            ADGlyph.HOME -> drawHome(tint, u, stroke)
            ADGlyph.PROMPT -> drawPrompt(tint, u, stroke)
            ADGlyph.AI -> drawAi(tint, u, stroke)
            ADGlyph.LIBRARY -> drawLibrary(tint, u, stroke)
            ADGlyph.ASK -> drawAsk(tint, u, stroke)
            ADGlyph.PHOTO -> drawPhoto(tint, u, stroke)
            ADGlyph.VIDEO -> drawVideo(tint, u, stroke)
            ADGlyph.TRANSLATE -> drawTranslate(tint, u, stroke)
            ADGlyph.SOUNDBITES -> drawSoundbites(tint, u, stroke)
            ADGlyph.AUDIO -> drawAudio(tint, u, stroke)
            ADGlyph.PRIVACY -> drawPrivacy(tint, u, stroke)
            ADGlyph.STORAGE -> drawStorage(tint, u, stroke)
            ADGlyph.LANGUAGE -> drawLanguage(tint, u, stroke)
            ADGlyph.PERMISSIONS -> drawPermissions(tint, u, stroke)
            ADGlyph.DEVICE -> drawDevice(tint, u, stroke)
            ADGlyph.SYNC -> drawSync(tint, u, stroke)
            ADGlyph.FIRMWARE -> drawFirmware(tint, u, stroke)
            ADGlyph.LENS -> drawLens(tint, u, stroke)
        }
    }
}

private fun DrawScope.drawHome(color: Color, u: Float, stroke: Stroke) {
    val roof = Path().apply {
        moveTo(4.5f * u, 11f * u)
        lineTo(12f * u, 4.8f * u)
        lineTo(19.5f * u, 11f * u)
    }
    drawPath(roof, color, style = stroke)
    drawRoundRect(
        color = color,
        topLeft = Offset(6.4f * u, 10.4f * u),
        size = Size(11.2f * u, 9f * u),
        cornerRadius = CornerRadius(2.7f * u),
        style = stroke,
    )
    drawLine(color, Offset(12f * u, 14.2f * u), Offset(12f * u, 19.1f * u), strokeWidth = stroke.width, cap = StrokeCap.Round)
}

private fun DrawScope.drawPrompt(color: Color, u: Float, stroke: Stroke) {
    drawRoundRect(
        color,
        Offset(3.7f * u, 5.1f * u),
        Size(16.6f * u, 11.7f * u),
        CornerRadius(4.2f * u),
        style = stroke,
    )
    drawLine(color, Offset(8f * u, 9.2f * u), Offset(16f * u, 9.2f * u), stroke.width, StrokeCap.Round)
    drawLine(color, Offset(8f * u, 12.7f * u), Offset(13.6f * u, 12.7f * u), stroke.width, StrokeCap.Round)
    drawLine(color, Offset(7.8f * u, 16.4f * u), Offset(6.2f * u, 19.3f * u), stroke.width, StrokeCap.Round)
}

private fun DrawScope.drawAi(color: Color, u: Float, stroke: Stroke) {
    fun sparkle(center: Offset, radius: Float) {
        drawLine(color, center.copy(y = center.y - radius), center.copy(y = center.y + radius), stroke.width, StrokeCap.Round)
        drawLine(color, center.copy(x = center.x - radius), center.copy(x = center.x + radius), stroke.width, StrokeCap.Round)
        drawLine(color, Offset(center.x - radius * .65f, center.y - radius * .65f), Offset(center.x + radius * .65f, center.y + radius * .65f), stroke.width * .78f, StrokeCap.Round)
        drawLine(color, Offset(center.x + radius * .65f, center.y - radius * .65f), Offset(center.x - radius * .65f, center.y + radius * .65f), stroke.width * .78f, StrokeCap.Round)
    }
    sparkle(Offset(11.2f * u, 11.2f * u), 5f * u)
    drawCircle(color, radius = 1.35f * u, center = Offset(18.2f * u, 6f * u))
    drawCircle(color, radius = .9f * u, center = Offset(18.2f * u, 17.8f * u))
}

private fun DrawScope.drawLibrary(color: Color, u: Float, stroke: Stroke) {
    drawRoundRect(color, Offset(4.5f * u, 6f * u), Size(12f * u, 12f * u), CornerRadius(3.1f * u), style = stroke)
    drawRoundRect(color.copy(alpha = .65f), Offset(8f * u, 3.7f * u), Size(11.3f * u, 11.3f * u), CornerRadius(3f * u), style = stroke)
    drawCircle(color, 1.35f * u, Offset(9f * u, 10.1f * u))
    val mountain = Path().apply {
        moveTo(7f * u, 15f * u)
        lineTo(10.2f * u, 12.1f * u)
        lineTo(12.2f * u, 13.7f * u)
        lineTo(14.2f * u, 11.8f * u)
    }
    drawPath(mountain, color, style = stroke)
}

private fun DrawScope.drawAsk(color: Color, u: Float, stroke: Stroke) {
    drawRoundRect(color, Offset(8.2f * u, 3.8f * u), Size(7.6f * u, 11.2f * u), CornerRadius(3.8f * u), style = stroke)
    drawLine(color, Offset(5.8f * u, 11.7f * u), Offset(5.8f * u, 12.1f * u), stroke.width, StrokeCap.Round)
    val arcPath = Path().apply {
        moveTo(6.2f * u, 12f * u)
        cubicTo(6.6f * u, 16.8f * u, 17.4f * u, 16.8f * u, 17.8f * u, 12f * u)
    }
    drawPath(arcPath, color, style = stroke)
    drawLine(color, Offset(12f * u, 17f * u), Offset(12f * u, 20f * u), stroke.width, StrokeCap.Round)
    drawLine(color, Offset(9f * u, 20f * u), Offset(15f * u, 20f * u), stroke.width, StrokeCap.Round)
}

private fun DrawScope.drawPhoto(color: Color, u: Float, stroke: Stroke) {
    drawRoundRect(color, Offset(3.5f * u, 6.5f * u), Size(17f * u, 12.5f * u), CornerRadius(3.2f * u), style = stroke)
    drawRoundRect(color, Offset(7.1f * u, 4.4f * u), Size(5.6f * u, 3f * u), CornerRadius(1.4f * u), style = stroke)
    drawCircle(color, radius = 3.2f * u, center = Offset(12f * u, 12.7f * u), style = stroke)
    drawCircle(color, radius = 1f * u, center = Offset(17.2f * u, 9f * u))
}

private fun DrawScope.drawVideo(color: Color, u: Float, stroke: Stroke) {
    drawRoundRect(color, Offset(3.8f * u, 6f * u), Size(12.5f * u, 12f * u), CornerRadius(3.2f * u), style = stroke)
    val lens = Path().apply {
        moveTo(16.5f * u, 10f * u)
        lineTo(20.4f * u, 7.8f * u)
        lineTo(20.4f * u, 16.2f * u)
        lineTo(16.5f * u, 14f * u)
    }
    drawPath(lens, color, style = stroke)
    drawCircle(color, 1f * u, Offset(8f * u, 10f * u))
}

private fun DrawScope.drawTranslate(color: Color, u: Float, stroke: Stroke) {
    drawLine(color, Offset(4.4f * u, 6.1f * u), Offset(12.4f * u, 6.1f * u), stroke.width, StrokeCap.Round)
    drawLine(color, Offset(8.4f * u, 4.3f * u), Offset(8.4f * u, 7.9f * u), stroke.width, StrokeCap.Round)
    drawLine(color, Offset(5.7f * u, 9f * u), Offset(10.7f * u, 9f * u), stroke.width, StrokeCap.Round)
    drawLine(color, Offset(7f * u, 6.1f * u), Offset(10f * u, 12.1f * u), stroke.width, StrokeCap.Round)
    drawLine(color, Offset(10f * u, 6.1f * u), Offset(6.3f * u, 12.1f * u), stroke.width, StrokeCap.Round)
    drawLine(color, Offset(13.5f * u, 18.5f * u), Offset(16.8f * u, 9.4f * u), stroke.width, StrokeCap.Round)
    drawLine(color, Offset(20.3f * u, 18.5f * u), Offset(16.8f * u, 9.4f * u), stroke.width, StrokeCap.Round)
    drawLine(color, Offset(14.8f * u, 15.3f * u), Offset(19f * u, 15.3f * u), stroke.width, StrokeCap.Round)
}

private fun DrawScope.drawSoundbites(color: Color, u: Float, stroke: Stroke) {
    val heights = listOf(7f, 13f, 18f, 11f, 16f, 8f)
    val xs = listOf(5f, 7.8f, 10.6f, 13.4f, 16.2f, 19f)
    xs.zip(heights).forEach { (x, height) ->
        drawLine(color, Offset(x * u, (12f - height / 2f) * u), Offset(x * u, (12f + height / 2f) * u), stroke.width, StrokeCap.Round)
    }
}

private fun DrawScope.drawAudio(color: Color, u: Float, stroke: Stroke) {
    drawRoundRect(color, Offset(8.5f * u, 3.7f * u), Size(7f * u, 11.5f * u), CornerRadius(3.5f * u), style = stroke)
    val cup = Path().apply {
        moveTo(5.5f * u, 11.5f * u)
        cubicTo(5.5f * u, 18f * u, 18.5f * u, 18f * u, 18.5f * u, 11.5f * u)
    }
    drawPath(cup, color, style = stroke)
    drawLine(color, Offset(12f * u, 18f * u), Offset(12f * u, 20.3f * u), stroke.width, StrokeCap.Round)
}

private fun DrawScope.drawPrivacy(color: Color, u: Float, stroke: Stroke) {
    drawRoundRect(color, Offset(5.2f * u, 10f * u), Size(13.6f * u, 10f * u), CornerRadius(3f * u), style = stroke)
    val shackle = Path().apply {
        moveTo(8.2f * u, 10f * u)
        lineTo(8.2f * u, 7.6f * u)
        cubicTo(8.2f * u, 3.3f * u, 15.8f * u, 3.3f * u, 15.8f * u, 7.6f * u)
        lineTo(15.8f * u, 10f * u)
    }
    drawPath(shackle, color, style = stroke)
    drawCircle(color, 1.2f * u, Offset(12f * u, 14.2f * u))
    drawLine(color, Offset(12f * u, 15.4f * u), Offset(12f * u, 17.4f * u), stroke.width, StrokeCap.Round)
}

private fun DrawScope.drawStorage(color: Color, u: Float, stroke: Stroke) {
    repeat(3) { index ->
        val y = (4.2f + index * 5.4f) * u
        drawRoundRect(color, Offset(4.2f * u, y), Size(15.6f * u, 4.2f * u), CornerRadius(2f * u), style = stroke)
        drawCircle(color, .75f * u, Offset(7f * u, y + 2.1f * u))
        drawLine(color, Offset(10f * u, y + 2.1f * u), Offset(16.8f * u, y + 2.1f * u), stroke.width * .7f, StrokeCap.Round)
    }
}

private fun DrawScope.drawLanguage(color: Color, u: Float, stroke: Stroke) {
    drawLine(color, Offset(4.5f * u, 18.5f * u), Offset(9f * u, 5.5f * u), stroke.width, StrokeCap.Round)
    drawLine(color, Offset(13.5f * u, 18.5f * u), Offset(9f * u, 5.5f * u), stroke.width, StrokeCap.Round)
    drawLine(color, Offset(6.4f * u, 13.1f * u), Offset(11.6f * u, 13.1f * u), stroke.width, StrokeCap.Round)
    drawLine(color, Offset(15.2f * u, 7f * u), Offset(20f * u, 7f * u), stroke.width, StrokeCap.Round)
    drawLine(color, Offset(17.6f * u, 5.1f * u), Offset(17.6f * u, 9f * u), stroke.width, StrokeCap.Round)
    val path = Path().apply {
        moveTo(14.8f * u, 11f * u)
        cubicTo(15.8f * u, 15.8f * u, 19.7f * u, 17.5f * u, 21f * u, 17.6f * u)
        moveTo(20.6f * u, 11f * u)
        cubicTo(19.4f * u, 15.1f * u, 16.5f * u, 17f * u, 14.5f * u, 17.5f * u)
    }
    drawPath(path, color, style = stroke)
}

private fun DrawScope.drawPermissions(color: Color, u: Float, stroke: Stroke) {
    val origins = listOf(Offset(4.7f * u, 4.7f * u), Offset(13.2f * u, 4.7f * u), Offset(4.7f * u, 13.2f * u), Offset(13.2f * u, 13.2f * u))
    origins.forEachIndexed { index, origin ->
        drawRoundRect(
            color = if (index == 0 || index == 3) color else color.copy(alpha = .55f),
            topLeft = origin,
            size = Size(6.1f * u, 6.1f * u),
            cornerRadius = CornerRadius(2f * u),
            style = stroke,
        )
    }
}

private fun DrawScope.drawDevice(color: Color, u: Float, stroke: Stroke) {
    drawRoundRect(color, Offset(3.8f * u, 8.2f * u), Size(6.4f * u, 6.4f * u), CornerRadius(3.2f * u), style = stroke)
    drawRoundRect(color, Offset(13.8f * u, 8.2f * u), Size(6.4f * u, 6.4f * u), CornerRadius(3.2f * u), style = stroke)
    drawLine(color, Offset(10.2f * u, 11.4f * u), Offset(13.8f * u, 11.4f * u), stroke.width, StrokeCap.Round)
    drawLine(color, Offset(4.6f * u, 8.8f * u), Offset(2.8f * u, 7.3f * u), stroke.width, StrokeCap.Round)
    drawLine(color, Offset(19.4f * u, 8.8f * u), Offset(21.2f * u, 7.3f * u), stroke.width, StrokeCap.Round)
}

private fun DrawScope.drawSync(color: Color, u: Float, stroke: Stroke) {
    val upper = Path().apply {
        moveTo(5.2f * u, 9f * u)
        cubicTo(7.4f * u, 4.7f * u, 14.5f * u, 4.2f * u, 17.8f * u, 8f * u)
        lineTo(19.5f * u, 6.4f * u)
        moveTo(17.8f * u, 8f * u)
        lineTo(19.8f * u, 9.5f * u)
    }
    val lower = Path().apply {
        moveTo(18.8f * u, 15f * u)
        cubicTo(16.6f * u, 19.3f * u, 9.5f * u, 19.8f * u, 6.2f * u, 16f * u)
        lineTo(4.5f * u, 17.6f * u)
        moveTo(6.2f * u, 16f * u)
        lineTo(4.2f * u, 14.5f * u)
    }
    drawPath(upper, color, style = stroke)
    drawPath(lower, color, style = stroke)
}

private fun DrawScope.drawFirmware(color: Color, u: Float, stroke: Stroke) {
    drawRoundRect(color, Offset(5f * u, 5f * u), Size(14f * u, 14f * u), CornerRadius(3.4f * u), style = stroke)
    drawLine(color, Offset(12f * u, 8f * u), Offset(12f * u, 15.5f * u), stroke.width, StrokeCap.Round)
    val arrow = Path().apply {
        moveTo(9.4f * u, 13f * u)
        lineTo(12f * u, 15.7f * u)
        lineTo(14.6f * u, 13f * u)
    }
    drawPath(arrow, color, style = stroke)
    listOf(7.6f, 12f, 16.4f).forEach { x ->
        drawLine(color, Offset(x * u, 2.8f * u), Offset(x * u, 5f * u), stroke.width * .75f, StrokeCap.Round)
        drawLine(color, Offset(x * u, 19f * u), Offset(x * u, 21.2f * u), stroke.width * .75f, StrokeCap.Round)
    }
}

private fun DrawScope.drawLens(color: Color, u: Float, stroke: Stroke) {
    val eye = Path().apply {
        moveTo(3.5f * u, 12f * u)
        cubicTo(7f * u, 6.2f * u, 17f * u, 6.2f * u, 20.5f * u, 12f * u)
        cubicTo(17f * u, 17.8f * u, 7f * u, 17.8f * u, 3.5f * u, 12f * u)
    }
    drawPath(eye, color, style = stroke)
    drawCircle(color, radius = 3f * u, center = Offset(12f * u, 12f * u), style = stroke)
    drawCircle(color, radius = .9f * u, center = Offset(12f * u, 12f * u))
}
