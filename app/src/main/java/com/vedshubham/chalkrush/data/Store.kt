package com.vedshubham.chalkrush.data

import android.content.Context
import com.vedshubham.chalkrush.game.Character
import com.vedshubham.chalkrush.game.Characters
import com.vedshubham.chalkrush.game.GameMap
import com.vedshubham.chalkrush.game.Maps
import com.vedshubham.chalkrush.game.Perk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.TimeZone

const val MAX_UPGRADE = 5

/** Coins for the next level of a bike upgrade that is at [level] now. */
fun upgradeCost(level: Int) = listOf(150, 300, 500, 800, 1200).getOrElse(level) { Int.MAX_VALUE }

/** Total XP needed to reach [level] (level 1 needs none). */
fun xpForLevel(level: Int): Int = (1 until level).sumOf { 100 + 40 * (it - 1) }

fun levelFor(xp: Int): Int {
    var level = 1
    while (xpForLevel(level + 1) <= xp) level++
    return level
}

/** XP for a round: something for every round, more for land, knockouts and wins. */
fun xpFor(percent: Float, kills: Int, won: Boolean): Int = 20 + percent.toInt() + kills * 10 + if (won) 50 else 0

/** Coins for each day of the daily reward ladder; it starts over after day 7. */
val DAILY_REWARDS = listOf(25, 40, 60, 80, 100, 150, 250)

private fun today(): Long {
    val now = System.currentTimeMillis()
    return (now + TimeZone.getDefault().getOffset(now)) / 86_400_000L
}

data class Profile(
    val coins: Int,
    val best: Float,
    val rounds: Int,
    val wins: Int,
    val owned: Set<Int>,
    val selected: Int,
    val muted: Boolean,
    /** Highest map unlocked (index). */
    val mapsUnlocked: Int,
    val currentMap: Int,
    val winStreak: Int,
    val bestWinStreak: Int,
    /** Days in a row the daily reward was claimed, counting the last claim. */
    val dailyStreak: Int,
    /** The daily reward is waiting to be claimed today. */
    val dailyReady: Boolean,
    /** The username shown over your bike; empty until you pick one. */
    val name: String,
    /** Which emblem (see the emblem list in the UI) you show next to your name. */
    val emblem: Int,
    val xp: Int,
    val kills: Int,
    /** Maps (lands) you've won at least once. */
    val conquered: Set<Int>,
    /** Bike upgrade levels, 0..[MAX_UPGRADE]. */
    val engine: Int,
    val armor: Int,
) {
    val level: Int get() = levelFor(xp)
    /** XP into the current level, and how much the level needs. */
    val levelProgress: Pair<Int, Int> get() = (xp - xpForLevel(level)) to (xpForLevel(level + 1) - xpForLevel(level))
    val speedBonus: Float get() = engine * 0.03f
    val shieldBonus: Float get() = armor * 0.6f

    /** Which day of the 7-day reward ladder today's claim would be (1..7). */
    val dailyDay: Int get() = dailyStreak % DAILY_REWARDS.size + 1
    val dailyCoins: Int get() = DAILY_REWARDS[dailyDay - 1]

    val character: Character get() = Characters.byId(selected)
    val map: GameMap get() = Maps.byIndex(currentMap)
}

/** What the game remembers on the phone: coins, unlocked characters, best score. */
class Store(context: Context) {
    private val prefs = context.getSharedPreferences("chalk_rush", Context.MODE_PRIVATE)

    private val _profile = MutableStateFlow(read())
    val profile: StateFlow<Profile> = _profile.asStateFlow()

    private fun read() = Profile(
        coins = prefs.getInt(KEY_COINS, 0),
        best = prefs.getFloat(KEY_BEST, 0f),
        rounds = prefs.getInt(KEY_ROUNDS, 0),
        wins = prefs.getInt(KEY_WINS, 0),
        owned = prefs.getStringSet(KEY_OWNED, null)?.mapNotNull { it.toIntOrNull() }?.toSet().orEmpty() + 0,
        selected = prefs.getInt(KEY_SELECTED, 0),
        muted = prefs.getBoolean(KEY_MUTED, false),
        mapsUnlocked = prefs.getInt(KEY_MAPS, 0),
        currentMap = prefs.getInt(KEY_CURRENT_MAP, 0),
        winStreak = prefs.getInt(KEY_STREAK, 0),
        bestWinStreak = prefs.getInt(KEY_BEST_STREAK, 0),
        dailyStreak = currentDailyStreak(),
        dailyReady = prefs.getLong(KEY_DAILY_DAY, -1L) < today(),
        name = prefs.getString(KEY_NAME, "") ?: "",
        emblem = prefs.getInt(KEY_EMBLEM, 0),
        xp = prefs.getInt(KEY_XP, 0),
        kills = prefs.getInt(KEY_KILLS, 0),
        conquered = prefs.getStringSet(KEY_CONQUERED, null)?.mapNotNull { it.toIntOrNull() }?.toSet().orEmpty(),
        engine = prefs.getInt(KEY_ENGINE, 0),
        armor = prefs.getInt(KEY_ARMOR, 0),
    )

    fun setProfile(name: String, emblem: Int) {
        prefs.edit().putString(KEY_NAME, name.trim().take(MAX_NAME)).putInt(KEY_EMBLEM, emblem).apply()
        _profile.value = read()
    }

