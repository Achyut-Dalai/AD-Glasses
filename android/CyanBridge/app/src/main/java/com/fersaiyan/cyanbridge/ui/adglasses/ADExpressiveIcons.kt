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

/**
 * AD's dot-matrix glyph family.
 *
 * Every product glyph is drawn from the same 7×7 matrix so the UI reads like one
 * instrument system instead of a mixture of stock Material icons and illustrations.
 * A supplied accent lights one matrix cell with a subtle pulse for live/selected states.
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
    SETTINGS,
    BACK,
    NEXT,
    CHECK,
    INFO,
}

@Composable
internal fun ADGlyphIcon(
    glyph: ADGlyph,
    tint: Color,
    modifier: Modifier = Modifier,
    accent: Color? = null,
) {
    val pulse by rememberInfiniteTransition(label = "ad-glyph-pulse").animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 820),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "ad-glyph-accent",
    )

    Canvas(modifier = modifier) {
        val pattern = glyph.pattern
        val grid = 7
        val cell = size.minDimension / 8.4f
        val dot = cell * 0.56f
        val matrixSize = cell * grid
        val left = (size.width - matrixSize) / 2f + (cell - dot) / 2f
        val top = (size.height - matrixSize) / 2f + (cell - dot) / 2f
        val accentCell = glyph.accentCell

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
}

private val ADGlyph.pattern: List<String>
    get() = when (this) {
        ADGlyph.HOME -> listOf(
            "1100110",
            "1100110",
            "0000000",
            "0000000",
            "1100110",
            "1100110",
            "0000000",
        )
        ADGlyph.PROMPT -> listOf(
            "0111110",
            "1000001",
            "1010101",
            "1000001",
            "0111110",
            "0010000",
            "0100000",
        )
        ADGlyph.AI -> listOf(
            "0010100",
            "0001000",
            "0101010",
            "0011100",
            "0101010",
            "0001000",
            "0010100",
        )
        ADGlyph.LIBRARY -> listOf(
            "1100110",
            "1100110",
            "0000000",
            "1111110",
            "1000010",
            "1111110",
            "0000000",
        )
        ADGlyph.ASK -> listOf(
            "0111100",
            "1000010",
            "1011010",
            "1000010",
            "0111101",
            "0010010",
            "0100000",
        )
        ADGlyph.PHOTO -> listOf(
            "0011000",
            "0111100",
            "1111110",
            "1100110",
            "1100110",
            "1111110",
            "0000000",
        )
        ADGlyph.VIDEO -> listOf(
            "1111000",
            "1001100",
            "1011010",
            "1011010",
            "1001100",
            "1111000",
            "0000000",
        )
        ADGlyph.TRANSLATE -> listOf(
            "0111001",
            "0010010",
            "0111010",
            "1010110",
            "1010110",
            "0001010",
            "0001001",
        )
        ADGlyph.SOUNDBITES -> listOf(
            "0010000",
            "1010100",
            "1010110",
            "1111111",
            "1010110",
            "1010100",
            "0010000",
        )
        ADGlyph.AUDIO -> listOf(
            "0011100",
            "0100010",
            "0101010",
            "0101010",
            "0011100",
            "0001000",
            "0011100",
        )
        ADGlyph.PRIVACY -> listOf(
            "0011100",
            "0100010",
            "0100010",
            "1111111",
            "1001001",
            "1001001",
            "1111111",
        )
        ADGlyph.STORAGE -> listOf(
            "1111110",
            "1000010",
            "1111110",
            "1000010",
            "1111110",
            "1000010",
            "1111110",
        )
        ADGlyph.LANGUAGE -> listOf(
            "0010001",
            "0101001",
            "1000101",
            "1111101",
            "1000101",
            "1000101",
            "0000111",
        )
        ADGlyph.PERMISSIONS -> listOf(
            "0011100",
            "0100010",
            "0100010",
            "0011100",
            "0111110",
            "1000001",
            "1000001",
        )
        ADGlyph.DEVICE -> listOf(
            "0000000",
            "1100011",
            "1010101",
            "1111111",
            "1010101",
            "1100011",
            "0000000",
        )
        ADGlyph.SYNC -> listOf(
            "0011110",
            "0100000",
            "1000001",
            "0000010",
            "0111100",
            "1000001",
            "0111110",
        )
        ADGlyph.FIRMWARE -> listOf(
            "0101010",
            "1111111",
            "1000001",
            "1011101",
            "1010101",
            "1000001",
            "1111111",
        )
        ADGlyph.LENS -> listOf(
            "0011100",
            "0100010",
            "1001001",
            "1011101",
            "1001001",
            "0100010",
            "0011100",
        )
        ADGlyph.SETTINGS -> listOf(
            "0101010",
            "0011100",
            "1100011",
            "1011101",
            "1100011",
            "0011100",
            "0101010",
        )
        ADGlyph.BACK -> listOf(
            "0001000",
            "0010000",
            "0100000",
            "1111110",
            "0100000",
            "0010000",
            "0001000",
        )
        ADGlyph.NEXT -> listOf(
            "0010000",
            "0001000",
            "0000100",
            "1111110",
            "0000100",
            "0001000",
            "0010000",
        )
        ADGlyph.CHECK -> listOf(
            "0000000",
            "0000001",
            "0000010",
            "1000100",
            "0101000",
            "0010000",
            "0000000",
        )
        ADGlyph.INFO -> listOf(
            "0011100",
            "0100010",
            "0001000",
            "0000000",
            "0001000",
            "0001000",
            "0011100",
        )
    }

private val ADGlyph.accentCell: Pair<Int, Int>?
    get() = when (this) {
        ADGlyph.HOME -> 4 to 4
        ADGlyph.PROMPT -> 2 to 4
        ADGlyph.AI -> 3 to 3
        ADGlyph.LIBRARY -> 4 to 5
        ADGlyph.ASK -> 4 to 6
        ADGlyph.PHOTO -> 3 to 3
        ADGlyph.VIDEO -> 2 to 5
        ADGlyph.TRANSLATE -> 6 to 6
        ADGlyph.SOUNDBITES -> 3 to 6
        ADGlyph.AUDIO -> 2 to 3
        ADGlyph.PRIVACY -> 4 to 3
        ADGlyph.STORAGE -> 3 to 1
        ADGlyph.LANGUAGE -> 0 to 6
        ADGlyph.PERMISSIONS -> 0 to 3
        ADGlyph.DEVICE -> 3 to 3
        ADGlyph.SYNC -> 0 to 4
        ADGlyph.FIRMWARE -> 3 to 3
        ADGlyph.LENS -> 3 to 3
        ADGlyph.SETTINGS -> 3 to 3
        ADGlyph.BACK -> 3 to 0
        ADGlyph.NEXT -> 3 to 5
        ADGlyph.CHECK -> 3 to 0
        ADGlyph.INFO -> 2 to 3
    }
