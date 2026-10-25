package com.vedshubham.chalkrush.game

/** A special ability that makes each character play a little differently. */
enum class Perk { NONE, SHIELD_START, FASTER, BIG_START, LONG_POWERUPS, BIG_SPLASH, GRIND_MASTER, MORE_COINS }

data class Character(
    val id: Int,
    val name: String,
    val tagline: String,
    val perk: Perk,
    val perkText: String,
    /** Coins needed to unlock. 0 = free. */
    val cost: Int,
)

object Characters {
    val all = listOf(
        Character(0, "Sparky", "Born on the grid", Perk.NONE, "All-rounder", 0),
        Character(1, "Pip", "Small but brave", Perk.SHIELD_START, "Starts every life with a longer shield", 150),
        Character(2, "Zippy", "Never walks anywhere", Perk.FASTER, "10% faster", 300),
        Character(3, "Rocky", "Likes a big garage", Perk.BIG_START, "Starts with more land", 450),
        Character(4, "Blaze", "Hot-headed and quick", Perk.LONG_POWERUPS, "Power-ups last 50% longer", 600),
        Character(5, "Luna", "Rides by moonlight", Perk.BIG_SPLASH, "Energy bursts are bigger", 800),
        Character(6, "Frost", "Cool on the corners", Perk.GRIND_MASTER, "Grinding along trails is extra fast", 1000),
        Character(7, "King Kilowatt", "Rules the city", Perk.MORE_COINS, "Earns 50% more coins", 1500),
    )

    fun byId(id: Int) = all.firstOrNull { it.id == id } ?: all[0]
}
