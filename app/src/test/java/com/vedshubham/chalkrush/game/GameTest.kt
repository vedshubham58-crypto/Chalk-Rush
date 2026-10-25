package com.vedshubham.chalkrush.game

import kotlin.math.PI
import kotlin.math.floor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameTest {
    private fun run(game: Game, seconds: Float, each: () -> Unit = {}) {
        var t = 0f
        while (t < seconds && !game.over) {
            each()
            game.update(1f / 60)
            t += 1f / 60
        }
    }

    private fun drive(game: Game, angle: Float, seconds: Float) {
        game.steer(angle)
        run(game, seconds)
    }

    private fun soloGame() = Game(size = 60, botCount = 0, seed = 1, countdownSeconds = 0f, powerUpsEnabled = false)

    /** Turns right at every cell centre, riding a tiny square inside home. */
    private fun circleHome(game: Game) = game.steer(game.me.angle + Game.HALF_PI)

    /** Puts [p] on the centre of cell [i], heading in grid direction [dir]. */
    private fun placeAt(game: Game, p: Player, i: Int, dir: Int) {
        p.cellX = i % game.size
        p.cellY = i / game.size
        p.x = p.cellX + 0.5f
        p.y = p.cellY + 0.5f
        p.dir = dir
        p.targetAngle = p.angle
    }

    /** The grid direction from cell [a] to the neighbouring cell [b]. */
    private fun dirBetween(game: Game, a: Int, b: Int): Int {
        val dx = b % game.size - a % game.size
        val dy = b / game.size - a / game.size
        return (0 until 4).first { Game.DX[it] == dx && Game.DY[it] == dy }
    }

    /** Puts [hunter] two cells beside [victim]'s line, riding straight at it. */
    private fun aimAt(game: Game, hunter: Player, victim: Player, k: Int) {
        val i = victim.trail[k]
        val side = (dirBetween(game, victim.trail[k - 1], i) + 1) % 4
        val start = (i % game.size - 2 * Game.DX[side]) + (i / game.size - 2 * Game.DY[side]) * game.size
        placeAt(game, hunter, start, side)
        if (hunter.id == game.me.id) game.steer(hunter.angle)
    }

    /** Faces "you" right, sitting on the centre of your start square. */
    private fun faceRight(game: Game) {
        val me = game.me
        placeAt(game, me, me.cellY * game.size + me.cellX, 0)
    }

    /** One bot that just rides straight ahead, for testing line cuts. */
    private fun duelGame(): Pair<Game, Player> {
        val game = Game(size = 60, botCount = 1, seed = 2, countdownSeconds = 0f, powerUpsEnabled = false)
        val bot = game.players[1]
        bot.brain = null
        run(game, 1f) { circleHome(game) }
        assertTrue("bot should be out drawing a line", bot.trail.size >= 3)
        return game to bot
    }

    @Test
    fun startsWithASmallSquare() {
        val game = soloGame()
        assertEquals(25, game.me.land)
        assertTrue(game.me.alive)
    }

    @Test
    fun nobodyMovesDuringTheCountdown() {
        val game = Game(size = 60, botCount = 0, seed = 1)
        val x = game.me.x
        run(game, 2.5f)
        assertEquals(x, game.me.x)
        run(game, 1f)
        assertTrue(game.me.x != x)
        assertTrue(game.events.contains(GameEvent.Go))
    }

    @Test
    fun drawingALoopCapturesTheInside() {
        val game = soloGame()
        faceRight(game)
        val start = game.me.land
        drive(game, 0f, 1f) // right
        drive(game, (PI / 2).toFloat(), 1f) // down
        drive(game, PI.toFloat(), 1f) // left
        drive(game, (-PI / 2).toFloat(), 1.1f) // up, back home
        assertTrue(game.me.alive)
        assertTrue("land should grow, was ${game.me.land}", game.me.land > start + 20)
        assertTrue(game.me.trail.isEmpty())
        assertTrue(game.events.any { it is GameEvent.Captured && it.gained > 20 })
    }

    @Test
    fun crossingYourOwnLineIsSafe() {
        val game = soloGame()
        faceRight(game)
        drive(game, 0f, 1f)
        drive(game, (PI / 2).toFloat(), 0.6f)
        drive(game, PI.toFloat(), 0.4f)
        drive(game, (-PI / 2).toFloat(), 1.5f)
        assertTrue(game.me.alive)
    }

    @Test
    fun theEdgeIsAWall() {
        val game = soloGame()
        faceRight(game)
        drive(game, 0f, 20f)
        assertTrue(game.me.alive)
        assertTrue(game.me.x in 0f..game.size.toFloat())
        assertTrue(game.me.y in 0f..game.size.toFloat())
    }

    @Test
    fun touchingSomeoneElsesLineKnocksThemOut() {
        val (game, bot) = duelGame()
        bot.shield = 0f
        aimAt(game, game.me, bot, 1)
        run(game, 0.5f)
        assertFalse(bot.alive)
        assertEquals(1, game.me.kills)
        assertEquals(Knockout.LINE_CUT, bot.knockout)
    }

    @Test
    fun aShieldProtectsYourLine() {
        val (game, bot) = duelGame()
        val me = game.me
        me.shield = 10f
        // You ride out to draw a line; the bot drives through it and bounces off.
        drive(game, me.angle, 0.7f)
        aimAt(game, bot, me, me.trail.size / 2)
        game.steer(me.angle)
        run(game, 0.5f)
        assertTrue(me.alive)
        assertTrue(game.events.any { it is GameEvent.Blocked })
    }

    @Test
    fun yourCutKnocksOutEvenAShieldedBot() {
        val (game, bot) = duelGame()
        bot.shield = 10f
        aimAt(game, game.me, bot, 1)
        run(game, 0.5f)
        assertFalse(bot.alive)
        assertEquals(1, game.me.kills)
    }

    @Test
    fun roundEndsWhenTimeRunsOut() {
        val game = Game(size = 60, botCount = 0, roundSeconds = 2f, seed = 3, countdownSeconds = 0f)
        // Circle inside home so we never leave.
        run(game, 5f) { circleHome(game) }
        assertTrue(game.over)
        assertEquals(0f, game.timeLeft)
    }

    @Test
    fun botsPlayAFullRoundAndClaimLand() {
        repeat(5) { seed ->
            val game = Game(botCount = 7, seed = seed.toLong())
            // Keep "you" safe: circle at home so the round runs to the end.
            var t = 0f
            var pickups = 0
            while (!game.over) {
                circleHome(game)
                game.update(1f / 30)
                pickups += game.events.count { it is GameEvent.PickedUp }
                game.events.clear()
                t += 1f / 30
            }
            assertTrue("round should end on time", game.timeLeft == 0f || !game.me.alive)
            val bots = game.players.drop(1)
            assertTrue("some bot should claim land", bots.any { game.percent(it) > 1f })
            assertTrue("bots should pick up power-ups", pickups > 0)
            // Land counts must match the board.
            for (p in game.players) {
                assertEquals(game.owner.count { it == p.id }, p.land)
            }
        }
    }

    @Test
    fun reviveBringsYouBackOnYourLand() {
        val (game, bot) = duelGame()
        val me = game.me
        // You head out and draw a line; then the bot drives straight through it.
        me.shield = 0f
        drive(game, me.angle, 0.7f)
        assertTrue("trail ${me.trail.size}", me.trail.size >= 3)
        aimAt(game, bot, me, me.trail.size / 2)
        game.steer(me.angle)
        var t = 0f
        while (me.alive && t < 1f) {
            game.update(1f / 60)
            t += 1f / 60
        }
        assertFalse("should be knocked out", me.alive)
        val land = me.land
        game.revive()
        assertTrue(me.alive)
        assertTrue(game.revived)
        assertEquals(land, me.land)
        assertEquals(game.me.id, game.owner[me.cellY * game.size + me.cellX])
        assertFalse(game.over)
    }

    @Test
    fun everyMapEndsWithin45Seconds() {
        for (map in Maps.all) {
            val game = Game(map, Characters.all[0], seed = map.index.toLong())
            assertEquals(map.size, game.size)
            assertEquals(map.bots + 1, game.players.size)
            run(game, 48.1f) { circleHome(game) }
            assertTrue("${map.name} should be over", game.over)
        }
    }

    @Test
    fun everyCharacterCanPlay() {
        for (c in Characters.all) {
            val game = Game(size = 80, botCount = 7, seed = c.id.toLong(), myCharacter = c, roundSeconds = 20f)
            run(game, 30f) { circleHome(game) }
            assertEquals(c, game.me.character)
            assertTrue(game.players.drop(1).none { it.character == c })
        }
    }

    @Test
    fun bikesTurnOnlyAtRightAnglesAndNeverUTurn() {
        val game = soloGame()
        faceRight(game)
        drive(game, 0f, 0.5f)
        drive(game, PI.toFloat(), 0.5f) // straight back: ignored
        assertEquals(0, game.me.dir)
        drive(game, 0.3f, 0.5f) // a little off: snaps to right
        assertEquals(0, game.me.dir)
        drive(game, (PI / 2).toFloat(), 0.3f)
        assertEquals(1, game.me.dir)
        val me = game.me
        assertEquals(me.x - floor(me.x), 0.5f, 0.01f) // still on the column's centre line
    }

    @Test
    fun ridingAlongsideAnotherTrailGrinds() {
        val (game, bot) = duelGame()
        val me = game.me
        val d = dirBetween(game, bot.trail[0], bot.trail[1])
        val side = (d + 1) % 4
        val i = bot.trail[0]
        placeAt(game, me, (i % game.size + Game.DX[side]) + (i / game.size + Game.DY[side]) * game.size, d)
        game.steer(me.angle)
        var grinded = false
        run(game, 0.3f) { grinded = grinded || me.grinding }
        assertTrue("should grind next to the trail", grinded)
        assertTrue(me.alive && bot.alive)
    }

    @Test
    fun powerDropIsOfferedMidRoundOnce() {
        val game = Game(size = 60, botCount = 0, roundSeconds = 45f, seed = 4, countdownSeconds = 0f, powerUpsEnabled = false)
        assertFalse(game.powerDropOffered)
        run(game, 19f) { circleHome(game) }
        assertTrue(game.powerDropOffered)
        game.powerDrop()
        assertFalse(game.powerDropOffered)
        assertTrue(game.me.shielded)
        assertTrue(game.me.boost > 0f)
    }

    @Test
    fun knockingSomeoneOutTakesTheirLand() {
        val (game, bot) = duelGame()
        bot.shield = 0f
        val mine = game.me.land
        val theirs = bot.land
        aimAt(game, game.me, bot, 1)
        run(game, 0.5f)
        assertFalse(bot.alive)
        assertTrue("you should own their land", game.me.land >= mine + theirs)
        assertEquals(game.owner.count { it == game.me.id }, game.me.land)
    }

    @Test
    fun countryArenasKeepEveryoneInside() {
        for (map in Maps.all) {
            val game = Game(map, Characters.all[0], seed = map.index.toLong())
            assertTrue("${map.name} should have room", game.area > 1500)
            for (p in game.players) assertTrue(game.inBounds(p.cellX, p.cellY))
            run(game, 30f) {
                circleHome(game)
                for (p in game.players) if (p.alive) assertTrue("${p.name} left ${map.name}", game.inBounds(p.cellX, p.cellY))
            }
            for (i in 0 until game.cells) if (!game.playable[i]) assertEquals(NONE, game.owner[i])
            for (p in game.players) assertEquals(game.owner.count { it == p.id }, p.land)
            assertTrue("bots should claim land in ${map.name}", game.players.drop(1).any { game.percent(it) > 1f })
        }
    }

    @Test
    fun youPickUpCoinsOffTheMap() {
        val game = Game(size = 60, botCount = 0, seed = 5, countdownSeconds = 0f)
        var picked = 0
        val me = game.me
        // Sweep the board in long straight runs to cross coins.
        run(game, 20f) {
            picked += game.events.count { it is GameEvent.CoinPicked }
            game.events.clear()
            if (game.elapsed % 3f < 0.02f) game.steer(me.angle + Game.HALF_PI)
        }
        assertEquals(picked, me.coins)
        assertTrue("should grab some coins", me.coins > 0)
    }
}
