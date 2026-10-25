package com.vedshubham.chalkrush.game

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
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

    /** One bot that just walks straight ahead, for testing line cuts. */
    private fun duelGame(): Pair<Game, Player> {
        val game = Game(size = 60, botCount = 1, seed = 2, countdownSeconds = 0f, powerUpsEnabled = false)
        val bot = game.players[1]
        bot.brain = null
        game.steer(game.me.angle + 1f)
        run(game, 1f) { game.steer(game.me.angle + 1f) }
        assertTrue("bot should be out drawing a line", bot.trail.size >= 3)
        return game to bot
    }

    /** Puts "you" next to the bot's line, heading straight at it. */
    private fun aimAtLine(game: Game, bot: Player) {
        val i = bot.trail[1]
        val tx = i % game.size + 0.5f
        val ty = i / game.size + 0.5f
        val me = game.me
        me.x = tx - sin(bot.angle) * 1.6f
        me.y = ty + cos(bot.angle) * 1.6f
        me.cellX = floor(me.x).toInt()
        me.cellY = floor(me.y).toInt()
        me.angle = atan2(ty - me.y, tx - me.x)
        game.steer(me.angle)
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
        drive(game, 0f, 1f)
        drive(game, (PI / 2).toFloat(), 0.6f)
        drive(game, PI.toFloat(), 0.4f)
        drive(game, (-PI / 2).toFloat(), 1.5f)
        assertTrue(game.me.alive)
    }

    @Test
    fun theEdgeIsAWall() {
        val game = soloGame()
        drive(game, 0f, 20f)
        assertTrue(game.me.alive)
        assertTrue(game.me.x in 0f..game.size.toFloat())
    }

    @Test
    fun touchingSomeoneElsesLineKnocksThemOut() {
        val (game, bot) = duelGame()
        bot.shield = 0f
        aimAtLine(game, bot)
        run(game, 0.5f)
        assertFalse(bot.alive)
        assertEquals(1, game.me.kills)
        assertEquals(Knockout.LINE_CUT, bot.knockout)
    }

    @Test
    fun aShieldProtectsYourLine() {
        val (game, bot) = duelGame()
        bot.shield = 10f
        aimAtLine(game, bot)
        run(game, 0.5f)
        assertTrue(bot.alive)
        assertTrue(game.events.any { it is GameEvent.Blocked })
    }

    @Test
    fun roundEndsWhenTimeRunsOut() {
        val game = Game(size = 60, botCount = 0, roundSeconds = 2f, seed = 3, countdownSeconds = 0f)
        // Turn in place inside home so we never leave.
        run(game, 5f) { game.steer(game.me.angle + 1f) }
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
                game.steer(game.me.angle + 1f)
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
        assertTrue("trail ${me.trail.size}", me.trail.size >= 2)
        val i = me.trail[me.trail.size / 2]
        val tx = i % game.size + 0.5f
        val ty = i / game.size + 0.5f
        bot.x = tx - sin(me.angle) * 1.6f
        bot.y = ty + cos(me.angle) * 1.6f
        bot.cellX = floor(bot.x).toInt()
        bot.cellY = floor(bot.y).toInt()
        bot.angle = atan2(ty - bot.y, tx - bot.x)
        bot.targetAngle = bot.angle
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
            run(game, 48.1f) { game.steer(game.me.angle + 1f) }
            assertTrue("${map.name} should be over", game.over)
        }
    }

    @Test
    fun everyCharacterCanPlay() {
        for (c in Characters.all) {
            val game = Game(size = 80, botCount = 7, seed = c.id.toLong(), myCharacter = c, roundSeconds = 20f)
            run(game, 30f) { game.steer(game.me.angle + 1f) }
            assertEquals(c, game.me.character)
            assertTrue(game.players.drop(1).none { it.character == c })
        }
    }
}
