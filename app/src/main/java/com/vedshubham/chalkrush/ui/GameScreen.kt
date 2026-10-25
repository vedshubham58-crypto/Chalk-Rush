package com.vedshubham.chalkrush.ui

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.vedshubham.chalkrush.game.Game
import com.vedshubham.chalkrush.game.GameEvent
import com.vedshubham.chalkrush.game.NONE
import com.vedshubham.chalkrush.game.Player
import com.vedshubham.chalkrush.game.PowerType
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

private val SpeedColor = Color(0xFFFFD23F)
private val ShieldColor = Color(0xFF5CE1FF)
private val SplashColor = Color(0xFFFF4FB8)

private class Particle(
    var x: Float, var y: Float, val vx: Float, val vy: Float,
    var life: Float, val maxLife: Float, val color: Color, val size: Float,
)

private class Popup(val text: String, val x: Float, val y: Float, val color: Color, var age: Float = 0f)

/** Everything on screen that isn't part of the game rules: camera, particles, pop-ups. */
private class Effects {
    var clock = 0f
    var camX = Float.NaN
    var camY = 0f
    var viewCells = 22f
    var shake = 0f
    val particles = ArrayList<Particle>()
    val popups = ArrayList<Popup>()
    var banner: String? = null
    var bannerColor = Color.White
    var bannerAge = 0f
    var happyFor = 0f
    var lastCount = 4
    val rng = Random(11)

    fun burst(x: Float, y: Float, color: Color, count: Int, speed: Float) {
        repeat(count) {
            val a = rng.nextFloat() * 6.283f
            val s = speed * (0.3f + rng.nextFloat())
            val life = 0.4f + rng.nextFloat() * 0.5f
            particles.add(Particle(x, y, cos(a) * s, sin(a) * s, life, life, color, 0.12f + rng.nextFloat() * 0.18f))
        }
        while (particles.size > 500) particles.removeAt(0)
    }

    fun show(text: String, color: Color) {
        banner = text
        bannerColor = color
        bannerAge = 0f
    }

    fun step(dt: Float) {
        clock += dt
        shake = (shake - dt * 3f).coerceAtLeast(0f)
        happyFor = (happyFor - dt).coerceAtLeast(0f)
        bannerAge += dt
        if (bannerAge > 1.4f) banner = null
        val pi = particles.iterator()
        while (pi.hasNext()) {
            val p = pi.next()
            p.life -= dt
            if (p.life <= 0f) pi.remove() else {
                p.x += p.vx * dt
                p.y += p.vy * dt
            }
        }
        popups.forEach { it.age += dt }
        popups.removeAll { it.age > 1.1f }
    }
}

