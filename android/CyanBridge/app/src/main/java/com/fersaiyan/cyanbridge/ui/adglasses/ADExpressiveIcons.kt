package com.fersaiyan.cyanbridge.ui.adglasses

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * AD's mixed icon family.
 *
 * Most product actions use the restrained line treatment. The handful of matrix glyphs the
 * product already established as signature controls are preserved exactly instead of being
 * flattened into stock Material icons or forcing the matrix language onto every action.
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
    BACK,
    NEXT,
}

@Composable
internal fun ADGlyphIcon(
    glyph: ADGlyph,
    tint: Color,
    modifier: Modifier = Modifier,
    accent: Color? = null,
) {
    val pulse by rememberInfiniteTransition(label = "ad-selected-glyph-pulse").animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 820),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "ad-selected-glyph-accent",
    )

    Canvas(modifier = modifier) {
        glyph.selectedMatrixPattern?.let { pattern ->
            drawSelectedMatrixGlyph(
                pattern = pattern,
                tint = tint,
                accent = accent,
                accentCell = glyph.selectedMatrixAccentCell,
                pulse = pulse,
            )
            return@Canvas
        }

        val u = size.minDimension / 24f
        val stroke = Stroke(
            width = 1.55f * u,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )
        when (glyph) {
            ADGlyph.HOME -> drawHome(tint, u, stroke)
            ADGlyph.PROMPT -> drawPrompt(tint, u, stroke)
            ADGlyph.AI -> drawAi(tint, accent, u, stroke)
            ADGlyph.LIBRARY -> drawLibrary(tint, u, stroke)
            ADGlyph.ASK -> drawAsk(tint, accent, u, stroke)
            ADGlyph.PHOTO -> drawPhoto(tint, accent, u, stroke)
            ADGlyph.VIDEO -> drawVideo(tint, accent, u, stroke)
            ADGlyph.TRANSLATE -> drawTranslate(tint, u, stroke)
            ADGlyph.SOUNDBITES -> drawSoundbites(tint, accent, u, stroke)
            ADGlyph.AUDIO -> drawAudio(tint, accent, u, stroke)
            ADGlyph.PRIVACY -> drawPrivacy(tint, u, stroke)
            ADGlyph.STORAGE -> drawStorage(tint, u, stroke)
            ADGlyph.LANGUAGE -> drawLanguage(tint, u, stroke)
            ADGlyph.PERMISSIONS -> drawPermissions(tint, u, stroke)
            ADGlyph.DEVICE -> drawGlasses(tint, accent, u, stroke)
            ADGlyph.SYNC -> drawSync(tint, u, stroke)
            ADGlyph.FIRMWARE -> drawFirmware(tint, accent, u, stroke)
            ADGlyph.LENS -> drawLens(tint, accent, u, stroke)
            ADGlyph.BACK, ADGlyph.NEXT -> Unit
        }
    }
}

private val ADGlyph.selectedMatrixPattern: List<String>?
    get() = when (this) {
        ADGlyph.PROMPT -> listOf("0111110", "1000001", "1010101", "1000001", "0111110", "0010000", "0100000")
        ADGlyph.PRIVACY -> listOf("0011100", "0100010", "0100010", "1111111", "1001001", "1001001", "1111111")
        ADGlyph.PERMISSIONS -> listOf("0011100", "0100010", "0100010", "0011100", "0111110", "1000001", "1000001")
        ADGlyph.FIRMWARE -> listOf("0101010", "1111111", "1000001", "1011101", "1010101", "1000001", "1111111")
        ADGlyph.BACK -> listOf("0001000", "0010000", "0100000", "1111110", "0100000", "0010000", "0001000")
        ADGlyph.NEXT -> listOf("0010000", "0001000", "0000100", "1111110", "0000100", "0001000", "0010000")
        else -> null
    }

private val ADGlyph.selectedMatrixAccentCell: Pair<Int, Int>?
    get() = when (this) {
        ADGlyph.PROMPT -> 2 to 4
        ADGlyph.PRIVACY -> 4 to 3
        ADGlyph.PERMISSIONS -> 0 to 3
        ADGlyph.FIRMWARE -> 3 to 3
        ADGlyph.BACK -> 3 to 0
        ADGlyph.NEXT -> 3 to 5
        else -> null
    }

