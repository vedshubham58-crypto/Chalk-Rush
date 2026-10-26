package com.vedshubham.chalkrush.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import com.vedshubham.chalkrush.game.Game
import com.vedshubham.chalkrush.game.NONE
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/** How high things stand, in cells. */
const val LAND_HEIGHT = 0.28f
const val WALL_HEIGHT = 0.9f
private const val CLIFF_DEPTH = 2.5f
private const val WALL_HALF = 0.14f

/**
 * A camera tilted over the arena, looking north, like a player watching from
 * behind and above. World points are in cells: (x, y) on the floor, h up.
 */
class Camera3D {
    var targetX = 0f
    var targetY = 0f
    var screenX = 0f
    var screenY = 0f
    private var f = 1f
    private val distance = 40f
    private val tilt = 0.6f
    private val cosT = cos(tilt)
    private val sinT = sin(tilt)

    /** Sets up for a frame: [cellPx] is how wide one cell looks at the target. */
    fun frame(width: Float, height: Float, x: Float, y: Float, cellPx: Float, shakeX: Float, shakeY: Float) {
        targetX = x
        targetY = y
        screenX = width / 2 + shakeX
        screenY = height * 0.56f + shakeY
        f = cellPx * distance
    }

    private fun depth(dy: Float, h: Float) = (distance - dy * sinT - h * cosT).coerceAtLeast(1f)

    fun project(x: Float, y: Float, h: Float = 0f): Offset {
        val dy = y - targetY
        val z = depth(dy, h)
        return Offset(screenX + f * (x - targetX) / z, screenY + f * (dy * cosT - h * sinT) / z)
    }

    /** Pixels per cell for something standing at (x, y, h). */
    fun scaleAt(x: Float, y: Float, h: Float = 0f): Float = f / depth(y - targetY, h)

    /** Which floor rows reach the screen, far to near. */
    fun visibleRows(height: Float): IntRange {
        fun rowAt(sy: Float): Float {
            val t = (sy - screenY) / f
            val denom = (cosT + t * sinT).coerceAtLeast(0.05f)
            return targetY + t * distance / denom
        }
        val far = rowAt(0f).coerceAtLeast(targetY - 60f)
        val near = rowAt(height)
        return floor(far).toInt() - 1..ceil(near).toInt() + 1
    }

    /** Which columns of row [y] reach the screen. */
    fun visibleCols(y: Float, width: Float): IntRange {
        val z = depth(y - targetY, 0f)
        val half = (width / 2 + 40f) * z / f
        return floor(targetX - half).toInt() - 1..ceil(targetX + half).toInt() + 1
    }

    /** Where the camera's eye is, on the floor plan, for working out which faces it can see. */
    val eyeY: Float get() = targetY + distance * sinT
}

private val quad = Path()

private fun DrawScope.quad(a: Offset, b: Offset, c: Offset, d: Offset, color: Color) {
    quad.reset()
    quad.moveTo(a.x, a.y)
    quad.lineTo(b.x, b.y)
    quad.lineTo(c.x, c.y)
    quad.lineTo(d.x, d.y)
    quad.close()
    drawPath(quad, color)
}

/**
 * Draws the arena in 3D, far rows first so nearer things cover them: the
 * land floating in the dark with glowing cliffs, raised land, and standing
 * light walls for trails. [onRow] draws whatever stands in that row (bikes,
 * power-ups) at the right moment.
 */
fun DrawScope.drawWorld3D(game: Game, cam: Camera3D, theme: MapTheme, clock: Float, onRow: DrawScope.(Int) -> Unit) {
    val n = game.size
    val rows = cam.visibleRows(size.height)
    val pulse = 0.65f + 0.25f * sin(clock * 3f)
    val cliff = lerp(theme.night, theme.accent, 0.35f)
    val cliffDark = lerp(Color.Black, theme.accent, 0.12f)
    val edge = theme.accent.copy(alpha = pulse)
    val gridColor = theme.grid

    // Trail segments by row, so walls draw in depth order with everything else.
    val segments = HashMap<Int, ArrayList<FloatArray>>()
    for (p in game.players) {
        if (!p.alive || p.trail.isEmpty()) continue
        var px = Float.NaN
        var py = 0f
        for (i in p.trail) {
            val x = i % n + 0.5f
            val y = i / n + 0.5f
            if (!px.isNaN()) segments.getOrPut(floor(maxOf(py, y)).toInt()) { ArrayList() }.add(floatArrayOf(px, py, x, y, p.character.id.toFloat()))
            px = x
            py = y
        }
        segments.getOrPut(floor(maxOf(py, p.y)).toInt()) { ArrayList() }.add(floatArrayOf(px, py, p.x, p.y, p.character.id.toFloat()))
    }

    for (y in rows.first..rows.last) {
        if (y in 0 until n) {
            val cols = cam.visibleCols(y + 0.5f, size.width)
            val x0 = cols.first.coerceAtLeast(0)
            val x1 = cols.last.coerceAtMost(n - 1)
            if (x0 <= x1) drawRow(game, cam, y, x0, x1, theme, cliff, cliffDark, edge, gridColor)
        }
        segments[y]?.forEach { drawWall(cam, it) }
        onRow(y)
    }
}

