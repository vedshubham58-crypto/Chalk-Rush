package com.vedshubham.chalkrush.game

/**
 * One level. Early maps are small with a few timid bots so they're easy to win.
 * Later maps get bigger, busier and braver.
 */
data class GameMap(
    val index: Int,
    val name: String,
    val size: Int,
    val bots: Int,
    val minBoldness: Float,
    val maxBoldness: Float,
    /** Bots move at this fraction of normal speed. */
    val botSpeed: Float,
    /** Own this much of the board to win straight away. */
    val winPercent: Float,
)

object Maps {
    val all = listOf(
        GameMap(0, "Neon Alley", 64, 3, 0.55f, 0.75f, 0.85f, 20f),
        GameMap(1, "Grid Garage", 68, 4, 0.6f, 0.8f, 0.88f, 20f),
        GameMap(2, "Synth Bridge", 72, 4, 0.65f, 0.9f, 0.9f, 22f),
        GameMap(3, "Pixel Plaza", 76, 5, 0.7f, 0.95f, 0.92f, 22f),
        GameMap(4, "Laser Lanes", 80, 5, 0.75f, 1.0f, 0.94f, 25f),
        GameMap(5, "Night Market", 84, 6, 0.8f, 1.1f, 0.96f, 25f),
        GameMap(6, "Ice Circuit", 88, 6, 0.85f, 1.2f, 0.98f, 27f),
        GameMap(7, "Volt Desert", 92, 7, 0.9f, 1.3f, 1f, 27f),
        GameMap(8, "Starlight Tower", 96, 7, 0.95f, 1.4f, 1f, 30f),
        GameMap(9, "The Core", 100, 7, 1.0f, 1.5f, 1f, 30f),
    )

    fun byIndex(i: Int) = all[i.coerceIn(0, all.lastIndex)]
}