private fun DrawScope.drawSelectedMatrixGlyph(
    pattern: List<String>,
    tint: Color,
    accent: Color?,
    accentCell: Pair<Int, Int>?,
    pulse: Float,
) {
    val grid = 7
    val cell = size.minDimension / 8.4f
    val dot = cell * 0.56f
    val matrixSize = cell * grid
    val left = (size.width - matrixSize) / 2f + (cell - dot) / 2f
    val top = (size.height - matrixSize) / 2f + (cell - dot) / 2f

    pattern.forEachIndexed { row, line ->
        line.forEachIndexed { column, bit ->
            if (bit == '1') {
                val isAccent = accent != null &&
                    accentCell?.let { it.first == row && it.second == column } == true
                drawRoundRect(
                    color = if (isAccent) accent.copy(alpha = pulse) else tint,
                    topLeft = Offset(left + column * cell, top + row * cell),
                    size = Size(dot, dot),
                    cornerRadius = CornerRadius(dot * 0.28f, dot * 0.28f),
                )
            }
        }
    }
}

private fun DrawScope.drawHome(c: Color, u: Float, s: Stroke) {
    drawRoundRect(c, Offset(4.5f * u, 4.5f * u), Size(6f * u, 6f * u), CornerRadius(1.5f * u), style = s)
    drawRoundRect(c, Offset(13.5f * u, 4.5f * u), Size(6f * u, 6f * u), CornerRadius(1.5f * u), style = s)
    drawRoundRect(c, Offset(4.5f * u, 13.5f * u), Size(6f * u, 6f * u), CornerRadius(1.5f * u), style = s)
    drawRoundRect(c, Offset(13.5f * u, 13.5f * u), Size(6f * u, 6f * u), CornerRadius(1.5f * u), style = s)
}

private fun DrawScope.drawPrompt(c: Color, u: Float, s: Stroke) {
    drawRoundRect(c, Offset(3.5f * u, 5f * u), Size(17f * u, 12f * u), CornerRadius(3f * u), style = s)
    val tail = Path().apply {
        moveTo(7f * u, 17f * u)
        lineTo(5.3f * u, 20f * u)
        lineTo(10f * u, 17f * u)
    }
    drawPath(tail, c, style = s)
    listOf(8f, 12f, 16f).forEach { x -> drawCircle(c, .75f * u, Offset(x * u, 11f * u)) }
}

private fun DrawScope.drawAi(c: Color, accent: Color?, u: Float, s: Stroke) {
    drawCircle(c, 6.2f * u, Offset(12f * u, 12f * u), style = s)
    drawCircle(c, 1.35f * u, Offset(12f * u, 12f * u))
    listOf(
        Offset(12f, 5.8f), Offset(18.2f, 12f), Offset(12f, 18.2f), Offset(5.8f, 12f),
    ).forEach { p -> drawCircle(c, .8f * u, Offset(p.x * u, p.y * u)) }
    accent?.let { drawCircle(it, .8f * u, Offset(18.2f * u, 12f * u)) }
}

private fun DrawScope.drawLibrary(c: Color, u: Float, s: Stroke) {
    drawRoundRect(c, Offset(4f * u, 5f * u), Size(7f * u, 7f * u), CornerRadius(1.6f * u), style = s)
    drawRoundRect(c, Offset(13f * u, 5f * u), Size(7f * u, 7f * u), CornerRadius(1.6f * u), style = s)
    drawRoundRect(c, Offset(4f * u, 14f * u), Size(16f * u, 5f * u), CornerRadius(1.6f * u), style = s)
}

private fun DrawScope.drawAsk(c: Color, accent: Color?, u: Float, s: Stroke) {
    drawRoundRect(c, Offset(4f * u, 5f * u), Size(13f * u, 10f * u), CornerRadius(3f * u), style = s)
    val tail = Path().apply {
        moveTo(7f * u, 15f * u)
        lineTo(5.8f * u, 18f * u)
        lineTo(10f * u, 15f * u)
    }
    drawPath(tail, c, style = s)
    drawLine(c, Offset(19f * u, 5f * u), Offset(19f * u, 10f * u), s.width, StrokeCap.Round)
    drawLine(c, Offset(16.5f * u, 7.5f * u), Offset(21.5f * u, 7.5f * u), s.width, StrokeCap.Round)
    accent?.let { drawCircle(it, .8f * u, Offset(19f * u, 7.5f * u)) }
}

