package com.vedshubham.chalkrush.game

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.random.Random

/** No one owns this cell. */
const val NONE = -1

/** Why a player was knocked out, for the results screen. */
enum class Knockout { LINE_CUT, LAND_TAKEN }

enum class PowerType {
    SPEED, SHIELD, SPLASH,

    /** Slows the riders around you for a few seconds. */
    FREEZE,

    /** Pulls in the coins around you for a few seconds. */
    MAGNET,
}

class PowerUp(val x: Int, val y: Int, val type: PowerType)

/** Things that just happened, so the screen can play effects and sounds. */
sealed interface GameEvent {
    data object Go : GameEvent
    data class Captured(val playerId: Int, val gained: Int, val x: Float, val y: Float) : GameEvent
    data class KnockedOut(val victimId: Int, val killerId: Int?, val x: Float, val y: Float) : GameEvent
    data class PickedUp(val playerId: Int, val type: PowerType, val x: Float, val y: Float) : GameEvent
    data class Blocked(val playerId: Int, val x: Float, val y: Float) : GameEvent
    data class Streak(val count: Int) : GameEvent
    data class CoinPicked(val x: Float, val y: Float) : GameEvent
}

class Player(val id: Int, val character: Character, val isBot: Boolean) {
    /** The player's chosen username, for "you". */
    var nickname: String? = null
    val name: String get() = nickname ?: if (isBot) character.name else "You"

    /** Position in cell units. Cell (i, j) covers [i, i+1) x [j, j+1). */
    var x = 0f
    var y = 0f
    /** Heading: 0 right, 1 down, 2 left, 3 up. Bikes only move along the grid. */
    var dir = 0
    val angle: Float get() = dir * Game.HALF_PI

    /** The way this rider wants to go; applied at the next cell centre (no U-turns). */
    var targetAngle = 0f

    /** Riding right alongside someone else's trail, which gives a speed boost. */
    var grinding = false
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

    /** Seconds left on each power-up. */
    var boost = 0f
    var shield = 0f
    var frozen = 0f
    var magnet = 0f

    /** Coins grabbed off the map this round. */
    var coins = 0

    internal var brain: BotBrain? = null

    val shielded get() = shield > 0f
}

data class FeedItem(val text: String, val at: Float)

/**
 * The whole game: a square board of cells, each owned by at most one player,
 * plus everyone's chalk lines. Pure Kotlin so it can be tested without a phone.
 *
 * Only other players can knock you out, by touching your line while you're
 * away from home. Crossing your own line is fine, and the edge is a wall.
 */
