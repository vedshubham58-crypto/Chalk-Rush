package com.vedshubham.chalkrush.game

/**
 * One level. Early maps are small with a few timid bots so they're easy to win.
 * Later maps get bigger, busier and braver. Each level is shaped like a country.
 */
data class GameMap(
    val index: Int,
    /** The country this level is shaped like (see [Arenas]). */
    val arena: Arena,
    val bots: Int,
    val minBoldness: Float,
    val maxBoldness: Float,
    /** Bots move at this fraction of normal speed. */
    val botSpeed: Float,
    /** Own this much of the country to win straight away. */
    val winPercent: Float,
) {
    val name: String get() = arena.name
    val size: Int get() = arena.size
}

object Maps {
    val all = listOf(
        GameMap(0, Arenas.byCode("IND"), 3, 0.55f, 0.75f, 0.85f, 20f),
        GameMap(1, Arenas.byCode("EGY"), 4, 0.6f, 0.8f, 0.88f, 20f),
        GameMap(2, Arenas.byCode("FRA"), 4, 0.65f, 0.9f, 0.9f, 22f),
        GameMap(3, Arenas.byCode("AUS"), 5, 0.7f, 0.95f, 0.92f, 22f),
        GameMap(4, Arenas.byCode("BRA"), 5, 0.75f, 1.0f, 0.94f, 25f),
        GameMap(5, Arenas.byCode("ZAF"), 6, 0.8f, 1.1f, 0.96f, 25f),
        GameMap(6, Arenas.byCode("SAU"), 6, 0.85f, 1.2f, 0.98f, 27f),
        GameMap(7, Arenas.byCode("DEU"), 7, 0.9f, 1.3f, 1f, 27f),
        GameMap(8, Arenas.byCode("CHN"), 7, 0.95f, 1.4f, 1f, 30f),
        GameMap(9, Arenas.byCode("USA"), 7, 1.0f, 1.5f, 1f, 30f),
    )

    fun byIndex(i: Int) = all[i.coerceIn(0, all.lastIndex)]
}