private fun DrawScope.drawRow(
    game: Game, cam: Camera3D, y: Int, x0: Int, x1: Int, theme: MapTheme,
    cliff: Color, cliffDark: Color, edge: Color, gridColor: Color,
) {
    val n = game.size
    val play = game.playable
    val y0f = y.toFloat()
    val y1f = y + 1f

    // Floor, and the cliff below its southern edge where the land ends.
    var x = x0
    while (x <= x1) {
        if (!play[y * n + x]) {
            x++
            continue
        }
        var end = x
        while (end + 1 <= x1 && play[y * n + end + 1]) end++
        val l = x.toFloat()
        val r = end + 1f
        quad(cam.project(l, y0f), cam.project(r, y0f), cam.project(r, y1f), cam.project(l, y1f), theme.night)
        x = end + 1
    }
    // Grid lines every 4 cells.
    if (y % 4 == 0) {
        x = x0
        while (x <= x1) {
            if (!play[y * n + x]) {
                x++
                continue
            }
            var end = x
            while (end + 1 <= x1 && play[y * n + end + 1]) end++
            drawLine(gridColor, cam.project(x.toFloat(), y0f), cam.project(end + 1f, y0f), cam.scaleAt(x.toFloat(), y0f) * 0.06f)
            x = end + 1
        }
    }
    for (gx in (x0 / 4) * 4..x1 + 1 step 4) {
        if (gx >= n || !play[y * n + gx]) continue
        drawLine(gridColor, cam.project(gx.toFloat(), y0f), cam.project(gx.toFloat(), y1f), cam.scaleAt(gx.toFloat(), y0f) * 0.06f)
    }

    for (cx in x0..x1) {
        val i = y * n + cx
        if (!play[i]) continue
        val l = cx.toFloat()
        val r = cx + 1f
        // Cliff faces where the land ends, facing the camera.
        if (y == n - 1 || !play[i + n]) {
            val a = cam.project(l, y1f)
            val b = cam.project(r, y1f)
            drawPath(
                Path().apply {
                    moveTo(a.x, a.y); lineTo(b.x, b.y)
                    val c = cam.project(r, y1f, -CLIFF_DEPTH); lineTo(c.x, c.y)
                    val d = cam.project(l, y1f, -CLIFF_DEPTH); lineTo(d.x, d.y)
                    close()
                },
                Brush.verticalGradient(listOf(cliff, cliffDark), startY = a.y, endY = cam.project(l, y1f, -CLIFF_DEPTH).y),
            )
            drawLine(edge, a, b, cam.scaleAt(l, y1f) * 0.12f, StrokeCap.Round)
        }
        if (y == 0 || !play[i - n]) drawLine(edge, cam.project(l, y0f), cam.project(r, y0f), cam.scaleAt(l, y0f) * 0.12f, StrokeCap.Round)
        if (cx == 0 || !play[i - 1]) {
            if (l > cam.targetX) quad(cam.project(l, y0f), cam.project(l, y1f), cam.project(l, y1f, -CLIFF_DEPTH), cam.project(l, y0f, -CLIFF_DEPTH), cliffDark)
            drawLine(edge, cam.project(l, y0f), cam.project(l, y1f), cam.scaleAt(l, y0f) * 0.12f)
        }
        if (cx == n - 1 || !play[i + 1]) {
            if (r < cam.targetX) quad(cam.project(r, y0f), cam.project(r, y1f), cam.project(r, y1f, -CLIFF_DEPTH), cam.project(r, y0f, -CLIFF_DEPTH), cliffDark)
            drawLine(edge, cam.project(r, y0f), cam.project(r, y1f), cam.scaleAt(r, y0f) * 0.12f)
        }
    }

    // Raised land: tops in runs, then the faces the camera can see, then a bright rim.
    x = x0
    while (x <= x1) {
        val o = game.owner[y * n + x]
        if (o == NONE) {
            x++
            continue
        }
        var end = x
        while (end + 1 <= x1 && game.owner[y * n + end + 1] == o) end++
        val color = colorOf(game.players[o].character.id)
        val l = x.toFloat()
        val r = end + 1f
        val top = lerp(theme.night, color.fill, 0.42f)
        // Front faces under the southern edge.
        var k = x
        while (k <= end) {
            if (y < n - 1 && game.owner[(y + 1) * n + k] == o) {
                k++
                continue
            }
            var e = k
            while (e + 1 <= end && !(y < n - 1 && game.owner[(y + 1) * n + e + 1] == o)) e++
            quad(cam.project(k.toFloat(), y1f), cam.project(e + 1f, y1f), cam.project(e + 1f, y1f, LAND_HEIGHT), cam.project(k.toFloat(), y1f, LAND_HEIGHT), color.edge)
            k = e + 1
        }
        if (l > cam.targetX && (x == 0 || game.owner[y * n + x - 1] != o)) {
            quad(cam.project(l, y0f), cam.project(l, y1f), cam.project(l, y1f, LAND_HEIGHT), cam.project(l, y0f, LAND_HEIGHT), lerp(color.edge, Color.Black, 0.3f))
        }
        if (r < cam.targetX && (end == n - 1 || game.owner[y * n + end + 1] != o)) {
            quad(cam.project(r, y0f), cam.project(r, y1f), cam.project(r, y1f, LAND_HEIGHT), cam.project(r, y0f, LAND_HEIGHT), lerp(color.edge, Color.Black, 0.3f))
        }
        quad(cam.project(l, y0f, LAND_HEIGHT), cam.project(r, y0f, LAND_HEIGHT), cam.project(r, y1f, LAND_HEIGHT), cam.project(l, y1f, LAND_HEIGHT), top)
        // Rim lines along the land's outline.
        val rim = color.fill
        val w = cam.scaleAt(l, y0f) * 0.1f
        for (c in x..end) {
            val cl = c.toFloat()
            if (y == 0 || game.owner[(y - 1) * n + c] != o) drawLine(rim, cam.project(cl, y0f, LAND_HEIGHT), cam.project(cl + 1f, y0f, LAND_HEIGHT), w)
            if (y == n - 1 || game.owner[(y + 1) * n + c] != o) drawLine(rim, cam.project(cl, y1f, LAND_HEIGHT), cam.project(cl + 1f, y1f, LAND_HEIGHT), w)
        }
        if (x == 0 || game.owner[y * n + x - 1] != o) drawLine(rim, cam.project(l, y0f, LAND_HEIGHT), cam.project(l, y1f, LAND_HEIGHT), w)
        if (end == n - 1 || game.owner[y * n + end + 1] != o) drawLine(rim, cam.project(r, y0f, LAND_HEIGHT), cam.project(r, y1f, LAND_HEIGHT), w)
        x = end + 1
    }
}

