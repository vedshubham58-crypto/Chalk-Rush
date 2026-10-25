package com.vedshubham.chalkrush.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.sin

val NeonCyan = Color(0xFF00E5FF)
val NeonPink = Color(0xFFFF2BD6)
val NeonYellow = Color(0xFFFFF04F)

/**
 * The Neon Rush logo: glowing italic lettering with a light trail racing
 * underneath it and Sparky's bike at the front. [time] animates it.
 */
@Composable
fun Logo(time: Float, modifier: Modifier = Modifier) {
    val flicker = if ((time % 4f) in 2.0f..2.08f) 0.5f else 1f
    Box(modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxWidth().height(200.dp)) {
            val w = size.width
            val h = size.height
            // A light trail sweeping under the title, ending at the bike.
            val bikeX = w * (0.78f + 0.03f * sin(time * 2f))
            val trail = Path().apply {
                moveTo(w * 0.04f, h * 0.9f)
                cubicTo(w * 0.3f, h * 0.98f, w * 0.5f, h * 0.84f, bikeX, h * 0.86f)
            }
            drawPath(trail, NeonPink.copy(alpha = 0.3f), style = Stroke(16.dp.toPx(), cap = StrokeCap.Round))
            drawPath(trail, NeonPink, style = Stroke(5.dp.toPx(), cap = StrokeCap.Round))
            drawPath(trail, Color.White, style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round))
            drawBike(1, Offset(bikeX + 18.dp.toPx(), h * 0.86f), 13.dp.toPx(), -0.05f, Mood.HAPPY, time)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.rotate(-4f).offset(y = (-12).dp)) {
            Text("NEON", style = neonStyle(NeonCyan.copy(alpha = flicker)))
            Text("RUSH", style = neonStyle(NeonPink), modifier = Modifier.offset(y = (-22).dp))
        }
    }
}

private fun neonStyle(glow: Color) = TextStyle(
    color = Color.White,
    fontSize = 68.sp,
    fontWeight = FontWeight.Black,
    fontStyle = FontStyle.Italic,
    letterSpacing = 4.sp,
    shadow = Shadow(glow, Offset.Zero, 28f),
)

@Composable
fun CoinIcon(modifier: Modifier = Modifier.size(18.dp)) {
    Canvas(modifier) {
        val r = size.minDimension / 2
        drawCircle(NeonYellow.copy(alpha = 0.3f), r, center)
        drawCircle(NeonYellow, r * 0.78f, center, style = Stroke(r * 0.22f))
        drawCircle(Color.White, r * 0.28f, center)
    }
}
