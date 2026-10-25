package com.vedshubham.chalkrush.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vedshubham.chalkrush.data.Profile
import com.vedshubham.chalkrush.game.Character
import com.vedshubham.chalkrush.game.Characters
import kotlin.random.Random

val MenuBackground = Color(0xFF2C2F36)
val MenuCard = Color(0xFF3A3E47)
val ChalkYellow = Color(0xFFFFE066)
val ChalkPink = Color(0xFFFF4FB8)

/** A ticking clock in seconds for menu animations. */
@Composable
fun rememberClock(): Float {
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { t = (it - start) / 1_000_000_000f }
    }
    return t
}

/** The dark sidewalk with chalk scribbles behind the menus. */
@Composable
fun MenuBackdrop(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(MenuBackground)) {
        Canvas(Modifier.fillMaxSize()) {
            val r = Random(3)
            repeat(120) {
                drawCircle(Color.White.copy(alpha = 0.05f), 2f + r.nextFloat() * 3f, Offset(r.nextFloat() * size.width, r.nextFloat() * size.height))
            }
        }
        content()
    }
}

@Composable
fun HomeScreen(
    profile: Profile,
    onPlay: () -> Unit,
    onSelect: (Character) -> Unit,
    onBuy: (Character) -> Unit,
    onToggleMute: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val time = rememberClock()
    var browsing by remember { mutableIntStateOf(profile.selected) }
    val shown = Characters.byId(browsing)
    val owned = shown.id in profile.owned

    MenuBackdrop {
        Column(
            modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CoinIcon()
                    Text("  ${profile.coins}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
                Spacer(Modifier.weight(1f))
                Text(
                    if (profile.muted) "🔇" else "🔊",
                    fontSize = 24.sp,
                    modifier = Modifier.clickable(onClick = onToggleMute).padding(8.dp),
                )
            }

            Logo(time)

            // Character picker.
            Column(
                Modifier.fillMaxWidth().background(MenuCard, RoundedCornerShape(24.dp)).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Arrow("‹") { browsing = (browsing - 1 + Characters.all.size) % Characters.all.size }
                    Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
                        CharacterPortrait(shown.id, Modifier.size(150.dp), time = time)
                        if (!owned) Text("🔒", fontSize = 32.sp, modifier = Modifier.align(Alignment.BottomEnd))
                    }
                    Arrow("›") { browsing = (browsing + 1) % Characters.all.size }
                }
                Text(shown.name, color = colorOf(shown.id).fill, fontSize = 26.sp, fontWeight = FontWeight.Black)
                Text(shown.tagline, color = Color.White.copy(alpha = 0.7f))
                Text("★ ${shown.perkText}", color = ChalkYellow, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Spacer(Modifier.height(10.dp))
                when {
                    owned && profile.selected == shown.id -> Text("Selected", color = Color.White.copy(alpha = 0.6f), fontWeight = FontWeight.Bold)
                    owned -> SmallButton("Choose ${shown.name}", Color(0xFF4A90D9)) { onSelect(shown) }
                    profile.coins >= shown.cost -> SmallButton("Unlock for ${shown.cost} coins", Color(0xFF3D9450)) { onBuy(shown) }
                    else -> Text(
                        "Unlock for ${shown.cost} coins · ${shown.cost - profile.coins} more to go",
                        color = Color.White.copy(alpha = 0.6f),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (c in Characters.all) {
                        Box(
                            Modifier
                                .size(if (c.id == browsing) 12.dp else 8.dp)
                                .background(if (c.id in profile.owned) colorOf(c.id).fill else Color.White.copy(alpha = 0.25f), RoundedCornerShape(50)),
                        )
                    }
                }
            }

            Button(
                onClick = onPlay,
                modifier = Modifier.fillMaxWidth().height(72.dp),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ChalkPink, contentColor = Color.White),
            ) {
                Text("PLAY as ${profile.character.name}", fontSize = 24.sp, fontWeight = FontWeight.Black)
            }

            if (profile.rounds > 0) {
                Text(
                    "Best %.1f%%  ·  %d wins  ·  %d rounds".format(profile.best, profile.wins, profile.rounds),
                    color = Color.White.copy(alpha = 0.7f),
                )
            }
        }
    }
}

@Composable
private fun Arrow(label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).border(2.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(50)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SmallButton(label: String, color: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White),
        modifier = Modifier.width(240.dp),
    ) {
        Text(label, fontWeight = FontWeight.Bold)
    }
}
