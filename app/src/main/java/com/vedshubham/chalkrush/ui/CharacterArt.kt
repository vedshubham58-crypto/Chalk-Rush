package com.vedshubham.chalkrush.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.cos
import kotlin.math.sin

enum class Mood { NORMAL, HAPPY, SCARED, KNOCKED_OUT }

private val Gold = Color(0xFFFFC83D)
private val GoldEdge = Color(0xFFC8901A)

private val Skin = Color(0xFFFFE3D1)
private val SkinShade = Color(0xFFF2B9A0)
private val Lash = Color(0xFF1B1530)

/**
 * Draws one of the riders as an anime-style head: big shiny eyes that look
 * the way they're heading, a hairstyle in their neon colour, a cyber earpiece,
 * blinking and expressions. [time] is in seconds.
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
    val hair = color.fill
    val hairDark = lerp(color.edge, Color.Black, 0.25f)

    // Shadow, then hair behind the head.
    drawOval(Color.Black.copy(alpha = 0.25f), Offset(center.x - r * 0.9f, center.y + r * 0.75f), Size(r * 1.8f, r * 0.55f))
    drawBackHair(characterId, center, r, hairDark, time)

    // Face: a soft oval that narrows to the chin.
    val face = Path().apply {
        moveTo(center.x - r * 0.82f, center.y - r * 0.2f)
        cubicTo(center.x - r * 0.85f, center.y + r * 0.45f, center.x - r * 0.35f, center.y + r * 0.9f, center.x, center.y + r * 0.95f)
        cubicTo(center.x + r * 0.35f, center.y + r * 0.9f, center.x + r * 0.85f, center.y + r * 0.45f, center.x + r * 0.82f, center.y - r * 0.2f)
        cubicTo(center.x + r * 0.8f, center.y - r * 1.05f, center.x - r * 0.8f, center.y - r * 1.05f, center.x - r * 0.82f, center.y - r * 0.2f)
        close()
    }
    drawPath(face, Brush.radialGradient(listOf(Skin, Skin, SkinShade), center = center + Offset(-r * 0.2f, -r * 0.2f), radius = r * 1.3f))

    // Eyes look where the rider is heading.
    val look = Offset(cos(lookAngle), sin(lookAngle)) * (r * 0.06f)
    val eyeY = center.y + r * 0.12f
    val eyeGap = r * 0.38f
    val blinking = mood != Mood.KNOCKED_OUT && ((time + characterId * 0.7f) % 3.4f) < 0.12f
    for (side in intArrayOf(-1, 1)) {
        val eye = Offset(center.x + side * eyeGap, eyeY) + look
        when {
            mood == Mood.KNOCKED_OUT -> {
                // Dizzy swirls.
                for (k in 0..1) drawCircle(Lash, r * (0.17f - k * 0.08f), eye, style = Stroke(r * 0.05f))
            }
            blinking || mood == Mood.HAPPY -> {
                // Closed happy arcs.
                val arc = Path().apply {
                    moveTo(eye.x - r * 0.18f, eye.y + r * 0.03f)
                    quadraticTo(eye.x, eye.y - (if (blinking) 0f else r * 0.16f), eye.x + r * 0.18f, eye.y + r * 0.03f)
                }
                drawPath(arc, Lash, style = Stroke(r * 0.07f, cap = StrokeCap.Round))
            }
            else -> {
                val w = r * 0.25f
                val h = if (mood == Mood.SCARED) r * 0.42f else r * 0.36f
                val top = eye.y - h * 0.55f
                drawOval(Color.White, Offset(eye.x - w / 2, top), Size(w, h))
                // Iris in the rider's colour, darker at the top.
                drawOval(
                    Brush.verticalGradient(listOf(color.edge, color.fill, lerp(color.fill, Color.White, 0.4f)), startY = top, endY = top + h),
                    Offset(eye.x - w * 0.42f, top + h * 0.08f),
                    Size(w * 0.84f, h * 0.88f),
                )
                drawOval(Lash, Offset(eye.x - w * 0.2f, top + h * 0.3f), Size(w * 0.4f, h * 0.45f))
                // Two sparkles.
                drawCircle(Color.White, w * 0.17f, Offset(eye.x - w * 0.14f, top + h * 0.3f))
                drawCircle(Color.White, w * 0.08f, Offset(eye.x + w * 0.14f, top + h * 0.68f))
                // Thick upper lash with a flick at the outer corner.
                val lash = Path().apply {
                    moveTo(eye.x - side * w * 0.65f, top + h * 0.12f)
                    quadraticTo(eye.x, top - h * 0.12f, eye.x + side * w * 0.7f, top + h * 0.05f)
                    lineTo(eye.x + side * w * 0.85f, top - h * 0.08f)
                }
                drawPath(lash, Lash, style = Stroke(r * 0.07f, cap = StrokeCap.Round))
            }
        }
        // Eyebrow.
        val browY = eyeY - r * (if (mood == Mood.SCARED) 0.4f else 0.33f)
        val tilt = if (mood == Mood.SCARED) -side * r * 0.04f else side * r * 0.02f
        drawLine(hairDark, Offset(center.x + side * r * 0.24f, browY + tilt), Offset(center.x + side * r * 0.5f, browY - tilt), r * 0.05f, StrokeCap.Round)
    }

    // Blush and mouth.
    drawOval(Color(0x55FF6F91), Offset(center.x - r * 0.66f, center.y + r * 0.36f), Size(r * 0.24f, r * 0.1f))
    drawOval(Color(0x55FF6F91), Offset(center.x + r * 0.42f, center.y + r * 0.36f), Size(r * 0.24f, r * 0.1f))
    val mouth = Offset(center.x, center.y + r * 0.56f)
    when (mood) {
        Mood.HAPPY -> {
            val p = Path().apply {
                moveTo(mouth.x - r * 0.15f, mouth.y - r * 0.04f)
                quadraticTo(mouth.x, mouth.y + r * 0.22f, mouth.x + r * 0.15f, mouth.y - r * 0.04f)
                close()
            }
            drawPath(p, Color(0xFF8A1E3C))
            drawOval(Color(0xFFFF8FA3), Offset(mouth.x - r * 0.07f, mouth.y + r * 0.03f), Size(r * 0.14f, r * 0.07f))
        }
        Mood.SCARED -> drawOval(Color(0xFF8A1E3C), Offset(mouth.x - r * 0.06f, mouth.y - r * 0.04f), Size(r * 0.12f, r * 0.14f))
        Mood.KNOCKED_OUT -> {
            val p = Path().apply {
                moveTo(mouth.x - r * 0.14f, mouth.y)
                quadraticTo(mouth.x - r * 0.07f, mouth.y - r * 0.06f, mouth.x, mouth.y)
                quadraticTo(mouth.x + r * 0.07f, mouth.y + r * 0.06f, mouth.x + r * 0.14f, mouth.y)
            }
            drawPath(p, Lash, style = Stroke(r * 0.04f, cap = StrokeCap.Round))
        }
        Mood.NORMAL -> {
            val p = Path().apply {
                moveTo(mouth.x - r * 0.08f, mouth.y)
                quadraticTo(mouth.x, mouth.y + r * 0.06f, mouth.x + r * 0.08f, mouth.y)
            }
            drawPath(p, Lash, style = Stroke(r * 0.04f, cap = StrokeCap.Round))
        }
    }

    drawFrontHair(characterId, center, r, hair, hairDark, time)

    // A glowing cyber earpiece.
    val ear = Offset(center.x + r * 0.84f, center.y + r * 0.05f)
    drawCircle(color.fill.copy(alpha = 0.35f), r * 0.2f, ear)
    drawRoundRect(Color(0xFF151A3A), ear - Offset(r * 0.09f, r * 0.15f), Size(r * 0.18f, r * 0.3f), CornerRadius(r * 0.08f))
    drawCircle(color.fill, r * 0.05f, ear)

    if (characterId == 1 || characterId == 5 || characterId == 7) drawAccessory(characterId, center + Offset(0f, -r * 0.15f), r, color)
}

/** Hair that hangs behind the face: tails, long hair or a messy back. */
private fun DrawScope.drawBackHair(id: Int, c: Offset, r: Float, dark: Color, time: Float) {
    val sway = sin(time * 3f + id) * r * 0.05f
    when (id) {
        1 -> for (side in intArrayOf(-1, 1)) { // Pip: twin tails
            val tail = Path().apply {
                moveTo(c.x + side * r * 0.7f, c.y - r * 0.6f)
                quadraticTo(c.x + side * r * 1.5f + sway, c.y - r * 0.2f, c.x + side * r * 1.2f + sway, c.y + r * 0.9f)
                quadraticTo(c.x + side * r * 1.0f, c.y + r * 0.2f, c.x + side * r * 0.6f, c.y - r * 0.2f)
                close()
            }
            drawPath(tail, dark)
        }
        5 -> drawPath( // Luna: long hair
            Path().apply {
                moveTo(c.x - r * 0.95f, c.y - r * 0.4f)
                quadraticTo(c.x - r * 1.15f + sway, c.y + r * 0.6f, c.x - r * 0.8f + sway, c.y + r * 1.15f)
                lineTo(c.x + r * 0.8f + sway, c.y + r * 1.15f)
                quadraticTo(c.x + r * 1.15f + sway, c.y + r * 0.6f, c.x + r * 0.95f, c.y - r * 0.4f)
                close()
            },
            dark,
        )
        else -> drawCircle(dark, r * 0.98f, c + Offset(0f, -r * 0.25f))
    }
}

