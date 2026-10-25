package com.vedshubham.chalkrush.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.cos
import kotlin.math.sin

enum class Mood { NORMAL, HAPPY, SCARED, KNOCKED_OUT }

private val Ink = Color(0xFF2B2B2B)
private val Cheek = Color(0x66FF6F91)
private val Gold = Color(0xFFFFC83D)
private val GoldEdge = Color(0xFFC8901A)

/**
 * Draws one of the chalk characters: a round body with a face that looks the
 * way it's heading, blinks, and changes expression. [time] is in seconds.
 */
fun DrawScope.drawCharacter(
    characterId: Int,
    center: Offset,
    radius: Float,
    lookAngle: Float,
    mood: Mood,
    time: Float,
) {
    val color = colorOf(characterId)
    val r = radius

    // Shadow, body and a chalky highlight.
    drawCircle(Color.Black.copy(alpha = 0.22f), r * 1.02f, center + Offset(0f, r * 0.28f))
    drawCircle(color.edge, r, center + Offset(0f, r * 0.1f))
    drawCircle(color.fill, r * 0.94f, center)
    drawCircle(Color.White.copy(alpha = 0.35f), r * 0.32f, center + Offset(-r * 0.38f, -r * 0.42f))

    drawAccessory(characterId, center, r, color)

    // Eyes look where the character is heading.
    val look = Offset(cos(lookAngle), sin(lookAngle))
    val eyeY = center.y - r * 0.12f
    val eyeGap = r * 0.36f
    val blinking = mood != Mood.KNOCKED_OUT && ((time + characterId * 0.7f) % 3.2f) < 0.12f
    for (side in intArrayOf(-1, 1)) {
        val eye = Offset(center.x + side * eyeGap, eyeY) + look * (r * 0.1f)
        when {
            mood == Mood.KNOCKED_OUT -> {
                val d = r * 0.14f
                drawLine(Ink, eye + Offset(-d, -d), eye + Offset(d, d), r * 0.09f, StrokeCap.Round)
                drawLine(Ink, eye + Offset(-d, d), eye + Offset(d, -d), r * 0.09f, StrokeCap.Round)
            }
            blinking -> drawLine(Ink, eye + Offset(-r * 0.14f, 0f), eye + Offset(r * 0.14f, 0f), r * 0.08f, StrokeCap.Round)
            else -> {
                val eyeR = if (mood == Mood.SCARED) r * 0.24f else r * 0.21f
                drawCircle(Color.White, eyeR, eye)
                drawCircle(Ink, eyeR * 0.55f, eye + look * (eyeR * 0.4f))
                drawCircle(Color.White, eyeR * 0.18f, eye + look * (eyeR * 0.4f) + Offset(-eyeR * 0.2f, -eyeR * 0.2f))
            }
        }
    }
    if (characterId == 3 && mood != Mood.KNOCKED_OUT) {
        // Rocky's bushy eyebrows.
        for (side in intArrayOf(-1, 1)) {
            val b = Offset(center.x + side * eyeGap, eyeY - r * 0.33f)
            drawLine(Ink, b + Offset(-r * 0.17f, -side * r * 0.04f), b + Offset(r * 0.17f, side * r * 0.04f), r * 0.11f, StrokeCap.Round)
        }
    }

    // Cheeks and mouth.
    drawCircle(Cheek, r * 0.12f, Offset(center.x - r * 0.6f, center.y + r * 0.16f))
    drawCircle(Cheek, r * 0.12f, Offset(center.x + r * 0.6f, center.y + r * 0.16f))
    val mouth = Offset(center.x, center.y + r * 0.32f)
    when (mood) {
        Mood.HAPPY -> {
            val p = Path().apply {
                moveTo(mouth.x - r * 0.28f, mouth.y - r * 0.06f)
                quadraticTo(mouth.x, mouth.y + r * 0.42f, mouth.x + r * 0.28f, mouth.y - r * 0.06f)
                close()
            }
            drawPath(p, Ink)
            drawCircle(Color(0xFFFF7A8A), r * 0.09f, Offset(mouth.x, mouth.y + r * 0.12f))
        }
        Mood.SCARED -> drawCircle(Ink, r * 0.11f, mouth + Offset(0f, r * 0.04f))
        Mood.KNOCKED_OUT -> drawLine(Ink, mouth + Offset(-r * 0.18f, 0f), mouth + Offset(r * 0.18f, 0f), r * 0.07f, StrokeCap.Round)
        Mood.NORMAL -> {
            val p = Path().apply {
                moveTo(mouth.x - r * 0.22f, mouth.y - r * 0.02f)
                quadraticTo(mouth.x, mouth.y + r * 0.2f, mouth.x + r * 0.22f, mouth.y - r * 0.02f)
            }
            drawPath(p, Ink, style = Stroke(width = r * 0.08f, cap = StrokeCap.Round))
        }
    }
}

