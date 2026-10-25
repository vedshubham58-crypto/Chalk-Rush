package com.vedshubham.chalkrush.data

import android.content.Context

/** What the game remembers on the phone. */
class Store(context: Context) {
    private val prefs = context.getSharedPreferences("chalk_rush", Context.MODE_PRIVATE)

    val best: Float get() = prefs.getFloat(KEY_BEST, 0f)
    val rounds: Int get() = prefs.getInt(KEY_ROUNDS, 0)

    /** Saves a finished round. Returns true if it's a new best. */
    fun saveRound(percent: Float): Boolean {
        val isBest = percent > best
        prefs.edit()
            .putInt(KEY_ROUNDS, rounds + 1)
            .apply { if (isBest) putFloat(KEY_BEST, percent) }
            .apply()
        return isBest
    }

    private companion object {
        const val KEY_BEST = "best_percent"
        const val KEY_ROUNDS = "rounds"
    }
}
