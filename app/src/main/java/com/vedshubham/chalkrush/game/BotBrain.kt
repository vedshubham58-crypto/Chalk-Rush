package com.vedshubham.chalkrush.game

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Simple rules for a computer player: leave home, draw a box-shaped loop,
 * run home when someone gets close, and cut enemy lines that are in reach.
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
            val nearest = game.nearestOwnCell(me)
            val homeDist = nearest?.let { dist(me, it.first, it.second) } ?: 0f
            val home = if (nearest == null) null else game.wayHome(me) ?: nearest
            if (home != null && (goingHome || danger(game, me) < homeDist + 4f ||
                    me.trail.size > 45 * boldness)
            ) {
                goingHome = true
                desired = angleTo(me, home.first, home.second)
            } else {
                attackTarget(game, me)?.let { desired = it } ?: run {
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
            if (!other.alive || other.id == me.id || other.trail.isEmpty()) continue
            for (i in other.trail) {
                val x = i % game.size
                val y = i / game.size
                val d = dist(me, x, y)
                if (d < 7f && d + 1f < dist(other, x, y)) return angleTo(me, x, y)
            }
        }
        return null
    }

    /** The angle nearest to [desired] that doesn't run into my own line or the edge soon. */
    private fun safest(game: Game, me: Player, desired: Float): Float {
        var best = desired
        var bestRoom = -1f
        for (offset in OFFSETS) {
            val a = desired + offset
            val room = roomAhead(game, me, a)
            if (room >= LOOKAHEAD) return a
            if (room > bestRoom) {
                bestRoom = room
                best = a
            }
        }
        return best
    }

    /**
     * Plays the next few cells forward, turning toward [target] as fast as a
     * player can, and returns how far the bot gets before hitting its own line
     * or the edge ([LOOKAHEAD] if it doesn't). Getting home counts as safe.
     */
    private fun roomAhead(game: Game, me: Player, target: Float): Float {
        var x = me.x
        var y = me.y
        var angle = me.angle
        var cx = me.cellX
        var cy = me.cellY
        val step = 0.35f
        val maxTurn = Game.TURN_RATE * step / Game.SPEED
        var travelled = 0f
        while (travelled < LOOKAHEAD) {
            var diff = (target - angle) % Game.TWO_PI
            if (diff > PI) diff -= Game.TWO_PI
            if (diff < -PI) diff += Game.TWO_PI
            angle += diff.coerceIn(-maxTurn, maxTurn)
            x += cos(angle) * step
            y += sin(angle) * step
            travelled += step
            val nx = floor(x).toInt()
            val ny = floor(y).toInt()
            if (nx == cx && ny == cy) continue
            // Same side-cell rule as the game uses for diagonal moves.
            if (nx != cx && ny != cy) {
                val side = cellRisk(game, me, nx, cy)
                if (side != 0) return if (side > 0) LOOKAHEAD else travelled
            }
            cx = nx
            cy = ny
            val risk = cellRisk(game, me, cx, cy)
            if (risk != 0) return if (risk > 0) LOOKAHEAD else travelled
        }
        return LOOKAHEAD
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
        val OFFSETS = floatArrayOf(0f, 0.4f, -0.4f, 0.8f, -0.8f, 1.2f, -1.2f, 1.6f, -1.6f, 2.2f, -2.2f, 3.14f)
        const val LOOKAHEAD = 5f
    }
}
