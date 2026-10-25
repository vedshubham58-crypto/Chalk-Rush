package com.vedshubham.chalkrush.data

import android.content.Context
import com.vedshubham.chalkrush.game.Character
import com.vedshubham.chalkrush.game.Characters
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Profile(
    val coins: Int,
    val best: Float,
    val rounds: Int,
    val wins: Int,
    val owned: Set<Int>,
    val selected: Int,
    val muted: Boolean,
) {
    val character: Character get() = Characters.byId(selected)
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
    )

    /** Saves a finished round. Returns true if it's a new best. */
    fun saveRound(percent: Float, coins: Int, won: Boolean): Boolean {
        val p = _profile.value
        val isBest = percent > p.best
        prefs.edit()
            .putInt(KEY_ROUNDS, p.rounds + 1)
            .putInt(KEY_COINS, p.coins + coins)
            .putInt(KEY_WINS, p.wins + if (won) 1 else 0)
            .apply { if (isBest) putFloat(KEY_BEST, percent) }
            .apply()
        _profile.value = read()
        return isBest
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
    }
}

/** Coins for a round: land, knockouts, and a bonus for finishing in the top 3. */
fun coinsFor(percent: Float, kills: Int, rank: Int, character: Character): Int {
    val base = (percent * 3).toInt() + kills * 10 + when (rank) {
        1 -> 50
        2 -> 25
        3 -> 10
        else -> 0
    }
    val multiplier = if (character.perk == com.vedshubham.chalkrush.game.Perk.MORE_COINS) 1.5f else 1f
    return (base * multiplier).toInt().coerceAtLeast(5)
}