/** Bangs and spikes on top of the head, different for each rider. */
private fun DrawScope.drawFrontHair(id: Int, c: Offset, r: Float, hair: Color, dark: Color, time: Float) {
    val brush = Brush.verticalGradient(listOf(lerp(hair, Color.White, 0.35f), hair, dark), startY = c.y - r * 1.3f, endY = c.y)
    val p = Path()
    val spikes = when (id) {
        0, 2, 4 -> 5 // spiky
        3 -> 3 // short
        6 -> 7 // icy
        else -> 4 // soft bangs
    }
    val height = when (id) {
        2 -> 1.55f
        4 -> 1.45f
        6 -> 1.35f
        3 -> 1.05f
        else -> 1.2f
    }
    p.moveTo(c.x - r * 0.95f, c.y - r * 0.05f)
    // Crown of the head.
    p.cubicTo(c.x - r * 1.0f, c.y - r * 1.0f, c.x - r * 0.4f, c.y - r * 1.25f, c.x, c.y - r * 1.2f)
    p.cubicTo(c.x + r * 0.4f, c.y - r * 1.25f, c.x + r * 1.0f, c.y - r * 1.0f, c.x + r * 0.95f, c.y - r * 0.05f)
    // Bangs coming down over the forehead, back from right to left.
    for (k in spikes downTo 0) {
        val t = k / spikes.toFloat()
        val bx = c.x + (t * 2f - 1f) * r * 0.9f
        val tipY = c.y - r * (if (k % 2 == 0) 0.25f else 0.42f)
        p.lineTo(bx, if (k == spikes || k == 0) c.y - r * 0.05f else c.y - r * 0.62f)
        if (k > 0) p.lineTo(bx - r * 0.9f / spikes, tipY)
    }
    p.close()
    drawPath(p, brush)
    // Spikes sticking up.
    if (id == 0 || id == 2 || id == 4 || id == 6) {
        for (k in -2..2) {
            val base = Offset(c.x + k * r * 0.32f, c.y - r * 1.0f)
            val lean = k * r * 0.12f + sin(time * 4f + k) * r * 0.02f
            val spike = Path().apply {
                moveTo(base.x - r * 0.2f, base.y + r * 0.1f)
                lineTo(base.x + lean, c.y - r * (height - 0.05f * kotlin.math.abs(k)))
                lineTo(base.x + r * 0.2f, base.y + r * 0.1f)
                close()
            }
            drawPath(spike, brush)
        }
    }
    // A shine across the hair.
    drawOval(Color.White.copy(alpha = 0.45f), Offset(c.x - r * 0.55f, c.y - r * 1.02f), Size(r * 0.5f, r * 0.12f))
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
 * A rider on a glowing bike, seen from above and a little from the front, so
 * the bike has a visible side and casts a shadow. The bike turns with [angle];
 * the rider sits up on top, bobs with the ride and looks the way they're heading.
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
    val depth = size * 0.4f
    val deg = Math.toDegrees(angle.toDouble()).toFloat()
    fun body(c: Offset) = Offset(c.x - len / 2, c.y - wid / 2)

    // Soft ground shadow.
    val shadowAt = center + Offset(size * 0.3f, depth * 1.8f)
    rotate(deg, pivot = shadowAt) {
        for (k in 0..2) {
            val g = size * 0.16f * k
            drawRoundRect(Color.Black.copy(alpha = 0.16f), body(shadowAt) - Offset(g, g), Size(len + 2 * g, wid + 2 * g), CornerRadius(wid / 2 + g))
        }
    }
    // The bike's side, stacked downward so it reads as a solid block.
    for (k in 4 downTo 1) {
        val c = center + Offset(0f, depth * k / 4f)
        rotate(deg, pivot = c) {
            drawRoundRect(lerp(color.edge, Color.Black, 0.25f * k / 4f), body(c), Size(len, wid), CornerRadius(wid / 2))
        }
    }
    rotate(deg, pivot = center) {
        // Headlight beam.
        val beam = Path().apply {
            moveTo(center.x + len * 0.45f, center.y - wid * 0.2f)
            lineTo(center.x + len * 1.4f, center.y - wid * 1.0f)
            lineTo(center.x + len * 1.4f, center.y + wid * 1.0f)
            lineTo(center.x + len * 0.45f, center.y + wid * 0.2f)
            close()
        }
        drawPath(beam, Brush.horizontalGradient(listOf(color.fill.copy(alpha = 0.3f), Color.Transparent), startX = center.x + len * 0.45f, endX = center.x + len * 1.4f))
        // Glow, a dark glossy top, and a bright neon rim.
        val topLeft = body(center)
        drawRoundRect(color.fill.copy(alpha = 0.22f), topLeft - Offset(size * 0.3f, size * 0.3f), Size(len + size * 0.6f, wid + size * 0.6f), CornerRadius(wid))
        drawRoundRect(
            Brush.verticalGradient(listOf(Color(0xFF2A3160), Color(0xFF0A0D1F)), startY = topLeft.y, endY = topLeft.y + wid),
            topLeft, Size(len, wid), CornerRadius(wid / 2),
        )
        drawRoundRect(color.fill, topLeft, Size(len, wid), CornerRadius(wid / 2), style = Stroke(size * 0.14f))
        // A glowing stripe down the middle and spinning wheels.
        drawLine(color.fill.copy(alpha = 0.8f), Offset(center.x - len * 0.3f, center.y), Offset(center.x + len * 0.3f, center.y), size * 0.08f, StrokeCap.Round)
        for (k in intArrayOf(-1, 1)) {
            val w = Offset(center.x + k * len * 0.36f, center.y)
            drawRoundRect(color.fill, w - Offset(size * 0.32f, wid * 0.42f), Size(size * 0.64f, wid * 0.84f), CornerRadius(size * 0.2f))
            val spin = (time * 14f) % 1f
            drawLine(Color.White.copy(alpha = 0.85f), w + Offset((spin - 0.5f) * size * 0.5f, -wid * 0.3f), w + Offset((spin - 0.5f) * size * 0.5f, wid * 0.3f), size * 0.1f, StrokeCap.Round)
        }
        // Headlight and tail light.
        drawCircle(Color.White.copy(alpha = 0.4f), size * 0.28f, Offset(center.x + len * 0.5f, center.y))
        drawCircle(Color.White, size * 0.13f, Offset(center.x + len * 0.5f, center.y))
        drawCircle(Color(0xFFFF2B4A), size * 0.1f, Offset(center.x - len * 0.5f, center.y))
    }
    // The rider sits up on the bike, bobbing a little with the ride.
    val bob = sin(time * 9f + characterId) * size * 0.05f
    drawCharacter(characterId, center - Offset(0f, size * 0.45f + bob), size * 0.62f, angle, mood, time)
}

