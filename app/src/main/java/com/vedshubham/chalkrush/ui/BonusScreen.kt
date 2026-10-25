package com.vedshubham.chalkrush.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

private enum class Loot { COIN, GEM, BOX }

/** Something flying across the bonus screen. Positions are fractions of the screen. */
private class Flyer(var x: Float, var y: Float, var vx: Float, var vy: Float, val kind: Loot, val spin: Float) {
    var taken = false
}

private class Sparkle(val x: Float, val y: Float, val text: String, val color: Color, var age: Float = 0f)

private enum class Phase { GRAB, OPEN, DONE }

private const val GRAB_SECONDS = 8f
private const val COIN_VALUE = 3
private const val GEM_VALUE = 15
private val Gold = Color(0xFFFFC83D)
private val BoxPink = Color(0xFFFF2BD6)

/**
 * The bonus game after a win: coins, gems and mystery boxes fly up the screen
 * for a few seconds, and you tap or swipe to grab them. Then the boxes open,
 * and you can watch a video for one golden box on top.
 * [onGoldenBox] shows the video and calls back if it was watched.
 */
@Composable
fun BonusScreen(
    videoReady: Boolean,
    onGoldenBox: (onWatched: () -> Unit) -> Unit,
    onDone: (coins: Int) -> Unit,
) {
    val rng = remember { Random(System.nanoTime()) }
    val flyers = remember { ArrayList<Flyer>() }
    val sparkles = remember { ArrayList<Sparkle>() }
    var frame by remember { mutableIntStateOf(0) }
    var phase by remember { mutableStateOf(Phase.GRAB) }
    var timeLeft by remember { mutableStateOf(GRAB_SECONDS) }
    var grabbed by remember { mutableIntStateOf(0) }
    var boxes by remember { mutableIntStateOf(0) }
    val boxPrizes = remember { mutableStateListOf<Int>() }
    var opened by remember { mutableIntStateOf(0) }
    var golden by remember { mutableIntStateOf(0) }
    var goldenAsked by remember { mutableStateOf(false) }
    val clock = rememberClock()
    val textMeasurer = rememberTextMeasurer()

    // The grab round: spawn loot from the bottom and let gravity do the rest.
    LaunchedEffect(Unit) {
        var last = 0L
        var spawnIn = 0f
        while (timeLeft > 0f) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)
                last = now
                timeLeft = (timeLeft - dt).coerceAtLeast(0f)
                spawnIn -= dt
                if (spawnIn <= 0f) {
                    spawnIn = 0.16f + rng.nextFloat() * 0.14f
                    val roll = rng.nextFloat()
                    val kind = when {
                        roll < 0.14f -> Loot.BOX
                        roll < 0.24f -> Loot.GEM
                        else -> Loot.COIN
                    }
                    val x = 0.15f + rng.nextFloat() * 0.7f
                    flyers.add(Flyer(x, 1.08f, (0.5f - x) * 0.5f + (rng.nextFloat() - 0.5f) * 0.3f, -(1.35f + rng.nextFloat() * 0.45f), kind, rng.nextFloat() * 6f))
                }
                for (f in flyers) {
                    f.vy += 1.5f * dt
                    f.x += f.vx * dt
                    f.y += f.vy * dt
                }
                flyers.removeAll { it.taken || it.y > 1.2f }
                sparkles.forEach { it.age += dt }
                sparkles.removeAll { it.age > 0.8f }
                frame++
            }
        }
        flyers.clear()
        repeat(boxes) { boxPrizes.add(listOf(20, 25, 30, 40, 50, 75)[rng.nextInt(6)]) }
        phase = if (boxes > 0) Phase.OPEN else Phase.DONE
    }

    // Open the boxes one at a time.
    LaunchedEffect(phase) {
        if (phase != Phase.OPEN) return@LaunchedEffect
        delay(500)
        while (opened < boxPrizes.size) {
            delay(550)
            opened++
            Sfx.play(Sound.CAPTURE)
        }
        delay(400)
        phase = Phase.DONE
    }

    fun grab(px: Float, py: Float, w: Float, h: Float) {
        if (phase != Phase.GRAB) return
        for (f in flyers) {
            if (f.taken) continue
            if (hypot((f.x - px) * w, (f.y - py) * h) > w * 0.09f) continue
            f.taken = true
            when (f.kind) {
                Loot.COIN -> {
                    grabbed += COIN_VALUE
                    sparkles.add(Sparkle(f.x, f.y, "+$COIN_VALUE", Gold))
                    Sfx.play(Sound.COIN, volume = 0.5f)
                }
                Loot.GEM -> {
                    grabbed += GEM_VALUE
                    sparkles.add(Sparkle(f.x, f.y, "+$GEM_VALUE", NeonCyan))
                    Sfx.play(Sound.POWER_UP, volume = 0.6f)
                }
                Loot.BOX -> {
                    boxes++
                    sparkles.add(Sparkle(f.x, f.y, "BOX!", BoxPink))
                    Sfx.play(Sound.POWER_UP)
                }
            }
        }
    }

    val boxCoins = boxPrizes.take(opened).sum()
    val total = grabbed + boxCoins + golden

    MenuBackdrop {
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    // Tap or swipe across loot to grab it.
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            for (c in event.changes) {
                                if (c.pressed) grab(c.position.x / size.width, c.position.y / size.height, size.width.toFloat(), size.height.toFloat())
                            }
                        }
                    }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                frame // redraw every frame
                val r = size.width * 0.07f
                for (f in flyers) {
                    val c = Offset(f.x * size.width, f.y * size.height)
                    when (f.kind) {
                        Loot.COIN -> drawCoin(c, r, clock * 6f + f.spin)
                        Loot.GEM -> drawGem(c, r * 0.9f)
                        Loot.BOX -> drawMysteryBox(c, r * 1.2f, sin(clock * 5f + f.spin) * 10f, Color(0xFFB0185A), BoxPink)
                    }
                }
                for (s in sparkles) {
                    val t = s.age / 0.8f
                    val at = Offset(s.x * size.width, s.y * size.height)
                    drawCircle(s.color.copy(alpha = (1f - t) * 0.6f), r * (0.6f + t * 1.4f), at, style = Stroke(r * 0.2f * (1f - t) + 1f))
                    // The number floats up over the grabbed loot.
                    val label = textMeasurer.measure(s.text, TextStyle(color = s.color.copy(alpha = 1f - t), fontSize = 22.sp, fontWeight = FontWeight.Black))
                    drawText(label, topLeft = at - Offset(label.size.width / 2f, r * 1.5f + t * size.height * 0.08f))
                }
            }
            Column(
                Modifier.fillMaxSize().safeDrawingPadding().padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("BONUS RUSH", color = NeonYellow, fontSize = 34.sp, fontWeight = FontWeight.Black)
                when (phase) {
                    Phase.GRAB -> {
                        Text("Tap or swipe to grab coins, gems and boxes!", color = Color.White, textAlign = TextAlign.Center)
                        Text("%.1f".format(timeLeft), color = if (timeLeft < 3f) Color(0xFFFF6B6B) else Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black)
                    }
                    else -> Text("Nice grab!", color = Color.White, fontSize = 18.sp)
                }
                Pill {
                    CoinIcon(Modifier.size(24.dp))
                    Text("  $total", color = NeonYellow, fontSize = 24.sp, fontWeight = FontWeight.Black)
                    if (phase == Phase.GRAB) Text("   📦 $boxes", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }

                if (phase != Phase.GRAB) {
                    Column(
                        Modifier.weight(1f).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                    ) {
                        if (boxPrizes.isNotEmpty()) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                boxPrizes.take(6).forEachIndexed { k, prize ->
                                    BoxReveal(open = k < opened, prize = prize, time = clock, big = false)
                                }
                            }
                            if (boxPrizes.size > 6) Text("+${boxPrizes.size - 6} more boxes", color = Color.White.copy(alpha = 0.7f))
                        }
                        if (phase == Phase.DONE) {
                            if (goldenAsked || golden > 0) {
                                BoxReveal(open = golden > 0, prize = golden, time = clock, big = true, golden = true)
                            }
                            if (videoReady && !goldenAsked) {
                                Button(
                                    onClick = {
                                        goldenAsked = true
                                        onGoldenBox {
                                            golden = listOf(100, 150, 200, 250)[rng.nextInt(4)]
                                            Sfx.play(Sound.CAPTURE_BIG)
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().height(60.dp),
                                    shape = RoundedCornerShape(20.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB000), contentColor = Color.Black),
                                ) {
                                    Text("▶  Watch video: open a GOLDEN BOX", fontWeight = FontWeight.Black)
                                }
                            }
                            Button(
                                onClick = { onDone(total) },
                                modifier = Modifier.fillMaxWidth().height(60.dp),
                                shape = RoundedCornerShape(20.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = NeonPink, contentColor = Color.White),
                            ) {
                                Text("COLLECT $total COINS", fontSize = 20.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A mystery box that shakes, then pops open to show its coins. */
@Composable
private fun BoxReveal(open: Boolean, prize: Int, time: Float, big: Boolean, golden: Boolean = false) {
    val s = if (big) 110.dp else 52.dp
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.size(s)) {
            val c = Offset(size.width / 2, size.height * 0.6f)
            val r = size.minDimension * 0.32f
            val (dark, light) = if (golden) Color(0xFFC8901A) to Gold else Color(0xFFB0185A) to BoxPink
            if (open) {
                drawCircle(light.copy(alpha = 0.35f), r * 1.6f, c)
                for (k in 0 until 8) {
                    rotate(k * 45f + time * 40f, pivot = c) {
                        drawLine(light.copy(alpha = 0.5f), c, c + Offset(0f, -r * 1.7f), r * 0.12f)
                    }
                }
                drawCoin(c - Offset(0f, r * 0.2f), r * 0.8f, time * 4f)
            } else {
                drawMysteryBox(c, r, sin(time * 30f) * 6f, dark, light)
            }
        }
        if (open) Text("+$prize", color = if (golden) Gold else Color.White, fontWeight = FontWeight.Black, fontSize = if (big) 24.sp else 14.sp)
    }
}

/** A spinning gold coin; the spin squashes it sideways. */
private fun DrawScope.drawCoin(c: Offset, r: Float, spin: Float) {
    val squash = 0.35f + 0.65f * kotlin.math.abs(sin(spin))
    drawOval(Color(0xFFC8901A), Offset(c.x - r * squash, c.y - r + r * 0.12f), Size(r * 2 * squash, r * 2))
    drawOval(
        Brush.radialGradient(listOf(Color(0xFFFFF1A6), Gold), center = c - Offset(r * 0.3f * squash, r * 0.4f), radius = r * 1.4f),
        Offset(c.x - r * squash, c.y - r),
        Size(r * 2 * squash, r * 2),
    )
    drawOval(Color(0xFFC8901A), Offset(c.x - r * 0.6f * squash, c.y - r * 0.6f), Size(r * 1.2f * squash, r * 1.2f), style = Stroke(r * 0.12f))
}

private fun DrawScope.drawGem(c: Offset, r: Float) {
    val gem = Path().apply {
        moveTo(c.x, c.y - r)
        lineTo(c.x + r * 0.8f, c.y - r * 0.2f)
        lineTo(c.x, c.y + r)
        lineTo(c.x - r * 0.8f, c.y - r * 0.2f)
        close()
    }
    drawCircle(NeonCyan.copy(alpha = 0.25f), r * 1.3f, c)
    drawPath(gem, Brush.linearGradient(listOf(Color.White, NeonCyan, Color(0xFF00809A)), start = c - Offset(r, r), end = c + Offset(r, r)))
    drawPath(gem, Color.White, style = Stroke(r * 0.08f))
}

/** A gift box drawn as a little cube: a lit top, a front face and a ribbon. */
private fun DrawScope.drawMysteryBox(c: Offset, r: Float, tiltDegrees: Float, dark: Color, light: Color) {
    rotate(tiltDegrees, pivot = c) {
        val top = Path().apply {
            moveTo(c.x - r, c.y - r * 0.5f)
            lineTo(c.x - r * 0.6f, c.y - r * 0.95f)
            lineTo(c.x + r * 1.0f, c.y - r * 0.95f)
            lineTo(c.x + r * 0.6f, c.y - r * 0.5f)
            close()
        }
        val side = Path().apply {
            moveTo(c.x + r * 0.6f, c.y - r * 0.5f)
            lineTo(c.x + r * 1.0f, c.y - r * 0.95f)
            lineTo(c.x + r * 1.0f, c.y + r * 0.55f)
            lineTo(c.x + r * 0.6f, c.y + r)
            close()
        }
        drawPath(side, dark)
        drawRect(Brush.verticalGradient(listOf(light, dark), startY = c.y - r * 0.5f, endY = c.y + r), Offset(c.x - r, c.y - r * 0.5f), Size(r * 1.6f, r * 1.5f))
        drawPath(top, Color.White.copy(alpha = 0.85f))
        drawPath(top, light.copy(alpha = 0.6f))
        // Ribbon.
        drawRect(Gold, Offset(c.x - r * 0.32f, c.y - r * 0.5f), Size(r * 0.28f, r * 1.5f))
        drawLine(Gold, Offset(c.x - r * 0.4f, c.y - r * 0.95f), Offset(c.x - r * 0.0f, c.y - r * 0.5f), r * 0.28f)
        drawCircle(Gold, r * 0.22f, Offset(c.x - r * 0.1f, c.y - r * 1.0f))
        drawCircle(Color.White.copy(alpha = 0.8f), r * 0.08f, Offset(c.x - r * 0.65f, c.y - r * 0.25f))
    }
}
