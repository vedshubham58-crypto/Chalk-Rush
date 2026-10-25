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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The Chalk Rush logo: chunky tilted lettering with a chalk loop swooshing
 * around it and Chalky peeking over the top. [time] animates the swoosh.
 */
@Composable
fun Logo(time: Float, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(190.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxWidth().height(190.dp)) {
            // A dashed chalk loop that slowly travels around the title.
            val w = size.width
            val h = size.height
            val loop = Path().apply {
                moveTo(w * 0.12f, h * 0.62f)
                cubicTo(w * 0.05f, h * 0.15f, w * 0.85f, h * 0.05f, w * 0.92f, h * 0.45f)
                cubicTo(w * 0.98f, h * 0.85f, w * 0.25f, h * 1.0f, w * 0.14f, h * 0.7f)
            }
            drawPath(
                loop,
                Color(0xFFFFE066),
                style = Stroke(
                    width = 7.dp.toPx(),
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(28f, 18f), phase = -time * 60f),
                ),
            )
            drawCharacter(0, Offset(w * 0.8f, h * 0.2f), 26.dp.toPx(), 2.4f, Mood.HAPPY, time)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.rotate(-5f)) {
            Text("CHALK", style = logoStyle(Color.White, Color(0xFF2E86C1)))
            Text("RUSH", style = logoStyle(Color(0xFFFF4FB8), Color(0xFF7A1450)), modifier = Modifier.offset(y = (-18).dp))
        }
    }
}

private fun logoStyle(fill: Color, shadow: Color) = TextStyle(
    color = fill,
    fontSize = 64.sp,
    fontWeight = FontWeight.Black,
    letterSpacing = 2.sp,
    shadow = Shadow(shadow, Offset(6f, 10f), 2f),
)

@Composable
fun CoinIcon(modifier: Modifier = Modifier.size(18.dp)) {
    Canvas(modifier) {
        val r = size.minDimension / 2
        drawCircle(Color(0xFFC8901A), r, center + Offset(0f, r * 0.12f))
        drawCircle(Color(0xFFFFC83D), r * 0.9f, center)
        drawCircle(Color(0xFFFFE28A), r * 0.55f, center, style = Stroke(r * 0.15f))
    }
}
