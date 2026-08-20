package com.fersaiyan.cyanbridge.ui.adglasses

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color

/**
 * Static 7x7 dot-matrix icon family for AD Glasses product chrome.
 *
 * These glyphs deliberately do not animate. Motion belongs to real camera, audio and AI states;
 * navigation and settings icons should remain calm and immediately recognizable.
 */
internal enum class ADMatrixGlyph(val pattern: List<String>, val accentCell: Pair<Int, Int>? = null) {
    HOME(
        listOf(
            "0001000",
            "0011100",
            "0111110",
            "1101011",
            "1101011",
            "1100011",
            "1111111",
        ),
    ),
    AI(
        listOf(
            "0001000",
            "0101010",
            "0011100",
            "1111111",
            "0011100",
            "0101010",
            "0001000",
        ),
        3 to 3,
    ),
    LIBRARY(
        listOf(
            "1110111",
            "1010101",
            "1110111",
            "0000000",
            "1111111",
            "1000001",
            "1111111",
        ),
    ),
    BACK(
        listOf(
            "0001000",
            "0010000",
            "0100000",
            "1111110",
            "0100000",
            "0010000",
            "0001000",
        ),
    ),
    NEXT(
        listOf(
            "0010000",
            "0001000",
            "0000100",
            "1111110",
            "0000100",
            "0001000",
            "0010000",
        ),
    ),
    SETTINGS(
        listOf(
            "0101010",
            "0011100",
            "1111111",
            "0110110",
            "1111111",
            "0011100",
            "0101010",
        ),
    ),
    LENS(
        listOf(
            "0011100",
            "0100010",
            "1000001",
            "1011101",
            "1000001",
            "0100010",
            "0011100",
        ),
        3 to 3,
    ),
    ASK(
        listOf(
            "0111110",
            "1000001",
            "1001001",
            "0000010",
            "0000100",
            "0000000",
            "0000100",
        ),
        6 to 3,
    ),
    MIC(
        listOf(
            "0011100",
            "0100010",
            "0100010",
            "0100010",
            "0011100",
            "0010000",
            "0111110",
        ),
        0 to 3,
    ),
    WEB(
        listOf(
            "0011100",
            "0101010",
            "1010101",
            "1111111",
            "1010101",
            "0101010",
            "0011100",
        ),
    ),
    SEARCH(
        listOf(
            "0011100",
            "0100010",
            "1000001",
            "1000001",
            "0100010",
            "0011110",
            "0000011",
        ),
    ),
    SYNC(
        listOf(
            "0011100",
            "0100010",
            "1000000",
            "1001110",
            "0000001",
            "0100010",
            "0011100",
        ),
        3 to 3,
    ),
    PRIVACY(
        listOf(
            "0011100",
            "0100010",
            "0100010",
            "1111111",
            "1001001",
            "1001001",
            "1111111",
        ),
    ),
    STORAGE(
        listOf(
            "0111110",
            "1000001",
            "1011101",
            "1000001",
            "1011101",
            "1000001",
            "0111110",
        ),
    ),
    LANGUAGE(
        listOf(
            "1000001",
            "0100010",
            "0010100",
            "1111111",
            "0010100",
            "0100010",
            "1000001",
        ),
    ),
    PERMISSIONS(
        listOf(
            "0011100",
            "0100010",
            "0100010",
            "0011100",
            "0111110",
            "1000001",
            "1000001",
        ),
    ),
    FIRMWARE(
        listOf(
            "0101010",
            "1111111",
            "1000001",
            "1011101",
            "1010101",
            "1000001",
            "1111111",
        ),
        3 to 3,
    ),
    INFO(
        listOf(
            "0011100",
            "0100010",
            "1000001",
            "0001000",
            "0001000",
            "0000000",
            "0001000",
        ),
    ),
    LOCAL(
        listOf(
            "1111111",
            "1000001",
            "1011101",
            "1010101",
            "1011101",
            "1000001",
            "1111111",
        ),
        3 to 3,
    ),
    RELAY(
        listOf(
            "0011100",
            "0100010",
            "1000001",
            "0001000",
            "0010100",
            "0100010",
            "1000001",
        ),
        3 to 3,
    ),
    AUTOMATION(
        listOf(
            "1000001",
            "0100010",
            "0010100",
            "1111111",
            "0010100",
            "0100010",
            "1000001",
        ),
        3 to 3,
    ),
    TIMELINE(
        listOf(
            "0010000",
            "0011100",
            "0010000",
            "0011110",
            "0010000",
            "0011100",
            "0010000",
        ),
        3 to 3,
    ),
    DIARY(
        listOf(
            "1111110",
            "1000010",
            "1011010",
            "1000010",
            "1011110",
            "1000010",
            "1111110",
        ),
    ),
    PHOTO(
        listOf(
            "0000000",
            "0011100",
            "0111110",
            "1101011",
            "1101011",
            "0111110",
            "0000000",
        ),
        4 to 3,
    ),
    VIDEO(
        listOf(
            "0000000",
            "1111000",
            "1001100",
            "1011110",
            "1001100",
            "1111000",
            "0000000",
        ),
        3 to 3,
    ),
    AUDIO(
        listOf(
            "0010000",
            "1010101",
            "1010101",
            "1111111",
            "1010101",
            "1010101",
            "0010000",
        ),
        3 to 3,
    ),
    CHECK(
        listOf(
            "0000000",
            "0000001",
            "0000010",
            "1000100",
            "0101000",
            "0010000",
            "0000000",
        ),
    ),
    CLOSE(
        listOf(
            "1000001",
            "0100010",
            "0010100",
            "0001000",
            "0010100",
            "0100010",
            "1000001",
        ),
    ),
    ADD(
        listOf(
            "0001000",
            "0001000",
            "0001000",
            "1111111",
            "0001000",
            "0001000",
            "0001000",
        ),
    ),
    SEND(
        listOf(
            "1000000",
            "1100000",
            "1010000",
            "1001000",
            "1010000",
            "1100000",
            "1000000",
        ),
        3 to 3,
    ),
}

@Composable
internal fun ADMatrixGlyphIcon(
    glyph: ADMatrixGlyph,
    tint: Color,
    modifier: Modifier = Modifier,
    accent: Color? = null,
) {
    Canvas(modifier = modifier) {
        val grid = 7
        val cell = size.minDimension / 8.35f
        val dot = cell * 0.58f
        val matrixSize = cell * grid
        val left = (size.width - matrixSize) / 2f + (cell - dot) / 2f
        val top = (size.height - matrixSize) / 2f + (cell - dot) / 2f

        glyph.pattern.forEachIndexed { row, line ->
            line.forEachIndexed { column, bit ->
                if (bit == '1') {
                    val isAccent = accent != null && glyph.accentCell == (row to column)
                    drawRoundRect(
                        color = if (isAccent) accent else tint,
                        topLeft = Offset(left + column * cell, top + row * cell),
                        size = Size(dot, dot),
                        cornerRadius = CornerRadius(dot * 0.30f, dot * 0.30f),
                    )
                }
            }
        }
    }
}