/** One straight piece of light wall: a thin glowing box from (x1, y1) to (x2, y2). */
private fun DrawScope.drawWall(cam: Camera3D, s: FloatArray) {
    val color = colorOf(s[4].toInt())
    val ax = minOf(s[0], s[2]) - WALL_HALF
    val bx = maxOf(s[0], s[2]) + WALL_HALF
    val ay = minOf(s[1], s[3]) - WALL_HALF
    val by = maxOf(s[1], s[3]) + WALL_HALF
    val h = WALL_HEIGHT
    val face = color.fill.copy(alpha = 0.75f)
    // Glow on the floor.
    quad(cam.project(ax - 0.25f, ay - 0.25f), cam.project(bx + 0.25f, ay - 0.25f), cam.project(bx + 0.25f, by + 0.25f), cam.project(ax - 0.25f, by + 0.25f), color.fill.copy(alpha = 0.18f))
    // Side facing the camera.
    if (bx < cam.targetX) quad(cam.project(bx, ay), cam.project(bx, by), cam.project(bx, by, h), cam.project(bx, ay, h), color.edge)
    if (ax > cam.targetX) quad(cam.project(ax, ay), cam.project(ax, by), cam.project(ax, by, h), cam.project(ax, ay, h), color.edge)
    // Front, then a white-hot top.
    quad(cam.project(ax, by), cam.project(bx, by), cam.project(bx, by, h), cam.project(ax, by, h), face)
    quad(cam.project(ax, ay, h), cam.project(bx, ay, h), cam.project(bx, by, h), cam.project(ax, by, h), Color.White.copy(alpha = 0.9f))
    drawLine(color.fill, cam.project(ax, by, h), cam.project(bx, by, h), cam.scaleAt(ax, by, h) * 0.08f)
}

