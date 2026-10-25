package com.vedshubham.chalkrush.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// The game is always dark: neon needs a night sky.
private val Neon = darkColorScheme(
    primary = Color(0xFF00E5FF),
    onPrimary = Color(0xFF00222A),
    secondary = Color(0xFFFF2BD6),
    background = Color(0xFF090C22),
    surface = Color(0xFF090C22),
    surfaceVariant = Color(0xFF161B3D),
    onSurfaceVariant = Color(0xFFB9C0E8),
    outline = Color(0xFF5A6399),
)

@Composable
fun ChalkRushTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Neon, content = content)
}

/** A rider's neon colour and a darker shade of it. */
data class ChalkColor(val fill: Color, val edge: Color)

/** One colour per character, by character id. */
val PlayerColors = listOf(
    ChalkColor(Color(0xFF00E5FF), Color(0xFF00809A)), // Sparky: cyan
    ChalkColor(Color(0xFFFF4FD8), Color(0xFFA3127F)), // Pip: pink
    ChalkColor(Color(0xFFFFF04F), Color(0xFFAA9B00)), // Zippy: yellow
    ChalkColor(Color(0xFF4FFF7A), Color(0xFF13A33A)), // Rocky: green
    ChalkColor(Color(0xFFFF8A2B), Color(0xFFB34A00)), // Blaze: orange
    ChalkColor(Color(0xFFB57CFF), Color(0xFF6A2DC2)), // Luna: violet
    ChalkColor(Color(0xFF9BF6FF), Color(0xFF2A9FB0)), // Frost: ice
    ChalkColor(Color(0xFFFF4F5E), Color(0xFFB3122A)), // King Kilowatt: red
)

fun colorOf(characterId: Int) = PlayerColors[characterId % PlayerColors.size]

/** The look of each map: night colour, grid lines and the glowing wall. */
data class MapTheme(val night: Color, val grid: Color, val accent: Color)

val MapThemes = listOf(
    MapTheme(Color(0xFF0B0F2A), Color(0xFF1C2766), Color(0xFF00E5FF)), // Neon Alley
    MapTheme(Color(0xFF120A24), Color(0xFF3A1F6B), Color(0xFFFF2BD6)), // Grid Garage
    MapTheme(Color(0xFF1A0B2E), Color(0xFF552A80), Color(0xFFFF8A00)), // Synth Bridge
    MapTheme(Color(0xFF051A17), Color(0xFF0F5446), Color(0xFF39FF88)), // Pixel Plaza
    MapTheme(Color(0xFF1C060E), Color(0xFF64142D), Color(0xFFFF3B5C)), // Laser Lanes
    MapTheme(Color(0xFF181204), Color(0xFF5E4310), Color(0xFFFFC400)), // Night Market
    MapTheme(Color(0xFF051626), Color(0xFF1A5583), Color(0xFF9BE7FF)), // Ice Circuit
    MapTheme(Color(0xFF200F04), Color(0xFF6E3410), Color(0xFFFFB000)), // Volt Desert
    MapTheme(Color(0xFF09091C), Color(0xFF36367A), Color(0xFFB388FF)), // Starlight Tower
    MapTheme(Color(0xFF050003), Color(0xFF4A0A12), Color(0xFFFF5F1F)), // The Core
)

fun themeOf(mapIndex: Int) = MapThemes[mapIndex.coerceIn(0, MapThemes.lastIndex)]

val OffBoard = Color(0xFF020309)
