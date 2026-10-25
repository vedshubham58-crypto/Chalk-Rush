package com.vedshubham.chalkrush.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.cos
import kotlin.math.sin

/** Badge shapes players pick to show next to their name. Each one unlocks at a level. */
object Emblems {
    val names = listOf("Bolt", "Star", "Flame", "Diamond", "Wave", "Moon", "Shield", "Heart", "Wing", "Eye", "Comet", "Crown")

    /** The level you need to use emblem [id]. */
    fun levelFor(id: Int) = if (id < 3) 1 else (id - 2) * 2 + 1
}

/** A round neon badge with one of the [Emblems] on it. */
@Composable
fun EmblemBadge(id: Int, color: Color, modifier: Modifier = Modifier, locked: Boolean = false) {
    Canvas(modifier) {
        drawEmblemBadge(id, Offset(size.width / 2, size.height / 2), size.minDimension / 2, if (locked) Color.Gray else color)
    }
}

fun DrawScope.drawEmblemBadge(id: Int, c: Offset, r: Float, color: Color) {
    drawCircle(color.copy(alpha = 0.3f), r, c)
    drawCircle(Brush.radialGradient(listOf(Color(0xFF2A3160), Color(0xFF0A0D1F)), center = c - Offset(r * 0.3f, r * 0.3f), radius = r * 1.2f), r * 0.86f, c)
    drawCircle(color, r * 0.86f, c, style = Stroke(r * 0.1f))
    drawEmblem(id, c, r * 0.5f, color)
}

private fun DrawScope.drawEmblem(id: Int, c: Offset, r: Float, color: Color) {
    val w = r * 0.18f
    fun poly(vararg pts: Float) = Path().apply {
        moveTo(c.x + pts[0] * r, c.y + pts[1] * r)
        for (k in 2 until pts.size step 2) lineTo(c.x + pts[k] * r, c.y + pts[k + 1] * r)
        close()
    }
    when (id % Emblems.names.size) {
        0 -> drawPath(poly(0.2f, -1f, -0.5f, 0.1f, 0f, 0.1f, -0.2f, 1f, 0.5f, -0.15f, 0f, -0.15f), color)
        1 -> drawStar(c, r, color)
        2 -> {
            val flame = Path().apply {
                moveTo(c.x, c.y - r)
                quadraticTo(c.x + r * 0.9f, c.y, c.x + r * 0.5f, c.y + r * 0.8f)
                quadraticTo(c.x, c.y + r * 1.1f, c.x - r * 0.5f, c.y + r * 0.8f)
                quadraticTo(c.x - r * 0.9f, c.y, c.x, c.y - r)
                close()
            }
            drawPath(flame, color)
            drawCircle(Color.White.copy(alpha = 0.8f), r * 0.28f, c + Offset(0f, r * 0.4f))
        }
        3 -> drawPath(poly(0f, -1f, 0.75f, -0.2f, 0f, 1f, -0.75f, -0.2f), color)
        4 -> for (k in 0..2) {
            val p = Path()
            for (s in 0..16) {
                val t = s / 16f
                val x = c.x + (t * 2f - 1f) * r
                val y = c.y + (k - 1) * r * 0.55f + sin(t * 6.283f) * r * 0.2f
                if (s == 0) p.moveTo(x, y) else p.lineTo(x, y)
            }
            drawPath(p, color, style = Stroke(w, cap = StrokeCap.Round))
        }
        5 -> {
            drawCircle(color, r * 0.9f, c)
            drawCircle(Color(0xFF0A0D1F), r * 0.8f, c + Offset(r * 0.4f, -r * 0.2f))
        }
        6 -> drawPath(poly(0f, -1f, 0.8f, -0.6f, 0.7f, 0.3f, 0f, 1f, -0.7f, 0.3f, -0.8f, -0.6f), color)
        7 -> {
            val heart = Path().apply {
                moveTo(c.x, c.y + r * 0.9f)
                cubicTo(c.x - r * 1.4f, c.y - r * 0.1f, c.x - r * 0.6f, c.y - r * 1.2f, c.x, c.y - r * 0.4f)
                cubicTo(c.x + r * 0.6f, c.y - r * 1.2f, c.x + r * 1.4f, c.y - r * 0.1f, c.x, c.y + r * 0.9f)
                close()
            }
            drawPath(heart, color)
        }
        8 -> for (k in 0..2) {
            val y = c.y - r * 0.4f + k * r * 0.4f
            drawLine(color, Offset(c.x - r * 0.9f, y), Offset(c.x + r * (0.9f - k * 0.3f), y - r * 0.3f), w * 1.3f, StrokeCap.Round)
        }
        9 -> {
            drawOval(color, Offset(c.x - r, c.y - r * 0.5f), Size(r * 2f, r))
            drawCircle(Color(0xFF0A0D1F), r * 0.4f, c)
            drawCircle(Color.White, r * 0.15f, c - Offset(r * 0.1f, r * 0.1f))
        }
        10 -> {
            drawCircle(color, r * 0.45f, c + Offset(r * 0.35f, -r * 0.35f))
            for (k in 0..2) {
                val a = 2.36f + (k - 1) * 0.3f
                drawLine(color.copy(alpha = 0.7f), c + Offset(r * 0.35f, -r * 0.35f), c + Offset(cos(a) * r * 1.3f, -sin(a) * r * 1.3f + r * 0.4f), w, StrokeCap.Round)
            }
        }
        else -> drawCrown(c + Offset(0f, r * 0.1f), r * 0.85f)
    }
}