private fun DrawScope.drawAccessory(id: Int, c: Offset, r: Float, color: ChalkColor) {
    val top = Offset(c.x, c.y - r * 0.92f)
    when (id) {
        1 -> { // Pip: a bow
            val left = Path().apply {
                moveTo(top.x, top.y); lineTo(top.x - r * 0.5f, top.y - r * 0.3f); lineTo(top.x - r * 0.5f, top.y + r * 0.22f); close()
            }
            val right = Path().apply {
                moveTo(top.x, top.y); lineTo(top.x + r * 0.5f, top.y - r * 0.3f); lineTo(top.x + r * 0.5f, top.y + r * 0.22f); close()
            }
            drawPath(left, Color(0xFFE5397A))
            drawPath(right, Color(0xFFE5397A))
            drawCircle(Color(0xFFB0185A), r * 0.13f, top)
        }
        2 -> { // Zippy: a lightning bolt quiff
            val bolt = Path().apply {
                moveTo(top.x - r * 0.05f, top.y + r * 0.1f)
                lineTo(top.x + r * 0.25f, top.y - r * 0.35f)
                lineTo(top.x + r * 0.05f, top.y - r * 0.32f)
                lineTo(top.x + r * 0.3f, top.y - r * 0.75f)
                lineTo(top.x - r * 0.2f, top.y - r * 0.2f)
                lineTo(top.x, top.y - r * 0.22f)
                close()
            }
            drawPath(bolt, Color(0xFFFFF3B0))
            drawPath(bolt, color.edge, style = Stroke(r * 0.05f))
        }
        4 -> { // Blaze: a flame tuft
            val flame = Path().apply {
                moveTo(top.x - r * 0.35f, top.y + r * 0.12f)
                quadraticTo(top.x - r * 0.4f, top.y - r * 0.4f, top.x - r * 0.08f, top.y - r * 0.7f)
                quadraticTo(top.x - r * 0.05f, top.y - r * 0.3f, top.x + r * 0.1f, top.y - r * 0.35f)
                quadraticTo(top.x + r * 0.2f, top.y - r * 0.6f, top.x + r * 0.3f, top.y - r * 0.5f)
                quadraticTo(top.x + r * 0.45f, top.y - r * 0.15f, top.x + r * 0.35f, top.y + r * 0.12f)
                close()
            }
            drawPath(flame, Color(0xFFFF5A1F))
            drawCircle(Color(0xFFFFD34D), r * 0.14f, top + Offset(0f, -r * 0.08f))
        }
        5 -> { // Luna: a star on an antenna
            drawLine(color.edge, top, top + Offset(r * 0.15f, -r * 0.45f), r * 0.07f, StrokeCap.Round)
            drawStar(top + Offset(r * 0.18f, -r * 0.6f), r * 0.26f, Gold)
        }
        6 -> { // Frost: ice spikes
            for (k in -1..1) {
                val base = top + Offset(k * r * 0.32f, r * 0.08f)
                val spike = Path().apply {
                    moveTo(base.x - r * 0.15f, base.y)
                    lineTo(base.x, base.y - r * (if (k == 0) 0.55f else 0.38f))
                    lineTo(base.x + r * 0.15f, base.y)
                    close()
                }
                drawPath(spike, Color(0xFFE3FBFF))
                drawPath(spike, color.edge, style = Stroke(r * 0.04f))
            }
        }
        7 -> { // King Crayon: a crown
            val crown = Path().apply {
                moveTo(top.x - r * 0.45f, top.y + r * 0.1f)
                lineTo(top.x - r * 0.5f, top.y - r * 0.4f)
                lineTo(top.x - r * 0.22f, top.y - r * 0.15f)
                lineTo(top.x, top.y - r * 0.5f)
                lineTo(top.x + r * 0.22f, top.y - r * 0.15f)
                lineTo(top.x + r * 0.5f, top.y - r * 0.4f)
                lineTo(top.x + r * 0.45f, top.y + r * 0.1f)
                close()
            }
            drawPath(crown, Gold)
            drawPath(crown, GoldEdge, style = Stroke(r * 0.05f))
            drawCircle(Color(0xFF4FC3F7), r * 0.07f, top + Offset(0f, -r * 0.08f))
        }
        else -> Unit // Chalky and Rocky keep it simple
    }
}

