package com.vedshubham.chalkrush.ui

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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
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
import com.vedshubham.chalkrush.game.NONE
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.random.Random

/** How many cells fit across the screen. */
private const val VIEW_CELLS = 24f

@Composable
fun GameScreen(game: Game, onRoundOver: () -> Unit) {
    var frame by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    // The game loop: runs every screen frame, pauses while the app is in the background.
    LaunchedEffect(game) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var last = 0L
            while (!game.over) {
                withFrameNanos { now ->
                    if (last != 0L) game.update((now - last) / 1_000_000_000f)
                    last = now
                }
                frame++
            }
            onRoundOver()
        }
    }

    val specks = remember(game) {
        val r = Random(7)
        List(game.size * game.size / 12) { Offset(r.nextFloat() * game.size, r.nextFloat() * game.size) }
    }
    val textMeasurer = rememberTextMeasurer()
    val names = remember(game) {
        val style = TextStyle(color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        game.players.map { textMeasurer.measure(it.name, style) }
    }

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
            val cell = size.width / VIEW_CELLS
            val me = game.me
            val ox = size.width / 2 - me.x * cell
            val oy = size.height / 2 - me.y * cell
            drawBoard(game, cell, ox, oy, specks)
            for (p in game.players) {
                if (!p.alive) continue
                val center = Offset(ox + p.x * cell, oy + p.y * cell)
                if (center.x < -cell * 4 || center.x > size.width + cell * 4 ||
                    center.y < -cell * 4 || center.y > size.height + cell * 4
                ) continue
                val color = colorOf(p.id)
                drawCircle(color.edge, radius = cell * 0.62f, center = center + Offset(0f, cell * 0.18f))
                drawCircle(color.fill, radius = cell * 0.55f, center = center)
                drawCircle(Color.White.copy(alpha = 0.7f), radius = cell * 0.2f, center = center - Offset(cell * 0.15f, cell * 0.15f))
                val label = names[p.id]
                drawText(
                    label,
                    topLeft = Offset(center.x - label.size.width / 2f, center.y - cell * 0.9f - label.size.height),
                )
            }
        }
        Hud(game, tick = frame / 6)
    }
}

private fun DrawScope.drawBoard(game: Game, cell: Float, ox: Float, oy: Float, specks: List<Offset>) {
    val n = game.size
    drawRect(Concrete, topLeft = Offset(ox, oy), size = Size(n * cell, n * cell))
    for (s in specks) {
        val p = Offset(ox + s.x * cell, oy + s.y * cell)
        if (p.x in -4f..size.width + 4f && p.y in -4f..size.height + 4f) {
            drawCircle(ConcreteSpeck, radius = cell * 0.08f, center = p)
        }
    }
    for (k in 0..n step 10) {
        drawLine(ConcreteJoint, Offset(ox + k * cell, oy), Offset(ox + k * cell, oy + n * cell), strokeWidth = cell * 0.12f)
        drawLine(ConcreteJoint, Offset(ox, oy + k * cell), Offset(ox + n * cell, oy + k * cell), strokeWidth = cell * 0.12f)
    }

    val x0 = floor(-ox / cell).toInt().coerceAtLeast(0)
    val x1 = ceil((size.width - ox) / cell).toInt().coerceAtMost(n - 1)
    val y0 = floor(-oy / cell).toInt().coerceAtLeast(0)
    val y1 = ceil((size.height - oy) / cell).toInt().coerceAtMost(n - 1)
    if (x0 > x1 || y0 > y1) return

    // Land: a darker edge underneath, then the chalk fill on top, drawn in row runs.
    for (pass in 0..1) {
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
                val color = colorOf(o)
                val top = oy + y * cell + if (pass == 0) cell * 0.25f else 0f
                drawRect(
                    if (pass == 0) color.edge else color.fill,
                    topLeft = Offset(ox + x * cell, top),
                    size = Size((end - x + 1) * cell + 0.5f, cell + 0.5f),
                )
                x = end + 1
            }
        }
    }

    // Chalk lines still being drawn.
    for (y in y0..y1) for (x in x0..x1) {
        val t = game.trailOwner[y * n + x]
        if (t == NONE) continue
        drawRect(
            colorOf(t).fill.copy(alpha = 0.75f),
            topLeft = Offset(ox + x * cell + cell * 0.15f, oy + y * cell + cell * 0.15f),
            size = Size(cell * 0.7f, cell * 0.7f),
        )
    }
}

@Composable
private fun Hud(game: Game, tick: Int) {
    tick // refresh about 10 times a second
    val pill = Modifier
        .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
        .padding(horizontal = 10.dp, vertical = 6.dp)
    Box(Modifier.fillMaxSize().safeDrawingPadding().padding(12.dp)) {
        val left = ceil(game.timeLeft).toInt()
        Text(
            "%d:%02d".format(left / 60, left % 60),
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = pill.align(Alignment.TopStart),
        )

        Column(pill.align(Alignment.TopEnd).width(150.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val ranking = game.ranking()
            val shown = ranking.take(5).toMutableList()
            if (game.me !in shown) shown[shown.lastIndex] = game.me
            for (p in shown) {
                val place = ranking.indexOf(p) + 1
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(colorOf(p.id).fill, CircleShape))
                    Text(
                        "  $place. ${if (p.isBot) "🤖 " else ""}${p.name}",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = if (p.isBot) FontWeight.Normal else FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    Text("%.1f%%".format(game.percent(p)), color = Color.White, fontSize = 13.sp)
                }
            }
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

        if (game.elapsed < 5f) {
            Text(
                "Swipe to steer.\nLeave your land, draw a loop, and come back to claim it.\nDon't let anyone touch your line!",
                color = Color.White,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 64.dp).then(pill),
            )
        }

        Text(
            "You  %.1f%%".format(game.percent(game.me)),
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Black,
            modifier = pill.align(Alignment.BottomCenter),
        )
    }
}
