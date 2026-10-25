package com.vedshubham.chalkrush.game

import kotlin.math.PI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameTest {
    private fun drive(game: Game, angle: Float, seconds: Float) {
        game.steer(angle)
        var t = 0f
        while (t < seconds && !game.over) {
            game.update(1f / 60)
            t += 1f / 60
        }
    }

    private fun soloGame() = Game(size = 60, botCount = 0, seed = 1)

    @Test
    fun startsWithASmallSquare() {
        val game = soloGame()
        assertEquals(25, game.me.land)
        assertTrue(game.me.alive)
    }

    @Test
    fun drawingALoopCapturesTheInside() {
        val game = soloGame()
        val start = game.me.land
        drive(game, 0f, 1f) // right
        drive(game, (PI / 2).toFloat(), 1f) // down
        drive(game, PI.toFloat(), 1f) // left
        drive(game, (-PI / 2).toFloat(), 1.5f) // up, back home
        assertTrue(game.me.alive)
        assertTrue("land should grow, was ${game.me.land}", game.me.land > start + 20)
        assertTrue(game.me.trail.isEmpty())
    }

    @Test
    fun hittingTheEdgeKnocksYouOut() {
        val game = soloGame()
        drive(game, 0f, 20f)
        assertFalse(game.me.alive)
        assertEquals(Knockout.EDGE, game.me.knockout)
        assertTrue(game.over)
    }

    @Test
    fun crossingYourOwnLineKnocksYouOut() {
        val game = soloGame()
        drive(game, 0f, 1f)
        drive(game, (PI / 2).toFloat(), 0.6f)
        drive(game, PI.toFloat(), 0.4f)
        drive(game, (-PI / 2).toFloat(), 1.5f)
        assertFalse(game.me.alive)
        assertEquals(Knockout.OWN_LINE, game.me.knockout)
    }

    @Test
    fun roundEndsWhenTimeRunsOut() {
        val game = Game(size = 60, botCount = 0, roundSeconds = 2f, seed = 3)
        // Turn in place inside home so we never leave.
        var t = 0f
        while (!game.over && t < 5f) {
            game.steer(game.me.angle + 1f)
            game.update(1f / 60)
            t += 1f / 60
        }
        assertTrue(game.over)
        assertEquals(0f, game.timeLeft)
    }

    @Test
    fun botsPlayAFullRoundAndClaimLand() {
        repeat(5) { seed ->
            val game = Game(botCount = 7, seed = seed.toLong())
            // Keep "you" safe: circle at home so the round runs to the end.
            var t = 0f
            while (!game.over) {
                game.steer(game.me.angle + 1f)
                game.update(1f / 30)
                t += 1f / 30
            }
            assertTrue("round should end on time", game.timeLeft == 0f || !game.me.alive)
            val bots = game.players.drop(1)
            assertTrue("some bot should claim land", bots.any { game.percent(it) > 1f })
            // Land counts must match the board.
            for (p in game.players) {
                assertEquals(game.owner.count { it == p.id }, p.land)
            }
        }
    }
}
