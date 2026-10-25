package com.vedshubham.chalkrush.game

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.random.Random

/** No one owns this cell. */
const val NONE = -1

/** Why a player was knocked out, for the results screen. */
enum class Knockout { OWN_LINE, EDGE, LINE_CUT, LAND_TAKEN }

class Player(val id: Int, val name: String, val isBot: Boolean) {
    /** Position in cell units. Cell (i, j) covers [i, i+1) x [j, j+1). */
    var x = 0f
    var y = 0f
    var angle = 0f
    var targetAngle = 0f
    var alive = false
    var respawnIn = 0f
    var cellX = 0
    var cellY = 0

    /** The chalk line drawn since leaving home, as cell indices in order. */
    val trail = ArrayList<Int>()
    var land = 0
    var bestLand = 0
    var kills = 0
    var knockedOutBy: String? = null
    var knockout: Knockout? = null

    internal var brain: BotBrain? = null
}

data class FeedItem(val text: String, val at: Float)

/**
 * The whole game: a square board of cells, each owned by at most one player,
 * plus everyone's chalk lines. Pure Kotlin so it can be tested without a phone.
 */
class Game(
    val size: Int = 120,
    botCount: Int = 7,
    val roundSeconds: Float = 180f,
    seed: Long = System.nanoTime(),
) {
    val random = Random(seed)
    val cells get() = size * size

    /** Who owns each cell (player id or [NONE]). */
    val owner = IntArray(size * size) { NONE }

    /** Whose chalk line runs through each cell (player id or [NONE]). */
    val trailOwner = IntArray(size * size) { NONE }

    val players: List<Player> = buildList {
        add(Player(0, "You", isBot = false))
        val names = BOT_NAMES.shuffled(random)
        for (i in 1..botCount) {
            add(Player(i, names[(i - 1) % names.size], isBot = true).also {
                it.brain = BotBrain(boldness = 0.7f + random.nextFloat() * 0.7f)
            })
        }
    }
    val me: Player get() = players[0]

    var elapsed = 0f
        private set
    val timeLeft: Float get() = (roundSeconds - elapsed).coerceAtLeast(0f)

    /** True once the round ends or you are knocked out. */
    val over: Boolean get() = elapsed >= roundSeconds || !me.alive

    val feed = ArrayList<FeedItem>()

    private val fillQueue = IntArray(size * size)
    private val fillMark = IntArray(size * size)
    private var fillStamp = 0

    init {
        players.forEach(::spawn)
    }

    fun percent(p: Player): Float = p.land * 100f / cells

    /** Players by land, biggest first. */
    fun ranking(): List<Player> = players.sortedByDescending { it.land }

    /** Your place in the round, 1 = most land. */
    fun myRank(): Int = 1 + players.count { it.id != me.id && it.land > me.land }

    fun steer(angle: Float) {
        me.targetAngle = angle
    }

    fun update(dtRaw: Float) {
        if (over) return
        val dt = dtRaw.coerceIn(0f, 0.05f)
        elapsed += dt
        for (p in players) {
            if (!p.alive) {
                if (p.isBot) {
                    p.respawnIn -= dt
                    if (p.respawnIn <= 0f) spawn(p)
                }
                continue
            }
            p.brain?.think(this, p, dt)
            turn(p, dt)
            move(p, dt)
        }
        feed.removeAll { elapsed - it.at > 4f }
    }

    private fun turn(p: Player, dt: Float) {
        var diff = (p.targetAngle - p.angle) % TWO_PI
        if (diff > PI) diff -= TWO_PI
        if (diff < -PI) diff += TWO_PI
        val maxTurn = TURN_RATE * dt
        p.angle += if (abs(diff) <= maxTurn) diff else maxTurn * if (diff > 0) 1 else -1
    }

    private fun move(p: Player, dt: Float) {
        val distance = SPEED * dt
        val steps = ceil(distance / 0.4f).toInt().coerceAtLeast(1)
        val step = distance / steps
        val dx = cos(p.angle) * step
        val dy = sin(p.angle) * step
        repeat(steps) {
            p.x += dx
            p.y += dy
            val cx = floor(p.x).toInt()
            val cy = floor(p.y).toInt()
            if (cx != p.cellX || cy != p.cellY) {
                // Moving diagonally: visit a side cell too, so lines never have
                // corner-only gaps that a flood fill could leak through.
                if (cx != p.cellX && cy != p.cellY && !enter(p, cx, p.cellY)) return
                if (!enter(p, cx, cy)) return
            }
        }
    }

    /** Steps [p] into cell (cx, cy). Returns false if they got knocked out. */
    private fun enter(p: Player, cx: Int, cy: Int): Boolean {
        if (cx !in 0 until size || cy !in 0 until size) {
            knockOut(p, null, Knockout.EDGE)
            return false
        }
        p.cellX = cx
        p.cellY = cy
        val i = cy * size + cx
        val lineOwner = trailOwner[i]
        if (lineOwner == p.id) {
            knockOut(p, null, Knockout.OWN_LINE)
            return false
        }
        if (lineOwner != NONE) knockOut(players[lineOwner], p, Knockout.LINE_CUT)

        if (owner[i] == p.id) {
            if (p.trail.isNotEmpty()) capture(p)
        } else {
            trailOwner[i] = p.id
            p.trail.add(i)
        }
        return true
    }

    private fun setOwner(i: Int, id: Int) {
        val old = owner[i]
        if (old == id) return
        if (old != NONE) players[old].land--
        owner[i] = id
        if (id != NONE) players[id].land++
    }

    /** [p] got back home: their line and everything it encloses becomes theirs. */
    private fun capture(p: Player) {
        for (i in p.trail) {
            trailOwner[i] = NONE
            setOwner(i, p.id)
        }
        p.trail.clear()

        // Flood in from the board's edges. Whatever the flood can't reach is enclosed.
        val stamp = ++fillStamp
        var head = 0
        var tail = 0
        fun push(i: Int) {
            if (fillMark[i] != stamp && owner[i] != p.id) {
                fillMark[i] = stamp
                fillQueue[tail++] = i
            }
        }
        for (k in 0 until size) {
            push(k)
            push((size - 1) * size + k)
            push(k * size)
            push(k * size + size - 1)
        }
        while (head < tail) {
            val i = fillQueue[head++]
            val x = i % size
            if (x > 0) push(i - 1)
            if (x < size - 1) push(i + 1)
            if (i >= size) push(i - size)
            if (i < cells - size) push(i + size)
        }
        for (i in 0 until cells) {
            if (fillMark[i] != stamp && owner[i] != p.id) setOwner(i, p.id)
        }
        p.bestLand = maxOf(p.bestLand, p.land)

        for (other in players) {
            if (other.alive && other.id != p.id && other.land == 0) {
                knockOut(other, p, Knockout.LAND_TAKEN)
            }
        }
    }

    private fun knockOut(victim: Player, by: Player?, why: Knockout) {
        if (!victim.alive) return
        victim.alive = false
        victim.knockout = why
        victim.knockedOutBy = by?.name
        for (i in victim.trail) trailOwner[i] = NONE
        victim.trail.clear()
        if (victim.id != me.id) {
            for (i in 0 until cells) if (owner[i] == victim.id) setOwner(i, NONE)
        }
        victim.respawnIn = RESPAWN_SECONDS
        if (by != null) {
            by.kills++
            feed.add(FeedItem("${by.name} knocked out ${victim.name}", elapsed))
        }
    }

    /** Puts [p] on the board with a small square of starting land. */
    private fun spawn(p: Player) {
        val margin = START_RADIUS + 4
        var cx = 0
        var cy = 0
        for (attempt in 0 until 300) {
            cx = margin + random.nextInt(size - 2 * margin)
            cy = margin + random.nextInt(size - 2 * margin)
            if (areaIsFree(cx, cy, START_RADIUS + 2)) break
        }
        for (y in cy - START_RADIUS..cy + START_RADIUS) {
            for (x in cx - START_RADIUS..cx + START_RADIUS) {
                val i = y * size + x
                val line = trailOwner[i]
                if (line != NONE && line != p.id) knockOut(players[line], p, Knockout.LINE_CUT)
                setOwner(i, p.id)
            }
        }
        p.x = cx + 0.5f
        p.y = cy + 0.5f
        p.cellX = cx
        p.cellY = cy
        p.angle = random.nextFloat() * TWO_PI
        p.targetAngle = p.angle
        p.alive = true
        p.knockout = null
        p.knockedOutBy = null
        p.bestLand = maxOf(p.bestLand, p.land)
        p.brain?.reset()
    }

    private fun areaIsFree(cx: Int, cy: Int, r: Int): Boolean {
        for (y in cy - r..cy + r) for (x in cx - r..cx + r) {
            if (x !in 0 until size || y !in 0 until size) return false
            val i = y * size + x
            if (owner[i] != NONE || trailOwner[i] != NONE) return false
        }
        return players.none { it.alive && abs(it.cellX - cx) <= r && abs(it.cellY - cy) <= r }
    }

    fun inBounds(x: Int, y: Int) = x in 0 until size && y in 0 until size

    /** The nearest cell [p] owns, searching outward in square rings. */
    internal fun nearestOwnCell(p: Player): Pair<Int, Int>? {
        for (r in 0 until size) {
            val x0 = p.cellX - r
            val x1 = p.cellX + r
            val y0 = p.cellY - r
            val y1 = p.cellY + r
            for (x in x0..x1) for (y in intArrayOf(y0, y1)) {
                if (inBounds(x, y) && owner[y * size + x] == p.id) return x to y
            }
            for (y in y0 + 1 until y1) for (x in intArrayOf(x0, x1)) {
                if (inBounds(x, y) && owner[y * size + x] == p.id) return x to y
            }
            if (x0 < 0 && y0 < 0 && x1 >= size && y1 >= size) break
        }
        return null
    }

    private val pathParent = IntArray(size * size)

    /**
     * A cell a few steps along the shortest way home for [p] that doesn't cross
     * their own line, or null if home is out of reach within [maxVisits] cells.
     * Prefers a path that keeps a one-cell gap from the line, because players
     * can't turn on the spot.
     */
    internal fun wayHome(p: Player, maxVisits: Int = 5000): Pair<Int, Int>? =
        searchHome(p, maxVisits, keepGap = true) ?: searchHome(p, maxVisits, keepGap = false)

    private fun searchHome(p: Player, maxVisits: Int, keepGap: Boolean): Pair<Int, Int>? {
        val start = p.cellY * size + p.cellX
        val stamp = ++fillStamp
        var head = 0
        var tail = 0
        fillMark[start] = stamp
        pathParent[start] = -1
        fillQueue[tail++] = start
        var found = -1
        while (head < tail && tail < maxVisits) {
            val i = fillQueue[head++]
            if (owner[i] == p.id) {
                found = i
                break
            }
            val x = i % size
            for (n in intArrayOf(
                if (x > 0) i - 1 else -1,
                if (x < size - 1) i + 1 else -1,
                if (i >= size) i - size else -1,
                if (i < cells - size) i + size else -1,
            )) {
                if (n < 0 || fillMark[n] == stamp || trailOwner[n] == p.id) continue
                if (keepGap && owner[n] != p.id && nextToLine(p, n) &&
                    (abs(n % size - p.cellX) > 2 || abs(n / size - p.cellY) > 2)
                ) continue
                fillMark[n] = stamp
                pathParent[n] = i
                fillQueue[tail++] = n
            }
        }
        if (found < 0) return null
        val path = ArrayList<Int>()
        var c = found
        while (c != -1) {
            path.add(c)
            c = pathParent[c]
        }
        val aim = path[(path.size - 1 - 4).coerceAtLeast(0)]
        return aim % size to aim / size
    }

    private fun nextToLine(p: Player, i: Int): Boolean {
        val x = i % size
        val y = i / size
        for (yy in y - 1..y + 1) for (xx in x - 1..x + 1) {
            if (inBounds(xx, yy) && trailOwner[yy * size + xx] == p.id) return true
        }
        return false
    }

    companion object {
        /** Cells per second. */
        const val SPEED = 7f

        /** Radians per second. */
        const val TURN_RATE = 6f
        const val START_RADIUS = 2
        const val RESPAWN_SECONDS = 3f
        const val TWO_PI = (2 * PI).toFloat()

        val BOT_NAMES = listOf(
            "Zigzag", "Doodle", "Smudge", "Scribble", "Pebble", "Hopscotch",
            "Crayon", "Squiggle", "Dusty", "Sketch", "Loopy", "Swirl",
        )
    }
}