@Composable
fun GameScreen(game: Game, mapIndex: Int, onRoundOver: () -> Unit) {
    val theme = themeOf(mapIndex)
    var frame by remember { mutableIntStateOf(0) }
    val fx = remember(game) { Effects() }
    val view = LocalView.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    fun buzz(strong: Boolean) {
        view.performHapticFeedback(if (strong) HapticFeedbackConstants.LONG_PRESS else HapticFeedbackConstants.KEYBOARD_TAP)
    }

    fun handle(e: GameEvent) {
        val me = game.me
        when (e) {
            GameEvent.Go -> {
                Sfx.play(Sound.GO)
                fx.show("GO!", SpeedColor)
            }
            is GameEvent.Captured -> {
                val color = colorOf(game.players[e.playerId].character.id).fill
                if (e.playerId == me.id && e.gained > 0) {
                    val pct = e.gained * 100f / game.cells
                    fx.popups.add(Popup("+%.1f%%".format(pct), e.x, e.y - 1f, Color.White))
                    fx.burst(e.x, e.y, color, 30 + e.gained.coerceAtMost(200) / 4, 6f)
                    fx.happyFor = 1.2f
                    Sfx.play(if (e.gained > 150) Sound.CAPTURE_BIG else Sound.CAPTURE)
                    if (e.gained > 300) fx.show("HUGE!", color)
                    buzz(false)
                }
            }
            is GameEvent.KnockedOut -> {
                val victim = game.players[e.victimId]
                fx.burst(e.x, e.y, colorOf(victim.character.id).fill, 50, 9f)
                fx.burst(e.x, e.y, Color.White, 20, 5f)
                when {
                    e.victimId == me.id -> {
                        fx.shake = 1f
                        Sfx.play(Sound.LOSE)
                        fx.show("KO!", Color(0xFFFF5A5A))
                        buzz(true)
                    }
                    e.killerId == me.id -> {
                        fx.shake = 0.6f
                        fx.popups.add(Popup("Knockout!", e.x, e.y - 1f, SpeedColor))
                        Sfx.play(Sound.KNOCKOUT)
                        if (game.streak < 2) fx.show("KO!", SpeedColor)
                        buzz(true)
                    }
                    hypot(e.x - me.x, e.y - me.y) < 18f -> Sfx.play(Sound.KNOCKOUT, volume = 0.35f)
                }
            }
            is GameEvent.PickedUp -> {
                val color = powerColor(e.type)
                fx.burst(e.x, e.y, color, 24, 5f)
                if (e.playerId == me.id) {
                    val label = when (e.type) {
                        PowerType.SPEED -> "Speed!"
                        PowerType.SHIELD -> "Shield!"
                        PowerType.SPLASH -> "Energy burst!"
                    }
                    fx.popups.add(Popup(label, e.x, e.y - 1f, color))
                    Sfx.play(Sound.POWER_UP)
                    buzz(false)
                }
            }
            is GameEvent.Blocked -> {
                fx.burst(e.x, e.y, ShieldColor, 16, 4f)
                if (e.playerId == me.id) {
                    fx.popups.add(Popup("Blocked!", e.x, e.y - 1f, ShieldColor))
                    Sfx.play(Sound.BLOCK)
                }
            }
            is GameEvent.Streak -> {
                fx.show(
                    when (e.count) {
                        2 -> "DOUBLE KO!"
                        3 -> "TRIPLE KO!"
                        else -> "UNSTOPPABLE!"
                    },
                    Color(0xFFFF9F1C),
                )
                fx.shake = 0.9f
            }
        }
    }

    // The game loop: runs every screen frame, pauses while the app is in the background.
    LaunchedEffect(game) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var last = 0L
            while (!game.over) {
                withFrameNanos { now ->
                    val dt = if (last == 0L) 0f else ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)
                    last = now
                    game.update(dt)
                    for (e in game.events) handle(e)
                    game.events.clear()
                    val count = ceil(game.countdown).toInt()
                    if (count in 1..3 && count != fx.lastCount) {
                        fx.lastCount = count
                        Sfx.play(Sound.BEEP)
                    }
                    // Chalk dust behind fast movers.
                    val me = game.me
                    if (me.alive && game.countdown <= 0f && fx.rng.nextFloat() < if (me.boost > 0f) 0.9f else 0.25f) {
                        fx.particles.add(
                            Particle(
                                me.x - cos(me.angle) * 0.5f, me.y - sin(me.angle) * 0.5f,
                                (fx.rng.nextFloat() - 0.5f), (fx.rng.nextFloat() - 0.5f),
                                0.5f, 0.5f, if (me.boost > 0f) SpeedColor else Color.White.copy(alpha = 0.7f), 0.16f,
                            ),
                        )
                    }
                    fx.step(dt)
                }
                frame++
            }
            onRoundOver()
        }
    }

    val miniMap = remember(game) { MiniMap(game, theme) }
    val textMeasurer = rememberTextMeasurer()
    val names = remember(game) {
        val style = TextStyle(
            color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
            shadow = Shadow(Color.Black.copy(alpha = 0.6f), Offset(1f, 2f), 3f),
        )
        game.players.map { textMeasurer.measure(it.name, style) }
    }
    val popupStyle = TextStyle(
        fontSize = 20.sp, fontWeight = FontWeight.Black,
        shadow = Shadow(Color.Black.copy(alpha = 0.7f), Offset(2f, 3f), 4f),
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(OffBoard)
            .pointerInput(game) {
                // Swipe anywhere: you head the way your finger moves.
                var pending = Offset.Zero
                val threshold = 6.dp.toPx()
                detectDragGestures(
                    onDragStart = { pending = Offset.Zero },
                    onDrag = { change, amount ->
                        change.consume()
                        pending += amount
                        if (pending.getDistance() > threshold) {
                            game.steer(atan2(pending.y, pending.x))
                            pending = Offset.Zero
                        }
                    },
                )
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            frame // redraw every frame
            val me = game.me
            // Smooth camera that zooms out as your land grows, with a little shake.
            val targetView = (22f + sqrt(me.land.toFloat()) * 0.12f).coerceAtMost(34f)
            fx.viewCells += (targetView - fx.viewCells) * 0.05f
            if (fx.camX.isNaN()) {
                fx.camX = me.x
                fx.camY = me.y
            }
            fx.camX += (me.x - fx.camX) * 0.18f
            fx.camY += (me.y - fx.camY) * 0.18f
            val cell = size.width / fx.viewCells
            val shake = fx.shake * fx.shake * cell * 0.6f
            val ox = size.width / 2 - fx.camX * cell + (fx.rng.nextFloat() - 0.5f) * shake
            val oy = size.height / 2 - fx.camY * cell + (fx.rng.nextFloat() - 0.5f) * shake

            drawBoard(game, cell, ox, oy, theme, fx.clock)
            drawTrails(game, cell, ox, oy)
            drawPowerUps(game, cell, ox, oy, fx.clock)

            for (p in fx.particles) {
                val a = (p.life / p.maxLife).coerceIn(0f, 1f)
                drawCircle(p.color.copy(alpha = p.color.alpha * a), p.size * cell * (0.5f + a * 0.5f), Offset(ox + p.x * cell, oy + p.y * cell))
            }

            for (p in game.players) {
                if (!p.alive && !(p.id == me.id)) continue
                val center = Offset(ox + p.x * cell, oy + p.y * cell)
                if (center.x < -cell * 4 || center.x > size.width + cell * 4 ||
                    center.y < -cell * 4 || center.y > size.height + cell * 4
                ) continue
                val c = center
                val radius = cell * 0.8f
                if (p.boost > 0f) {
                    for (k in 1..3) {
                        val back = c - Offset(cos(p.angle), sin(p.angle)) * (cell * 0.7f * k)
                        drawCircle(SpeedColor.copy(alpha = 0.3f / k), radius * (1f - k * 0.15f), back)
                    }
                }
                drawBike(p.character.id, c, radius, p.angle, moodOf(game, p, fx), fx.clock)
                if (p.shielded) {
                    val pulse = 0.5f + 0.3f * sin(fx.clock * 8f)
                    drawCircle(ShieldColor.copy(alpha = 0.15f), radius * 1.9f, c)
                    drawCircle(ShieldColor.copy(alpha = pulse), radius * 1.9f, c, style = Stroke(cell * 0.12f))
                }
                val label = names[p.id]
                drawText(label, topLeft = Offset(c.x - label.size.width / 2f, c.y - radius * 2f - label.size.height))
            }

            for (pop in fx.popups) {
                val t = pop.age / 1.1f
                val layout = textMeasurer.measure(pop.text, popupStyle.copy(color = pop.color.copy(alpha = 1f - t * t)))
                val x = ox + pop.x * cell - layout.size.width / 2f
                val y = oy + (pop.y - t * 1.8f) * cell - layout.size.height
                drawText(layout, topLeft = Offset(x, y))
            }
        }
        Hud(game, fx, tick = frame, miniMap = miniMap, mapIndex = mapIndex)
    }
}