private fun DrawScope.drawPhoto(c: Color, accent: Color?, u: Float, s: Stroke) {
    drawRoundRect(c, Offset(3.5f * u, 6f * u), Size(17f * u, 12.5f * u), CornerRadius(2.4f * u), style = s)
    drawCircle(c, 3f * u, Offset(12f * u, 12.3f * u), style = s)
    drawLine(c, Offset(7f * u, 6f * u), Offset(9f * u, 4.5f * u), s.width, StrokeCap.Round)
    drawLine(c, Offset(9f * u, 4.5f * u), Offset(13f * u, 4.5f * u), s.width, StrokeCap.Round)
    accent?.let { drawCircle(it, .7f * u, Offset(17.3f * u, 9f * u)) }
}

private fun DrawScope.drawVideo(c: Color, accent: Color?, u: Float, s: Stroke) {
    drawRoundRect(c, Offset(3.5f * u, 6f * u), Size(12.5f * u, 12f * u), CornerRadius(2.6f * u), style = s)
    val head = Path().apply {
        moveTo(16f * u, 10f * u)
        lineTo(20.5f * u, 7.5f * u)
        lineTo(20.5f * u, 16.5f * u)
        lineTo(16f * u, 14f * u)
    }
    drawPath(head, c, style = s)
    accent?.let { drawCircle(it, .8f * u, Offset(7.2f * u, 9.5f * u)) }
}

private fun DrawScope.drawTranslate(c: Color, u: Float, s: Stroke) {
    drawLine(c, Offset(4f * u, 6f * u), Offset(12f * u, 6f * u), s.width, StrokeCap.Round)
    drawLine(c, Offset(8f * u, 4f * u), Offset(8f * u, 8f * u), s.width, StrokeCap.Round)
    drawLine(c, Offset(5.5f * u, 10f * u), Offset(10.5f * u, 10f * u), s.width, StrokeCap.Round)
    drawLine(c, Offset(7f * u, 6f * u), Offset(10f * u, 13f * u), s.width, StrokeCap.Round)
    drawLine(c, Offset(10f * u, 6f * u), Offset(6.5f * u, 13f * u), s.width, StrokeCap.Round)
    drawLine(c, Offset(14f * u, 19f * u), Offset(17f * u, 9f * u), s.width, StrokeCap.Round)
    drawLine(c, Offset(20f * u, 19f * u), Offset(17f * u, 9f * u), s.width, StrokeCap.Round)
    drawLine(c, Offset(15.3f * u, 15f * u), Offset(18.8f * u, 15f * u), s.width, StrokeCap.Round)
}

private fun DrawScope.drawSoundbites(c: Color, accent: Color?, u: Float, s: Stroke) {
    val heights = listOf(5f, 10f, 15f, 8f, 12f, 6f)
    heights.forEachIndexed { index, height ->
        val x = (5f + index * 2.8f) * u
        val color = if (index == 2 && accent != null) accent else c
        drawLine(color, Offset(x, (12f - height / 2f) * u), Offset(x, (12f + height / 2f) * u), s.width, StrokeCap.Round)
    }
}

private fun DrawScope.drawAudio(c: Color, accent: Color?, u: Float, s: Stroke) {
    drawRoundRect(c, Offset(8f * u, 3.5f * u), Size(8f * u, 11f * u), CornerRadius(4f * u), style = s)
    val cup = Path().apply {
        moveTo(5.5f * u, 11f * u)
        cubicTo(5.5f * u, 18f * u, 18.5f * u, 18f * u, 18.5f * u, 11f * u)
    }
    drawPath(cup, c, style = s)
    drawLine(c, Offset(12f * u, 18f * u), Offset(12f * u, 20.5f * u), s.width, StrokeCap.Round)
    accent?.let { drawCircle(it, .75f * u, Offset(12f * u, 8f * u)) }
}

private fun DrawScope.drawPrivacy(c: Color, u: Float, s: Stroke) {
    drawRoundRect(c, Offset(5f * u, 10f * u), Size(14f * u, 10f * u), CornerRadius(2.2f * u), style = s)
    val lock = Path().apply {
        moveTo(8f * u, 10f * u)
        lineTo(8f * u, 7.5f * u)
        cubicTo(8f * u, 3.5f * u, 16f * u, 3.5f * u, 16f * u, 7.5f * u)
        lineTo(16f * u, 10f * u)
    }
    drawPath(lock, c, style = s)
    drawCircle(c, .8f * u, Offset(12f * u, 15f * u))
}

private fun DrawScope.drawStorage(c: Color, u: Float, s: Stroke) {
    repeat(3) { i ->
        val y = (4.5f + i * 5.5f) * u
        drawRoundRect(c, Offset(4f * u, y), Size(16f * u, 4f * u), CornerRadius(1.5f * u), style = s)
        drawCircle(c, .55f * u, Offset(7f * u, y + 2f * u))
    }
}