fun DrawScope.drawStar(c: Offset, r: Float, color: Color) {
    val p = Path()
    for (k in 0 until 10) {
        val a = -Math.PI / 2 + k * Math.PI / 5
        val rr = if (k % 2 == 0) r else r * 0.45f
        val pt = Offset(c.x + (cos(a) * rr).toFloat(), c.y + (sin(a) * rr).toFloat())
        if (k == 0) p.moveTo(pt.x, pt.y) else p.lineTo(pt.x, pt.y)
    }
    p.close()
    drawPath(p, color)
}

/** A character on its own, for menus. */
@Composable
fun CharacterPortrait(characterId: Int, modifier: Modifier = Modifier, mood: Mood = Mood.HAPPY, time: Float = 0f) {
    Canvas(modifier) {
        val r = size.minDimension * 0.32f
        drawCharacter(characterId, Offset(size.width / 2, size.height * 0.56f), r, (Math.PI / 2).toFloat(), mood, time)
    }
}

/**
 * A rider on a glowing bike, seen from above. The bike turns with [angle];
 * the rider's face stays upright and looks the way they're heading.
 */
fun DrawScope.drawBike(
    characterId: Int,
    center: Offset,
    size: Float,
    angle: Float,
    mood: Mood,
    time: Float,
) {
    val color = colorOf(characterId)
    val len = size * 2.3f
    val wid = size * 0.75f
    rotate(Math.toDegrees(angle.toDouble()).toFloat(), pivot = center) {
        // Headlight beam.
        val beam = Path().apply {
            moveTo(center.x + len * 0.45f, center.y - wid * 0.2f)
            lineTo(center.x + len * 1.3f, center.y - wid * 0.9f)
            lineTo(center.x + len * 1.3f, center.y + wid * 0.9f)
            lineTo(center.x + len * 0.45f, center.y + wid * 0.2f)
            close()
        }
        drawPath(beam, color.fill.copy(alpha = 0.12f))
        // Glow, body, and a bright outline.
        val topLeft = Offset(center.x - len / 2, center.y - wid / 2)
        drawRoundRect(color.fill.copy(alpha = 0.25f), topLeft - Offset(size * 0.25f, size * 0.25f), Size(len + size * 0.5f, wid + size * 0.5f), CornerRadius(wid))
        drawRoundRect(Color(0xFF0A0D1F), topLeft, Size(len, wid), CornerRadius(wid / 2))
        drawRoundRect(color.fill, topLeft, Size(len, wid), CornerRadius(wid / 2), style = Stroke(size * 0.14f))
        // Wheels.
        for (k in intArrayOf(-1, 1)) {
            val w = Offset(center.x + k * len * 0.36f, center.y)
            drawRoundRect(color.fill, w - Offset(size * 0.32f, wid * 0.42f), Size(size * 0.64f, wid * 0.84f), CornerRadius(size * 0.2f))
            drawRoundRect(Color.White.copy(alpha = 0.8f), w - Offset(size * 0.16f, wid * 0.2f), Size(size * 0.32f, wid * 0.4f), CornerRadius(size * 0.1f))
        }
        // Headlight.
        drawCircle(Color.White, size * 0.13f, Offset(center.x + len * 0.5f, center.y))
    }
    drawCharacter(characterId, center, size * 0.62f, angle, mood, time)
}

/** A rider on their bike, for menus. */
@Composable
fun BikePortrait(characterId: Int, modifier: Modifier = Modifier, mood: Mood = Mood.HAPPY, time: Float = 0f) {
    Canvas(modifier) {
        val s = size.minDimension * 0.2f
        drawBike(characterId, Offset(size.width / 2, size.height * 0.55f), s, -0.35f + kotlin.math.sin(time * 1.5f) * 0.08f, mood, time)
    }
}