private fun moodOf(game: Game, p: Player, fx: Effects): Mood {
    if (!p.alive) return Mood.KNOCKED_OUT
    if (p.id == game.me.id && fx.happyFor > 0f) return Mood.HAPPY
    if (p.boost > 0f) return Mood.HAPPY
    if (p.trail.size > 25 && !p.shielded) return Mood.SCARED
    return Mood.NORMAL
}

private fun powerColor(t: PowerType) = when (t) {
    PowerType.SPEED -> SpeedColor
    PowerType.SHIELD -> ShieldColor
    PowerType.SPLASH -> SplashColor
}

private fun DrawScope.drawPowerUps(game: Game, cell: Float, ox: Float, oy: Float, clock: Float) {
    for (pu in game.powerUps) {
        val bob = sin(clock * 4f + pu.x) * cell * 0.15f
        val c = Offset(ox + (pu.x + 0.5f) * cell, oy + (pu.y + 0.5f) * cell + bob)
        if (c.x < -cell * 2 || c.x > size.width + cell * 2 || c.y < -cell * 2 || c.y > size.height + cell * 2) continue
        val color = powerColor(pu.type)
        val r = cell * 0.75f
        drawCircle(color.copy(alpha = 0.25f + 0.1f * sin(clock * 6f)), r * 1.5f, c)
        drawCircle(Color.Black.copy(alpha = 0.2f), r, c + Offset(0f, cell * 0.2f))
        drawCircle(color, r, c)
        drawCircle(Color.White, r, c, style = Stroke(cell * 0.1f))
        when (pu.type) {
            PowerType.SPEED -> {
                val s = r * 0.55f
                drawLine(Color.White, c + Offset(s * 0.3f, -s), c + Offset(-s * 0.3f, s * 0.1f), cell * 0.14f, StrokeCap.Round)
                drawLine(Color.White, c + Offset(-s * 0.3f, s * 0.1f), c + Offset(s * 0.3f, -s * 0.1f), cell * 0.14f, StrokeCap.Round)
                drawLine(Color.White, c + Offset(s * 0.3f, -s * 0.1f), c + Offset(-s * 0.3f, s), cell * 0.14f, StrokeCap.Round)
            }
            PowerType.SHIELD -> drawCircle(Color.White, r * 0.45f, c, style = Stroke(cell * 0.12f))
            PowerType.SPLASH -> {
                drawCircle(Color.White, r * 0.3f, c)
                for (k in 0 until 5) {
                    val a = k * 1.2566f + clock
                    drawCircle(Color.White, r * 0.12f, c + Offset(cos(a), sin(a)) * (r * 0.55f))
                }
            }
        }
    }
}