/** A little gold crown, for whoever has the most land. */
fun DrawScope.drawCrown(c: Offset, r: Float) {
    val crown = Path().apply {
        moveTo(c.x - r, c.y + r * 0.5f)
        lineTo(c.x - r * 1.1f, c.y - r * 0.6f)
        lineTo(c.x - r * 0.5f, c.y - r * 0.05f)
        lineTo(c.x, c.y - r * 0.8f)
        lineTo(c.x + r * 0.5f, c.y - r * 0.05f)
        lineTo(c.x + r * 1.1f, c.y - r * 0.6f)
        lineTo(c.x + r, c.y + r * 0.5f)
        close()
    }
    drawPath(crown, Brush.verticalGradient(listOf(Color(0xFFFFF1A6), Gold, GoldEdge), startY = c.y - r * 0.8f, endY = c.y + r * 0.5f))
    drawPath(crown, GoldEdge, style = Stroke(r * 0.12f))
    drawCircle(Color(0xFFFF4F5E), r * 0.16f, Offset(c.x, c.y + r * 0.1f))
}

/** A rider on their bike, for menus. */
@Composable
fun BikePortrait(characterId: Int, modifier: Modifier = Modifier, mood: Mood = Mood.HAPPY, time: Float = 0f) {
    Canvas(modifier) {
        val s = size.minDimension * 0.2f
        drawBike(characterId, Offset(size.width / 2, size.height * 0.55f), s, -0.35f + kotlin.math.sin(time * 1.5f) * 0.08f, mood, time)
    }
}
