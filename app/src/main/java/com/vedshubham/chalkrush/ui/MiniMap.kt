package com.vedshubham.chalkrush.ui

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.vedshubham.chalkrush.game.Game
import com.vedshubham.chalkrush.game.NONE

/** A tiny picture of the whole arena, one pixel per cell, redrawn a few times a second. */
class MiniMap(private val game: Game, theme: MapTheme) {
    private val n = game.size
    private val bitmap = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
    val image = bitmap.asImageBitmap()
    private val pixels = IntArray(n * n)
    private val night = theme.night.toArgb()
    private val land = game.players.map { colorOf(it.character.id).fill.copy(alpha = 0.75f).toArgb() }
    private val lines = game.players.map { colorOf(it.character.id).fill.toArgb() }

    fun refresh() {
        for (i in 0 until n * n) {
            val t = game.trailOwner[i]
            val o = game.owner[i]
            pixels[i] = when {
                t != NONE -> lines[t]
                o != NONE -> land[o]
                else -> night
            }
        }
        bitmap.setPixels(pixels, 0, n, 0, 0, n, n)
    }

    init {
        refresh()
    }
}

fun DrawScope.drawMiniMap(game: Game, map: MiniMap) {
    val side = size.minDimension
    drawImage(
        map.image,
        dstOffset = IntOffset.Zero,
        dstSize = IntSize(side.toInt(), side.toInt()),
        filterQuality = FilterQuality.None,
    )
    val scale = side / game.size
    for (p in game.players) {
        if (!p.alive) continue
        val c = Offset(p.x * scale, p.y * scale)
        if (p.id == game.me.id) {
            drawCircle(Color.White, side * 0.05f, c)
            drawCircle(colorOf(p.character.id).fill, side * 0.032f, c)
        } else {
            drawCircle(colorOf(p.character.id).fill, side * 0.028f, c)
            drawCircle(Color.Black, side * 0.028f, c, style = Stroke(side * 0.008f))
        }
    }
    for (pu in game.powerUps) {
        drawCircle(Color.White.copy(alpha = 0.8f), side * 0.012f, Offset((pu.x + 0.5f) * scale, (pu.y + 0.5f) * scale))
    }
}