private fun DrawScope.drawBoard(game: Game, cell: Float, ox: Float, oy: Float, theme: MapTheme, clock: Float) {
    val n = game.size
    drawRect(theme.night, topLeft = Offset(ox, oy), size = Size(n * cell, n * cell))

    val x0 = floor(-ox / cell).toInt().coerceAtLeast(0)
    val x1 = ceil((size.width - ox) / cell).toInt().coerceAtMost(n - 1)
    val y0 = floor(-oy / cell).toInt().coerceAtLeast(0)
    val y1 = ceil((size.height - oy) / cell).toInt().coerceAtMost(n - 1)

    // The grid: thin lines every 4 cells, brighter ones every 16.
    for (k in (x0 / 4) * 4..x1 + 1 step 4) {
        val bright = k % 16 == 0
        drawLine(if (bright) theme.grid.copy(alpha = 1f) else theme.grid.copy(alpha = 0.55f), Offset(ox + k * cell, oy + y0 * cell), Offset(ox + k * cell, oy + (y1 + 1) * cell), strokeWidth = if (bright) cell * 0.1f else cell * 0.05f)
    }
    for (k in (y0 / 4) * 4..y1 + 1 step 4) {
        val bright = k % 16 == 0
        drawLine(if (bright) theme.grid.copy(alpha = 1f) else theme.grid.copy(alpha = 0.55f), Offset(ox + x0 * cell, oy + k * cell), Offset(ox + (x1 + 1) * cell, oy + k * cell), strokeWidth = if (bright) cell * 0.1f else cell * 0.05f)
    }
    // A glowing wall around the arena, so it's clear you bounce off it.
    val pulse = 0.6f + 0.25f * sin(clock * 3f)
    drawRect(theme.accent.copy(alpha = 0.25f), topLeft = Offset(ox - cell * 0.6f, oy - cell * 0.6f), size = Size((n + 1.2f) * cell, (n + 1.2f) * cell), style = Stroke(cell * 1.0f))
    drawRect(theme.accent.copy(alpha = pulse), topLeft = Offset(ox - cell * 0.3f, oy - cell * 0.3f), size = Size((n + 0.6f) * cell, (n + 0.6f) * cell), style = Stroke(cell * 0.25f))
    if (x0 > x1 || y0 > y1) return

    // Land: a see-through neon fill, drawn in row runs.
    for (y in y0..y1) {
        var x = x0
        while (x <= x1) {
            val o = game.owner[y * n + x]
            if (o == NONE) {
                x++
                continue
            }
            var end = x
            while (end + 1 <= x1 && game.owner[y * n + end + 1] == o) end++
            drawRect(
                colorOf(game.players[o].character.id).fill.copy(alpha = 0.32f),
                topLeft = Offset(ox + x * cell, oy + y * cell),
                size = Size((end - x + 1) * cell + 0.5f, cell + 0.5f),
            )
            x = end + 1
        }
    }
    // Bright glowing borders where one player's land ends.
    for (pass in 0..1) {
        val width = if (pass == 0) cell * 0.45f else cell * 0.14f
        for (y in y0..y1) for (x in x0..x1) {
            val o = game.owner[y * n + x]
            if (o == NONE) continue
            val color = colorOf(game.players[o].character.id).fill.copy(alpha = if (pass == 0) 0.3f else 1f)
            val left = ox + x * cell
            val top = oy + y * cell
            if (x == 0 || game.owner[y * n + x - 1] != o) drawLine(color, Offset(left, top), Offset(left, top + cell), width)
            if (x == n - 1 || game.owner[y * n + x + 1] != o) drawLine(color, Offset(left + cell, top), Offset(left + cell, top + cell), width)
            if (y == 0 || game.owner[(y - 1) * n + x] != o) drawLine(color, Offset(left, top), Offset(left + cell, top), width)
            if (y == n - 1 || game.owner[(y + 1) * n + x] != o) drawLine(color, Offset(left, top + cell), Offset(left + cell, top + cell), width)
        }
    }
}