    /** Buys the next level of the engine or armor upgrade. Returns true on success. */
    fun upgrade(engine: Boolean): Boolean {
        val p = _profile.value
        val level = if (engine) p.engine else p.armor
        if (level >= MAX_UPGRADE) return false
        val cost = upgradeCost(level)
        if (p.coins < cost) return false
        prefs.edit()
            .putInt(KEY_COINS, p.coins - cost)
            .putInt(if (engine) KEY_ENGINE else KEY_ARMOR, level + 1)
            .apply()
        _profile.value = read()
        return true
    }

    /** The streak still counts if the last claim was today or yesterday. */
    private fun currentDailyStreak(): Int {
        val last = prefs.getLong(KEY_DAILY_DAY, -1L)
        return if (last >= today() - 1) prefs.getInt(KEY_DAILY_STREAK, 0) else 0
    }

    /** Claims today's reward, times [multiplier] for watching a video. Returns the coins given. */
    fun claimDaily(multiplier: Int = 1): Int {
        val p = _profile.value
        if (!p.dailyReady) return 0
        val coins = p.dailyCoins * multiplier
        prefs.edit()
            .putLong(KEY_DAILY_DAY, today())
            .putInt(KEY_DAILY_STREAK, p.dailyStreak + 1)
            .putInt(KEY_COINS, p.coins + coins)
            .apply()
        _profile.value = read()
        return coins
    }

    /**
     * Saves a finished round. A win grows the win streak and unlocks the next
     * map (and moves you onto it). Returns true if it's a new best land score.
     */
    fun saveRound(percent: Float, coins: Int, won: Boolean, kills: Int = 0, xp: Int = 0): Boolean {
        val p = _profile.value
        val isBest = percent > p.best
        val streak = if (won) p.winStreak + 1 else 0
        val edit = prefs.edit()
            .putInt(KEY_ROUNDS, p.rounds + 1)
            .putInt(KEY_COINS, p.coins + coins)
            .putInt(KEY_WINS, p.wins + if (won) 1 else 0)
            .putInt(KEY_STREAK, streak)
            .putInt(KEY_BEST_STREAK, maxOf(p.bestWinStreak, streak))
            .putInt(KEY_XP, p.xp + xp)
            .putInt(KEY_KILLS, p.kills + kills)
        if (won) edit.putStringSet(KEY_CONQUERED, (p.conquered + p.currentMap).map { it.toString() }.toSet())
        if (isBest) edit.putFloat(KEY_BEST, percent)
        if (won && p.currentMap < Maps.all.lastIndex) {
            val next = p.currentMap + 1
            edit.putInt(KEY_MAPS, maxOf(p.mapsUnlocked, next)).putInt(KEY_CURRENT_MAP, next)
        }
        edit.apply()
        _profile.value = read()
        return isBest
    }

    fun addCoins(coins: Int) {
        prefs.edit().putInt(KEY_COINS, _profile.value.coins + coins).apply()
        _profile.value = read()
    }

    fun selectMap(index: Int) {
        if (index !in 0.._profile.value.mapsUnlocked) return
        prefs.edit().putInt(KEY_CURRENT_MAP, index).apply()
        _profile.value = read()
    }

    /** Unlocks [c] if there are enough coins. Returns true on success. */
    fun buy(c: Character): Boolean {
        val p = _profile.value
        if (c.id in p.owned) return true
        if (p.coins < c.cost) return false
        prefs.edit()
            .putInt(KEY_COINS, p.coins - c.cost)
            .putStringSet(KEY_OWNED, (p.owned + c.id).map { it.toString() }.toSet())
            .putInt(KEY_SELECTED, c.id)
            .apply()
        _profile.value = read()
        return true
    }

    fun select(c: Character) {
        if (c.id !in _profile.value.owned) return
        prefs.edit().putInt(KEY_SELECTED, c.id).apply()
        _profile.value = read()
    }

    fun setMuted(muted: Boolean) {
        prefs.edit().putBoolean(KEY_MUTED, muted).apply()
        _profile.value = read()
    }

    private companion object {
        const val KEY_COINS = "coins"
        const val KEY_BEST = "best_percent"
        const val KEY_ROUNDS = "rounds"
        const val KEY_WINS = "wins"
        const val KEY_OWNED = "owned"
        const val KEY_SELECTED = "selected"
        const val KEY_MUTED = "muted"
        const val KEY_MAPS = "maps_unlocked"
        const val KEY_CURRENT_MAP = "current_map"
        const val KEY_STREAK = "win_streak"
        const val KEY_BEST_STREAK = "best_win_streak"
        const val KEY_DAILY_DAY = "daily_day"
        const val KEY_DAILY_STREAK = "daily_streak"
        const val KEY_NAME = "name"
        const val KEY_EMBLEM = "emblem"
        const val KEY_XP = "xp"
        const val KEY_KILLS = "kills"
        const val KEY_CONQUERED = "conquered"
        const val KEY_ENGINE = "engine"
        const val KEY_ARMOR = "armor"
        const val MAX_NAME = 14
    }
}

/**
 * Coins for a round: land, knockouts, a bonus for a win or a top-3 finish,
 * and a bonus that grows with your win streak.
 */
fun coinsFor(percent: Float, kills: Int, rank: Int, won: Boolean, winStreak: Int, character: Character): Int {
    val placing = when {
        won -> 50
        rank == 2 -> 25
        rank == 3 -> 10
        else -> 0
    }
    val streakBonus = if (won) 10 * winStreak.coerceAtMost(10) else 0
    val base = (percent * 3).toInt() + kills * 10 + placing + streakBonus
    val multiplier = if (character.perk == Perk.MORE_COINS) 1.5f else 1f
    return (base * multiplier).toInt().coerceAtLeast(5)
}
