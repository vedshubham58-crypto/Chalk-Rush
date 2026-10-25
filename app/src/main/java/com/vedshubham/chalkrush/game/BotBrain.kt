package com.vedshubham.chalkrush.game

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot

/**
 * Simple rules for a computer player: leave home, draw a box-shaped loop,
 * run home when someone gets close, grab nearby power-ups, and cut enemy
 * lines that are in reach.
 * [boldness] above 1 means longer trips and more attacks.
 */
internal class BotBrain(private val boldness: Float) {
    private var thinkIn = 0f
    private var legTime = 0f
    private var legsLeft = 0
    private var turnSign = 1f
    private var goingHome = false
    private var wasHome = true

    fun reset() {
        thinkIn = 0f
        legsLeft = 0
        goingHome = false
        wasHome = true
    }

    fun think(game: Game, me: Player, dt: Float) {
        legTime -= dt
        thinkIn -= dt
        if (thinkIn > 0f) return
        thinkIn = 0.1f

        val atHome = me.trail.isEmpty()
        if (atHome) {
            if (!wasHome || legsLeft <= 0) planTrip(game, me)
            wasHome = true
            goingHome = false
        } else {
            wasHome = false
        }

        var desired = me.targetAngle
        if (!atHome) {
            val home = game.nearestOwnCell(me)
            val homeDist = home?.let { dist(me, it.first, it.second) } ?: 0f
            val scared = !me.shielded && danger(game, me) < homeDist + 4f
            if (home != null && (goingHome || scared || me.trail.size > 45 * boldness)) {
                goingHome = true
                desired = angleTo(me, home.first, home.second)
            } else {
                (attackTarget(game, me) ?: powerUpTarget(game, me))?.let { desired = it } ?: run {
                    if (legTime <= 0f) {
                        legsLeft--
                        if (legsLeft <= 0) {
                            goingHome = true
                        } else {
                            desired = me.angle + turnSign * (PI / 2).toFloat()
                            legTime = legLength(game)
                        }
                    }
                }
            }
        }
        me.targetAngle = safest(game, me, desired)
    }

    private fun planTrip(game: Game, me: Player) {
        val r = game.random
        legsLeft = 3
        turnSign = if (r.nextBoolean()) 1f else -1f
        legTime = legLength(game)
        // Head roughly toward open space: away from the nearest edge.
        val towardCenter = angleTo(me, game.size / 2, game.size / 2)
        me.targetAngle = towardCenter + (r.nextFloat() - 0.5f) * PI.toFloat() * 1.4f
    }

    private fun legLength(game: Game) = (0.7f + game.random.nextFloat() * 1.3f) * boldness

    /** How close the nearest enemy is to any part of my line. */
    private fun danger(game: Game, me: Player): Float {
        var best = Float.MAX_VALUE
        for (other in game.players) {
            if (!other.alive || other.id == me.id) continue
            if (dist(me, floor(other.x).toInt(), floor(other.y).toInt()) > me.trail.size + 20) continue
            for (k in me.trail.indices step 2) {
                val i = me.trail[k]
                val d = hypot(other.x - (i % game.size + 0.5f), other.y - (i / game.size + 0.5f))
                if (d < best) best = d
            }
        }
        return best
    }

    /** Angle toward an enemy line I can reach before its owner gets home. */
    private fun attackTarget(game: Game, me: Player): Float? {
        if (boldness < 0.9f) return null
        for (other in game.players) {
            if (!other.alive || other.id == me.id || other.trail.isEmpty() || other.shielded) continue
            for (i in other.trail) {
                val x = i % game.size
                val y = i / game.size
                val d = dist(me, x, y)
                if (d < 7f && d + 1f < dist(other, x, y)) return angleTo(me, x, y)
            }
        }
        return null
    }

    /** Angle toward a power-up that's close by. */
    private fun powerUpTarget(game: Game, me: Player): Float? {
        val near = game.powerUps.minByOrNull { dist(me, it.x, it.y) } ?: return null
        return if (dist(me, near.x, near.y) < 8f) angleTo(me, near.x, near.y) else null
    }

    /**
     * The grid direction nearest to [desired] that doesn't run into my own line
     * or the wall soon. Bikes can't U-turn, so straight back is never an option.
     */
    private fun safest(game: Game, me: Player, desired: Float): Float {
        val want = Game.dirOf(desired)
        val back = (me.dir + 2) % 4
        // Try the wanted way first, then the turns closest to it.
        val order = intArrayOf(want, (want + 1) % 4, (want + 3) % 4, (want + 2) % 4)
            .sortedBy { d -> if (d == want) 0f else angleGap(desired, d * Game.HALF_PI) + 1f }
        var best = me.dir
        var bestRoom = -1
        for (d in order) {
            if (d == back) continue
            val room = roomAhead(game, me, d)
            if (room >= LOOKAHEAD) return d * Game.HALF_PI
            if (room > bestRoom) {
                bestRoom = room
                best = d
            }
        }
        return best * Game.HALF_PI
    }

    /**
     * Counts open cells going straight in [d] from where the next turn happens,
     * up to [LOOKAHEAD]. Reaching home counts as safe; my own line or the wall stops it.
     */
    private fun roomAhead(game: Game, me: Player, d: Int): Int {
        var x = me.cellX
        var y = me.cellY
        val along = (me.x - (x + 0.5f)) * Game.DX[me.dir] + (me.y - (y + 0.5f)) * Game.DY[me.dir]
        if (along > 1e-4f) {
            // Already past this cell's centre, so the turn happens one cell on.
            x += Game.DX[me.dir]
            y += Game.DY[me.dir]
            if (cellRisk(game, me, x, y) < 0) return 0
        }
        for (k in 1..LOOKAHEAD) {
            x += Game.DX[d]
            y += Game.DY[d]
            val risk = cellRisk(game, me, x, y)
            if (risk > 0) return LOOKAHEAD
            if (risk < 0) return k - 1
        }
        return LOOKAHEAD
    }

    private fun angleGap(a: Float, b: Float): Float {
        var diff = (a - b) % Game.TWO_PI
        if (diff > PI) diff -= Game.TWO_PI
        if (diff < -PI) diff += Game.TWO_PI
        return abs(diff)
    }

    /** 1 = home (safe), -1 = own line or off the board, 0 = open ground. */
    private fun cellRisk(game: Game, me: Player, x: Int, y: Int): Int {
        if (!game.inBounds(x, y)) return -1
        val i = y * game.size + x
        if (game.owner[i] == me.id) return 1
        if (game.trailOwner[i] == me.id) return -1
        return 0
    }

    private fun dist(p: Player, x: Int, y: Int) = hypot(p.x - (x + 0.5f), p.y - (y + 0.5f))

    private fun angleTo(p: Player, x: Int, y: Int) = atan2(y + 0.5f - p.y, x + 0.5f - p.x)

    private companion object {
        const val LOOKAHEAD = 5
    }
}