/** Light trails: a wide glow with a bright core, from home to the bike. */
private fun DrawScope.drawTrails(game: Game, cell: Float, ox: Float, oy: Float) {
    val n = game.size
    for (p in game.players) {
        if (!p.alive || p.trail.isEmpty()) continue
        val path = Path()
        p.trail.forEachIndexed { k, i ->
            val x = ox + (i % n + 0.5f) * cell
            val y = oy + (i / n + 0.5f) * cell
            if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.lineTo(ox + p.x * cell, oy + p.y * cell)
        val color = colorOf(p.character.id).fill
        drawPath(path, color.copy(alpha = 0.25f), style = Stroke(cell * 0.9f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(path, color, style = Stroke(cell * 0.35f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(path, Color.White.copy(alpha = 0.8f), style = Stroke(cell * 0.1f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
private fun Hud(game: Game, fx: Effects, tick: Int, miniMap: MiniMap, mapIndex: Int) {
    tick // refresh every frame
    val pill = Modifier
        .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
        .padding(horizontal = 10.dp, vertical = 6.dp)
    Box(Modifier.fillMaxSize().safeDrawingPadding().padding(12.dp)) {
        val left = ceil(game.timeLeft).toInt()
        Column(pill.align(Alignment.TopStart)) {
            Text(
                "0:%02d".format(left.coerceAtMost(59)),
                color = if (left <= 10) Color(0xFFFF6B6B) else Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
            )
            Text("Map ${mapIndex + 1}", color = themeOf(mapIndex).accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text("Win: most land\nor %.0f%%".format(game.winPercent), color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
        }

        Column(pill.align(Alignment.TopEnd).width(160.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val ranking = game.ranking()
            val shown = ranking.take(5).toMutableList()
            if (game.me !in shown) shown[shown.lastIndex] = game.me
            for (p in shown) {
                val place = ranking.indexOf(p) + 1
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(colorOf(p.character.id).fill, CircleShape))
                    Text(
                        "  $place. ${p.name}",
                        color = if (p.isBot) Color.White else SpeedColor,
                        fontSize = 13.sp,
                        fontWeight = if (p.isBot) FontWeight.Normal else FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    Text("%.1f%%".format(game.percent(p)), color = Color.White, fontSize = 13.sp)
                }
            }
            Text("Computer players", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp)
        }

        Column(
            Modifier.align(Alignment.TopCenter).padding(top = 56.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            for (item in game.feed.takeLast(3)) {
                Text(item.text, color = Color.White, fontSize = 13.sp, modifier = pill)
            }
        }

        // Big centre text: countdown, GO!, KO! and streaks.
        val count = ceil(game.countdown).toInt()
        val (big, bigColor, age) = when {
            count > 0 -> Triple(count.toString(), Color.White, 1f - (game.countdown - (count - 1)))
            fx.banner != null -> Triple(fx.banner!!, fx.bannerColor, fx.bannerAge)
            else -> Triple(null, Color.White, 0f)
        }
        if (big != null) {
            val pop = if (age < 0.15f) 0.6f + age / 0.15f * 0.6f else 1.2f - ((age - 0.15f) * 0.2f).coerceAtMost(0.2f)
            val alpha = if (count > 0) 1f else (1f - ((age - 1f) / 0.4f).coerceIn(0f, 1f))
            Text(
                big,
                color = bigColor.copy(alpha = alpha),
                fontSize = 64.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.6f), Offset(4f, 6f), 8f)),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(bottom = 160.dp)
                    .graphicsLayer {
                        scaleX = pop
                        scaleY = pop
                    },
            )
        }

        if (game.countdown > 0f) {
            Text(
                "Swipe to steer your bike.\nLeave your zone and loop back to claim land.\nHit other trails to knock riders out!",
                color = Color.White,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 120.dp).then(pill),
            )
        }

        Canvas(
            Modifier
                .align(Alignment.BottomStart)
                .size(104.dp)
                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                .padding(4.dp),
        ) {
            if (tick % 8 == 0) miniMap.refresh()
            drawMiniMap(game, miniMap)
        }

        Row(
            Modifier.align(Alignment.BottomEnd).then(pill),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "You  %.1f%%".format(game.percent(game.me)),
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
            )
            if (game.me.boost > 0f) Text("⚡", fontSize = 20.sp)
            if (game.me.shielded) Text("🛡", fontSize = 20.sp)
        }
    }
}