private fun DrawScope.drawLanguage(c: Color, u: Float, s: Stroke) {
    drawLine(c, Offset(4.5f * u, 18f * u), Offset(9f * u, 5f * u), s.width, StrokeCap.Round)
    drawLine(c, Offset(13.5f * u, 18f * u), Offset(9f * u, 5f * u), s.width, StrokeCap.Round)
    drawLine(c, Offset(6.4f * u, 13f * u), Offset(11.6f * u, 13f * u), s.width, StrokeCap.Round)
    drawRoundRect(c, Offset(14f * u, 6f * u), Size(6f * u, 12f * u), CornerRadius(2f * u), style = s)
    drawLine(c, Offset(15.5f * u, 10f * u), Offset(18.5f * u, 10f * u), s.width, StrokeCap.Round)
    drawLine(c, Offset(15.5f * u, 14f * u), Offset(18f * u, 14f * u), s.width, StrokeCap.Round)
}

private fun DrawScope.drawPermissions(c: Color, u: Float, s: Stroke) {
    drawCircle(c, 3f * u, Offset(12f * u, 8f * u), style = s)
    val shoulders = Path().apply {
        moveTo(5f * u, 20f * u)
        cubicTo(6f * u, 14f * u, 18f * u, 14f * u, 19f * u, 20f * u)
    }
    drawPath(shoulders, c, style = s)
    drawCircle(c, .7f * u, Offset(18.5f * u, 7f * u))
}

private fun DrawScope.drawGlasses(c: Color, accent: Color?, u: Float, s: Stroke) {
    drawRoundRect(c, Offset(2.5f * u, 8f * u), Size(8.3f * u, 7f * u), CornerRadius(2.2f * u), style = s)
    drawRoundRect(c, Offset(13.2f * u, 8f * u), Size(8.3f * u, 7f * u), CornerRadius(2.2f * u), style = s)
    val bridge = Path().apply {
        moveTo(10.8f * u, 10.6f * u)
        cubicTo(11.2f * u, 9f * u, 12.8f * u, 9f * u, 13.2f * u, 10.6f * u)
    }
    drawPath(bridge, c, style = s)
    drawLine(c, Offset(2.5f * u, 10f * u), Offset(.8f * u, 9.3f * u), s.width, StrokeCap.Round)
    drawLine(c, Offset(21.5f * u, 10f * u), Offset(23.2f * u, 9.3f * u), s.width, StrokeCap.Round)
    accent?.let { drawCircle(it, .7f * u, Offset(19.1f * u, 9.8f * u)) }
}

private fun DrawScope.drawSync(c: Color, u: Float, s: Stroke) {
    val top = Path().apply {
        moveTo(5f * u, 9f * u)
        cubicTo(7f * u, 4f * u, 15f * u, 3.5f * u, 19f * u, 8f * u)
        lineTo(19f * u, 5.5f * u)
        moveTo(19f * u, 8f * u)
        lineTo(16.5f * u, 8f * u)
    }
    val bottom = Path().apply {
        moveTo(19f * u, 15f * u)
        cubicTo(17f * u, 20f * u, 9f * u, 20.5f * u, 5f * u, 16f * u)
        lineTo(5f * u, 18.5f * u)
        moveTo(5f * u, 16f * u)
        lineTo(7.5f * u, 16f * u)
    }
    drawPath(top, c, style = s)
    drawPath(bottom, c, style = s)
}

private fun DrawScope.drawFirmware(c: Color, accent: Color?, u: Float, s: Stroke) {
    drawRoundRect(c, Offset(5f * u, 5f * u), Size(14f * u, 14f * u), CornerRadius(2.5f * u), style = s)
    repeat(3) { i ->
        val p = (8f + i * 4f) * u
        drawLine(c, Offset(p, 2.5f * u), Offset(p, 5f * u), s.width, StrokeCap.Round)
        drawLine(c, Offset(p, 19f * u), Offset(p, 21.5f * u), s.width, StrokeCap.Round)
    }
    drawCircle(accent ?: c, 1.2f * u, Offset(12f * u, 12f * u))
}

private fun DrawScope.drawLens(c: Color, accent: Color?, u: Float, s: Stroke) {
    drawCircle(c, 7.2f * u, Offset(12f * u, 12f * u), style = s)
    drawCircle(c, 3.5f * u, Offset(12f * u, 12f * u), style = s)
    accent?.let { drawCircle(it, .8f * u, Offset(17f * u, 7f * u)) }
}