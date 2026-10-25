package com.vedshubham.chalkrush.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary = Color(0xFF2E86C1),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3EAF8),
    onPrimaryContainer = Color(0xFF0E3A57),
    secondary = Color(0xFFC2185B),
    background = Color(0xFFF4F1EA),
    surface = Color(0xFFF4F1EA),
    surfaceVariant = Color(0xFFE6E1D8),
    onSurfaceVariant = Color(0xFF55524C),
    outline = Color(0xFF8A867F),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF7EC8F2),
    onPrimary = Color(0xFF06324D),
    primaryContainer = Color(0xFF1D4A68),
    onPrimaryContainer = Color(0xFFD3EAF8),
    secondary = Color(0xFFF48FB1),
    background = Color(0xFF1C1D20),
    surface = Color(0xFF1C1D20),
    surfaceVariant = Color(0xFF2C2D31),
    onSurfaceVariant = Color(0xFFC9C6C0),
    outline = Color(0xFF8F8C86),
)

@Composable
fun ChalkRushTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}

/** Chalk colours for each player (you are always the first). */
data class ChalkColor(val fill: Color, val edge: Color)

val PlayerColors = listOf(
    ChalkColor(Color(0xFF7EC8F2), Color(0xFF2E86C1)), // you: sky blue
    ChalkColor(Color(0xFFF48FB1), Color(0xFFC2185B)),
    ChalkColor(Color(0xFFFFD966), Color(0xFFD4A20F)),
    ChalkColor(Color(0xFF8ED69A), Color(0xFF3D9450)),
    ChalkColor(Color(0xFFFFB574), Color(0xFFE0771A)),
    ChalkColor(Color(0xFFC3AEF0), Color(0xFF6E4FC2)),
    ChalkColor(Color(0xFF7FDDE6), Color(0xFF16929E)),
    ChalkColor(Color(0xFFF08A8A), Color(0xFFC23B3B)),
    ChalkColor(Color(0xFFDDE68A), Color(0xFF97A02A)),
)

fun colorOf(id: Int) = PlayerColors[id % PlayerColors.size]

/** The sidewalk itself looks the same in light and dark mode. */
val Concrete = Color(0xFF9A9C9F)
val ConcreteJoint = Color(0xFF85878A)
val ConcreteSpeck = Color(0xFFADAFB2)
val OffBoard = Color(0xFF3A3C40)