/**
 * A light bike as a solid 3D block, with a rider sitting up on top. [angle]
 * is the heading on the floor; the rider is a flat picture that always faces you.
 */
fun DrawScope.drawBike3D(cam: Camera3D, x: Float, y: Float, angle: Float, characterId: Int, mood: Mood, clock: Float, boost: Boolean) {
    val color = colorOf(characterId)
    val fx = cos(angle)
    val fy = sin(angle)
    val len = 0.85f
    val wid = 0.3f
    val h = 0.5f
    val lift = 0.08f
    // Corners: front-left, front-right, back-right, back-left.
    val cx = floatArrayOf(x + fx * len - fy * wid, x + fx * len + fy * wid, x - fx * len + fy * wid, x - fx * len - fy * wid)
    val cy = floatArrayOf(y + fy * len + fx * wid, y + fy * len - fx * wid, y - fy * len - fx * wid, y - fy * len + fx * wid)

    // Shadow and under-glow.
    val s = cam.scaleAt(x, y)
    val ground = cam.project(x, y)
    drawOval(Color.Black.copy(alpha = 0.45f), ground - Offset(s * 1.1f, s * 0.45f), androidx.compose.ui.geometry.Size(s * 2.2f, s * 0.9f))
    drawCircle(color.fill.copy(alpha = if (boost) 0.45f else 0.25f), s * 1.3f, ground)

    // Sides facing the camera, sorted far to near.
    val order = (0 until 4).sortedBy { k -> (cy[k] + cy[(k + 1) % 4]) }
    for (k in order) {
        val k2 = (k + 1) % 4
        val mx = (cx[k] + cx[k2]) / 2 - x
        val my = (cy[k] + cy[k2]) / 2 - y
        // A side faces the camera if it points south, or toward the camera's column.
        val toEyeX = cam.targetX - (x + mx)
        val toEyeY = cam.eyeY - (y + my)
        if (mx * toEyeX + my * toEyeY <= 0f) continue
        val shade = if (k == 0) lerp(color.fill, Color.White, 0.3f) else color.edge
        quad(cam.project(cx[k], cy[k], lift), cam.project(cx[k2], cy[k2], lift), cam.project(cx[k2], cy[k2], h), cam.project(cx[k], cy[k], h), shade)
    }
    // Glossy top with a neon rim and stripe.
    val t0 = cam.project(cx[0], cy[0], h)
    val t1 = cam.project(cx[1], cy[1], h)
    val t2 = cam.project(cx[2], cy[2], h)
    val t3 = cam.project(cx[3], cy[3], h)
    quad(t0, t1, t2, t3, Color(0xFF151A3A))
    val rimW = s * 0.07f
    drawLine(color.fill, t0, t1, rimW)
    drawLine(color.fill, t1, t2, rimW)
    drawLine(color.fill, t2, t3, rimW)
    drawLine(color.fill, t3, t0, rimW)
    drawLine(color.fill, cam.project(x + fx * len * 0.8f, y + fy * len * 0.8f, h), cam.project(x - fx * len * 0.8f, y - fy * len * 0.8f, h), rimW)
    // Headlight and its beam on the floor, tail light behind.
    val head = cam.project(x + fx * len, y + fy * len, h * 0.6f)
    drawCircle(Color.White.copy(alpha = 0.35f), s * 0.35f, head)
    drawCircle(Color.White, s * 0.14f, head)
    drawCircle(Color(0xFFFF2B4A), s * 0.1f, cam.project(x - fx * len, y - fy * len, h * 0.6f))

    // The rider, sitting up on the bike and bobbing with the ride.
    val bob = sin(clock * 9f + characterId) * 0.04f
    val seat = cam.project(x - fx * 0.1f, y - fy * 0.1f, h + 0.55f + bob)
    drawCharacter(characterId, seat, cam.scaleAt(x, y, h) * 0.62f, angle, mood, clock)
}

/** A ring on the floor, for capture and knockout shock waves. */
fun DrawScope.drawFloorRing(cam: Camera3D, x: Float, y: Float, radius: Float, color: Color, width: Float) {
    val path = Path()
    for (k in 0..24) {
        val a = k / 24f * 6.2832f
        val p = cam.project(x + cos(a) * radius, y + sin(a) * radius, 0.05f)
        if (k == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
    }
    drawPath(path, color, style = androidx.compose.ui.graphics.drawscope.Stroke(width))
}