class Game(
    val size: Int = 120,
    botCount: Int = 7,
    val roundSeconds: Float = 45f,
    seed: Long = System.nanoTime(),
    myCharacter: Character = Characters.all[0],
    countdownSeconds: Float = 3f,
    private val powerUpsEnabled: Boolean = true,
    minBoldness: Float = 0.7f,
    maxBoldness: Float = 1.4f,
    /** Bots move at this fraction of normal speed. */
    private val botSpeed: Float = 1f,
    /** Owning this much of the board wins the round straight away. */
    val winPercent: Float = 101f,
    /** Which cells are inside the arena (a country's shape); null means the whole square. */
    mask: BooleanArray? = null,
    /** Bike upgrades for "you": extra speed (0.04 = 4% faster) and extra seconds of spawn shield. */
    private val mySpeedBonus: Float = 0f,
    private val myShieldBonus: Float = 0f,
) {
    constructor(
        map: GameMap,
        myCharacter: Character,
        seed: Long = System.nanoTime(),
        speedBonus: Float = 0f,
        shieldBonus: Float = 0f,
    ) : this(
        size = map.size,
        botCount = map.bots,
        seed = seed,
        myCharacter = myCharacter,
        minBoldness = map.minBoldness,
        maxBoldness = map.maxBoldness,
        botSpeed = map.botSpeed,
        winPercent = map.winPercent,
        mask = map.arena.mask(),
        mySpeedBonus = speedBonus,
        myShieldBonus = shieldBonus,
    )

    val random = Random(seed)
    val cells get() = size * size

    /** Whether each cell is inside the arena. Outside cells are wall. */
    val playable: BooleanArray = mask ?: BooleanArray(size * size) { true }
    private val playableCells: IntArray = (0 until size * size).filter { playable[it] }.toIntArray()

    /** How many cells can be owned; percentages are of this. */
    val area: Int = playableCells.size

    /** The middle of the arena, for bots heading into open space. */
    val centerX: Int = (playableCells.sumOf { (it % size).toLong() } / area.coerceAtLeast(1)).toInt()
    val centerY: Int = (playableCells.sumOf { (it / size).toLong() } / area.coerceAtLeast(1)).toInt()

    /** Coins lying on the map, as cell indices. */
    val fieldCoins = ArrayList<Int>()
    private var coinIn = 0.5f

    /** Who owns each cell (player id or [NONE]). */
    val owner = IntArray(size * size) { NONE }

    /** Whose chalk line runs through each cell (player id or [NONE]). */
    val trailOwner = IntArray(size * size) { NONE }

    val players: List<Player> = buildList {
        add(Player(0, myCharacter, isBot = false))
        val others = Characters.all.filter { it.id != myCharacter.id }.shuffled(random)
        for (i in 1..botCount) {
            add(Player(i, others[(i - 1) % others.size], isBot = true).also {
                it.brain = BotBrain(boldness = minBoldness + random.nextFloat() * (maxBoldness - minBoldness))
            })
        }
    }
    val me: Player get() = players[0]

    val powerUps = ArrayList<PowerUp>()
    private var powerUpIn = 2f

    /** Seconds left in the 3-2-1 countdown before anyone moves. */
    var countdown = countdownSeconds
        private set
    var elapsed = 0f
        private set
    val timeLeft: Float get() = (roundSeconds - elapsed).coerceAtLeast(0f)
    private var deadFor = 0f

    /** True once the round ends, you claim enough land, or shortly after you are knocked out. */
    val over: Boolean
        get() = elapsed >= roundSeconds || (!me.alive && deadFor >= DEATH_PAUSE) || wonEarly

    /** You reached [winPercent] before time ran out. */
    val wonEarly: Boolean get() = me.alive && percent(me) >= winPercent

    /** You won: still standing, and either hit the target or had the most land at the end. */
    val won: Boolean get() = me.alive && (wonEarly || (elapsed >= roundSeconds && myRank() == 1))

    /** Whether the one revive per round has been used. */
    var revived = false
        private set

    val feed = ArrayList<FeedItem>()

    /** Effects to play. The screen empties this every frame. */
    val events = ArrayList<GameEvent>()

    /** Your knockouts in a row, each within [STREAK_SECONDS] of the last. */
    var streak = 0
        private set
    var bestStreak = 0
        private set
    private var lastKillAt = -100f

    private val fillQueue = IntArray(size * size)
    private val fillMark = IntArray(size * size)
    private var fillStamp = 0

    init {
        players.forEach(::spawn)
    }

    fun percent(p: Player): Float = p.land * 100f / area

    /** Players by land, biggest first. */
    fun ranking(): List<Player> = players.sortedByDescending { it.land }

    /** Your place in the round, 1 = most land. */
    fun myRank(): Int = 1 + players.count { it.id != me.id && it.land > me.land }

    fun steer(angle: Float) {
        me.targetAngle = angle
    }

    fun update(dtRaw: Float) {
        val dt = dtRaw.coerceIn(0f, 0.05f)
        if (countdown > 0f) {
            countdown -= dt
            if (countdown <= 0f) events.add(GameEvent.Go)
            return
        }
        if (over) return
        elapsed += dt
        if (!me.alive) deadFor += dt
        if (powerUpsEnabled) {
            spawnPowerUps(dt)
            spawnCoins(dt)
        }
        for (p in players) {
            if (!p.alive) {
                if (p.isBot) {
                    p.respawnIn -= dt
                    if (p.respawnIn <= 0f) spawn(p)
                }
                continue
            }
            p.boost = (p.boost - dt).coerceAtLeast(0f)
            p.shield = (p.shield - dt).coerceAtLeast(0f)
            p.frozen = (p.frozen - dt).coerceAtLeast(0f)
            p.magnet = (p.magnet - dt).coerceAtLeast(0f)
            p.brain?.think(this, p, dt)
            move(p, dt)
            p.grinding = isGrinding(p)
        }
        if (elapsed - lastKillAt > STREAK_SECONDS) streak = 0
        feed.removeAll { elapsed - it.at > 4f }
    }

    /**
     * Brings you back after a knockout (once per round, for watching a video).
     * You keep your land and come back on it with a shield.
     */
    fun revive() {
        if (me.alive || revived) return
        revived = true
        deadFor = 0f
        val home = (0 until cells).firstOrNull { owner[it] == me.id }
        if (home == null) {
            spawn(me)
        } else {
            me.cellX = home % size
            me.cellY = home / size
            me.x = me.cellX + 0.5f
            me.y = me.cellY + 0.5f
            me.alive = true
            me.knockout = null
            me.knockedOutBy = null
        }
        me.shield = 3f
    }

    /** Whether the mid-round Power Drop has been used. */
    var powerDropUsed = false
        private set

    /** The mid-round Power Drop is on offer: about halfway through, while you're riding. */
    val powerDropOffered: Boolean
        get() = !powerDropUsed && me.alive && countdown <= 0f && !over &&
            elapsed in roundSeconds * 0.4f..roundSeconds * 0.4f + POWER_DROP_SECONDS

    /** The reward for watching the Power Drop video: a shield and a speed boost. */
    fun powerDrop() {
        if (powerDropUsed || !me.alive) return
        powerDropUsed = true
        me.shield = maxOf(me.shield, 5f)
        me.boost = maxOf(me.boost, 3f)
        events.add(GameEvent.PickedUp(me.id, PowerType.SHIELD, me.x, me.y))
    }

    /** Stops offering the Power Drop (no video was ready, or it was skipped). */
    fun skipPowerDrop() {
        powerDropUsed = true
    }

    fun speedOf(p: Player): Float {
        var s = SPEED
        if (p.isBot) s *= botSpeed
        if (p.character.perk == Perk.FASTER) s *= 1.1f
        if (p.id == 0) s *= 1f + mySpeedBonus
        if (p.boost > 0f) s *= 1.6f
        if (p.grinding) s *= if (p.character.perk == Perk.GRIND_MASTER) 1.6f else 1.35f
        if (p.frozen > 0f) s *= 0.5f
        return s
    }

    private fun isGrinding(p: Player): Boolean {
        if (p.trail.isEmpty()) return false
        val side = (p.dir + 1) % 4
        for (sign in intArrayOf(1, -1)) {
            val x = p.cellX + DX[side] * sign
            val y = p.cellY + DY[side] * sign
            if (!inBounds(x, y)) continue
            val t = trailOwner[y * size + x]
            if (t != NONE && t != p.id) return true
        }
        return false
    }

    /**
     * Light-cycle movement: straight along the grid, turning 90 degrees only at
     * cell centres. The arena wall turns you automatically instead of stopping you.
     */
    private fun move(p: Player, dt: Float) {
        var remaining = speedOf(p) * dt
        var guard = 0
        while (remaining > 1e-5f && p.alive && guard++ < 16) {
            val cx = p.cellX + 0.5f
            val cy = p.cellY + 0.5f
            val along = (p.x - cx) * DX[p.dir] + (p.y - cy) * DY[p.dir]
            if (abs(along) < 1e-4f) {
                // At a cell centre: take the turn the rider asked for, then dodge the wall.
                p.x = cx
                p.y = cy
                val want = dirOf(p.targetAngle)
                if (want != p.dir && want != (p.dir + 2) % 4) p.dir = want
                if (!inBounds(p.cellX + DX[p.dir], p.cellY + DY[p.dir])) {
                    p.dir = wallTurn(p)
                    p.targetAngle = p.angle
                }
            }
            val toCenter = if (along < -1e-4f) -along else 1f - along.coerceAtLeast(0f)
            val step = minOf(remaining, toCenter)
            p.x += DX[p.dir] * step
            p.y += DY[p.dir] * step
            remaining -= step
            val nx = floor(p.x).toInt()
            val ny = floor(p.y).toInt()
            if ((nx != p.cellX || ny != p.cellY) && inBounds(nx, ny)) {
                if (!enter(p, nx, ny)) return
            }
            if (step == toCenter) {
                p.x = p.cellX + 0.5f
                p.y = p.cellY + 0.5f
            }
        }
    }

    /** Which way to turn at the wall: towards the side with more room. */
    private fun wallTurn(p: Player): Int {
        val a = (p.dir + 1) % 4
        val b = (p.dir + 3) % 4
        val roomA = roomTo(p, a)
        val roomB = roomTo(p, b)
        // Boxed in on three sides (a narrow tip of the country): turn back.
        if (roomA == 0 && roomB == 0) return (p.dir + 2) % 4
        return if (roomA >= roomB) a else b
    }

    /** Open cells in a straight line from [p] going [d], before the wall. */
    private fun roomTo(p: Player, d: Int): Int {
        var n = 0
        var x = p.cellX + DX[d]
        var y = p.cellY + DY[d]
        while (inBounds(x, y) && n < size) {
            n++
            x += DX[d]
            y += DY[d]
        }
        return n
    }

    /** Steps [p] into cell (cx, cy). Returns false if they got knocked out. */
    private fun enter(p: Player, cx: Int, cy: Int): Boolean {
        p.cellX = cx
        p.cellY = cy
        val i = cy * size + cx
        val lineOwner = trailOwner[i]
        if (lineOwner != NONE && lineOwner != p.id) {
            val victim = players[lineOwner]
            // Your cut always lands: a bot's shield only stops other bots.
            if (victim.shielded && !(p.id == me.id && victim.isBot)) {
                events.add(GameEvent.Blocked(victim.id, cx + 0.5f, cy + 0.5f))
            } else {
                knockOut(victim, p, Knockout.LINE_CUT)
            }
        }

        if (owner[i] == p.id) {
            if (p.trail.isNotEmpty()) capture(p)
        } else if (trailOwner[i] == NONE) {
            trailOwner[i] = p.id
            p.trail.add(i)
        }
        if (powerUps.isNotEmpty()) pickUp(p)
        if (fieldCoins.isNotEmpty()) pickCoins(p)
        return p.alive
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
        val before = p.land
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
        // Everything outside the country counts as outside the loop too.
        if (area < cells) for (i in 0 until cells) if (!playable[i]) push(i)
        while (head < tail) {
            val i = fillQueue[head++]
            val x = i % size
            if (x > 0) push(i - 1)
            if (x < size - 1) push(i + 1)
            if (i >= size) push(i - size)
            if (i < cells - size) push(i + size)
        }
        for (i in 0 until cells) {
            if (fillMark[i] != stamp && owner[i] != p.id && playable[i]) setOwner(i, p.id)
        }
        afterLandGrab(p, before)
    }

    private fun afterLandGrab(p: Player, before: Int) {
        p.bestLand = maxOf(p.bestLand, p.land)
        events.add(GameEvent.Captured(p.id, p.land - before, p.x, p.y))
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
        // The knocker takes all the loser's land. Your own land stays put so a revive can bring you back on it.
        if (victim.id != me.id) {
            val taker = by?.takeIf { it.alive && it.id != victim.id }
            val before = taker?.land ?: 0
            for (i in 0 until cells) if (owner[i] == victim.id) setOwner(i, taker?.id ?: NONE)
            if (taker != null && taker.land > before) {
                taker.bestLand = maxOf(taker.bestLand, taker.land)
                events.add(GameEvent.Captured(taker.id, taker.land - before, victim.x, victim.y))
            }
        }
        victim.respawnIn = RESPAWN_SECONDS
        victim.boost = 0f
        victim.shield = 0f
        victim.frozen = 0f
        victim.magnet = 0f
        events.add(GameEvent.KnockedOut(victim.id, by?.id, victim.x, victim.y))
        if (by != null) {
            by.kills++
            feed.add(FeedItem("${by.name} knocked out ${victim.name}", elapsed))
            if (by.id == me.id) {
                streak = if (elapsed - lastKillAt <= STREAK_SECONDS) streak + 1 else 1
                lastKillAt = elapsed
                bestStreak = maxOf(bestStreak, streak)
                if (streak >= 2) events.add(GameEvent.Streak(streak))
            }
        }
    }

    private fun spawnPowerUps(dt: Float) {
        powerUpIn -= dt
        if (powerUpIn > 0f) return
        powerUpIn = POWER_UP_EVERY
        if (powerUps.size >= MAX_POWER_UPS) return
        repeat(40) {
            val i = playableCells[random.nextInt(area)]
            val x = i % size
            val y = i / size
            if (owner[i] == NONE && trailOwner[i] == NONE) {
                powerUps.add(PowerUp(x, y, PowerType.entries[random.nextInt(PowerType.entries.size)]))
                return
            }
        }
    }

    private fun pickUp(p: Player) {
        val it = powerUps.iterator()
        while (it.hasNext()) {
            val pu = it.next()
            if (abs(pu.x - p.cellX) > 1 || abs(pu.y - p.cellY) > 1) continue
            it.remove()
            val longer = if (p.character.perk == Perk.LONG_POWERUPS) 1.5f else 1f
            events.add(GameEvent.PickedUp(p.id, pu.type, pu.x + 0.5f, pu.y + 0.5f))
            when (pu.type) {
                PowerType.SPEED -> p.boost = 3f * longer
                PowerType.SHIELD -> p.shield = 5f * longer
                PowerType.SPLASH -> splash(p)
                PowerType.FREEZE -> for (other in players) {
                    if (other.alive && other.id != p.id && abs(other.cellX - p.cellX) + abs(other.cellY - p.cellY) < FREEZE_RANGE) {
                        other.frozen = 3f * longer
                    }
                }
                PowerType.MAGNET -> p.magnet = 6f * longer
            }
            return
        }
    }

    private fun spawnCoins(dt: Float) {
        coinIn -= dt
        if (coinIn > 0f) return
        coinIn = COIN_EVERY
        if (fieldCoins.size >= MAX_FIELD_COINS) return
        repeat(20) {
            val i = playableCells[random.nextInt(area)]
            if (owner[i] == NONE && trailOwner[i] == NONE && i !in fieldCoins) {
                fieldCoins.add(i)
                return
            }
        }
    }

    /** Only "you" collect coins; bots just ride over them. A magnet pulls them in from further away. */
    private fun pickCoins(p: Player) {
        if (p.id != me.id) return
        val reach = if (p.magnet > 0f) MAGNET_RANGE else 1
        val it = fieldCoins.iterator()
        while (it.hasNext()) {
            val i = it.next()
            val x = i % size
            val y = i / size
            if (abs(x - p.cellX) > reach || abs(y - p.cellY) > reach) continue
            it.remove()
            p.coins++
            events.add(GameEvent.CoinPicked(x + 0.5f, y + 0.5f))
        }
    }

    /** Paints a blob of land around [p] in one go. */
    private fun splash(p: Player) {
        val r = if (p.character.perk == Perk.BIG_SPLASH) 6 else 4
        val before = p.land
        for (y in p.cellY - r..p.cellY + r) for (x in p.cellX - r..p.cellX + r) {
            if (!inBounds(x, y)) continue
            val dx = x - p.cellX
            val dy = y - p.cellY
            if (dx * dx + dy * dy > r * r) continue
            val i = y * size + x
            if (trailOwner[i] == p.id) continue
            setOwner(i, p.id)
        }
        // Now standing on own land: the line drawn so far closes a loop too.
        if (p.trail.isNotEmpty()) capture(p) else afterLandGrab(p, before)
    }

    /** Puts [p] on the board with a small square of starting land. */
    private fun spawn(p: Player) {
        val radius = if (p.character.perk == Perk.BIG_START) START_RADIUS + 1 else START_RADIUS
        var cx = 0
        var cy = 0
        var found = false
        for (attempt in 0 until 400) {
            val c = playableCells[random.nextInt(area)]
            cx = c % size
            cy = c / size
            if (areaIsFree(cx, cy, radius + 2)) {
                found = true
                break
            }
        }
        if (!found) {
            // Crowded: settle for any spot whose own square fits inside the arena.
            for (attempt in 0 until 400) {
                val c = playableCells[random.nextInt(area)]
                cx = c % size
                cy = c / size
                if (fitsInside(cx, cy, radius)) break
            }
        }
        for (y in cy - radius..cy + radius) {
            for (x in cx - radius..cx + radius) {
                if (!inBounds(x, y)) continue
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
        p.dir = random.nextInt(4)
        p.targetAngle = p.angle
        p.grinding = false
        p.alive = true
        p.knockout = null
        p.knockedOutBy = null
        p.boost = 0f
        p.shield = (if (p.character.perk == Perk.SHIELD_START) 5f else SPAWN_SHIELD) + if (p.id == 0) myShieldBonus else 0f
        p.bestLand = maxOf(p.bestLand, p.land)
        p.brain?.reset()
    }

    private fun fitsInside(cx: Int, cy: Int, r: Int): Boolean {
        for (y in cy - r..cy + r) for (x in cx - r..cx + r) if (!inBounds(x, y)) return false
        return true
    }

    private fun areaIsFree(cx: Int, cy: Int, r: Int): Boolean {
        for (y in cy - r..cy + r) for (x in cx - r..cx + r) {
            if (!inBounds(x, y)) return false
            val i = y * size + x
            if (owner[i] != NONE || trailOwner[i] != NONE) return false
        }
        return players.none { it.alive && abs(it.cellX - cx) <= r && abs(it.cellY - cy) <= r }
    }

    fun inBounds(x: Int, y: Int) = x in 0 until size && y in 0 until size && playable[y * size + x]

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

    companion object {
        /** Cells per second. */
        const val SPEED = 8.5f

        const val HALF_PI = (PI / 2).toFloat()
        val DX = intArrayOf(1, 0, -1, 0)
        val DY = intArrayOf(0, 1, 0, -1)

        /** The grid direction (0 right, 1 down, 2 left, 3 up) nearest to [angle]. */
        fun dirOf(angle: Float): Int {
            val turns = Math.round(angle / HALF_PI)
            return ((turns % 4) + 4) % 4
        }
        const val START_RADIUS = 2
        const val RESPAWN_SECONDS = 3f
        const val SPAWN_SHIELD = 2f
        const val STREAK_SECONDS = 8f
        const val DEATH_PAUSE = 1.5f
        const val POWER_UP_EVERY = 4f
        const val MAX_POWER_UPS = 8
        const val POWER_DROP_SECONDS = 8f
        const val FREEZE_RANGE = 14
        const val MAGNET_RANGE = 5
        const val COIN_EVERY = 0.6f
        const val MAX_FIELD_COINS = 30
        const val TWO_PI = (2 * PI).toFloat()
    }
}
