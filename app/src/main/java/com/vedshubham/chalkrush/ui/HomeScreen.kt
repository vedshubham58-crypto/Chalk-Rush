package com.vedshubham.chalkrush.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vedshubham.chalkrush.data.DAILY_REWARDS
import com.vedshubham.chalkrush.data.Profile
import com.vedshubham.chalkrush.game.Character
import com.vedshubham.chalkrush.game.Characters
import com.vedshubham.chalkrush.game.Maps

val MenuBackground = Color(0xFF090C22)
val MenuCard = Color(0xFF151A3A)

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

/** The night grid behind the menus, slowly scrolling. */
@Composable
fun MenuBackdrop(content: @Composable () -> Unit) {
    val time = rememberClock()
    Box(Modifier.fillMaxSize().background(MenuBackground)) {
        Canvas(Modifier.fillMaxSize()) {
            val step = 40.dp.toPx()
            val shift = (time * 20f) % step
            var y = -step + shift
            while (y < size.height) {
                drawLine(Color(0xFF1C2766), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                y += step
            }
            var x = 0f
            while (x < size.width) {
                drawLine(Color(0xFF1C2766), Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
                x += step
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
    onSelectMap: (Int) -> Unit,
    onToggleMute: () -> Unit,
    /** Claims the daily reward; true means by watching a video for triple coins. */
    onClaimDaily: (video: Boolean) -> Unit,
    videoReady: Boolean,
    onProfile: () -> Unit,
    modifier: Modifier = Modifier,
    banner: @Composable () -> Unit = {},
) {
    val time = rememberClock()
    var browsing by remember { mutableIntStateOf(profile.selected) }
    val shown = Characters.byId(browsing)
    val owned = shown.id in profile.owned

    MenuBackdrop {
        Column(modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Pill {
                        CoinIcon()
                        Text("  ${profile.coins}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                    Spacer(Modifier.width(8.dp))
                    if (profile.winStreak > 0) {
                        Pill { Text("🔥 ${profile.winStreak} win streak", color = NeonYellow, fontWeight = FontWeight.Bold) }
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (profile.muted) "🔇" else "🔊",
                        fontSize = 24.sp,
                        modifier = Modifier.clickable(onClick = onToggleMute).padding(8.dp),
                    )
                }

                // Your profile card: emblem, name and level. Tap to open the profile and upgrades.
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(MenuCard, RoundedCornerShape(18.dp))
                        .border(1.dp, colorOf(profile.selected).fill.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
                        .clickable(onClick = onProfile)
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    EmblemBadge(profile.emblem, colorOf(profile.selected).fill, Modifier.size(44.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(profile.name.ifBlank { "Rider" }, color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp)
                        LevelBar(profile, Modifier.fillMaxWidth())
                    }
                    Spacer(Modifier.width(10.dp))
                    Text("PROFILE\n& UPGRADES ›", color = NeonCyan, fontSize = 11.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.End)
                }

                Logo(time)

                if (profile.dailyReady) DailyReward(profile, videoReady, onClaimDaily)

                // Map picker: win a map to unlock the next one.
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "MAP ${profile.currentMap + 1} OF ${Maps.all.size}: ${profile.map.name.uppercase()}",
                        color = themeOf(profile.currentMap).accent,
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp,
                    )
                    Text("Conquer a land to unlock the next one", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (m in Maps.all) {
                            val open = m.index <= profile.mapsUnlocked
                            val theme = themeOf(m.index)
                            val chosen = m.index == profile.currentMap
                            Box(
                                Modifier
                                    .size(52.dp)
                                    .background(theme.night, RoundedCornerShape(12.dp))
                                    .border(if (chosen) 3.dp else 1.dp, if (open) theme.accent else Color.White.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                                    .clickable(enabled = open) { onSelectMap(m.index) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    if (open) "${m.index + 1}" else "🔒",
                                    color = if (open) theme.accent else Color.White,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 18.sp,
                                )
                            }
                        }
                    }
                }

                // Rider picker.
                Column(
                    Modifier.fillMaxWidth().background(MenuCard, RoundedCornerShape(24.dp)).border(1.dp, colorOf(shown.id).fill.copy(alpha = 0.5f), RoundedCornerShape(24.dp)).padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Arrow("‹") { browsing = (browsing - 1 + Characters.all.size) % Characters.all.size }
                        Box(Modifier.size(170.dp, 130.dp), contentAlignment = Alignment.Center) {
                            BikePortrait(shown.id, Modifier.size(170.dp, 130.dp), time = time)
                            if (!owned) Text("🔒", fontSize = 30.sp, modifier = Modifier.align(Alignment.BottomEnd))
                        }
                        Arrow("›") { browsing = (browsing + 1) % Characters.all.size }
                    }
                    Text(shown.name, color = colorOf(shown.id).fill, fontSize = 26.sp, fontWeight = FontWeight.Black)
                    Text(shown.tagline, color = Color.White.copy(alpha = 0.7f))
                    Text("★ ${shown.perkText}", color = NeonYellow, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(10.dp))
                    when {
                        owned && profile.selected == shown.id -> Text("Your rider", color = Color.White.copy(alpha = 0.6f), fontWeight = FontWeight.Bold)
                        owned -> SmallButton("Ride as ${shown.name}", Color(0xFF1D7FA3)) { onSelect(shown) }
                        profile.coins >= shown.cost -> SmallButton("Unlock for ${shown.cost} coins", Color(0xFF13A33A)) { onBuy(shown) }
                        else -> Text(
                            "Unlock for ${shown.cost} coins · ${shown.cost - profile.coins} more to go",
                            color = Color.White.copy(alpha = 0.6f),
                        )
                    }
                }

                Button(
                    onClick = onPlay,
                    modifier = Modifier.fillMaxWidth().height(72.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonPink, contentColor = Color.White),
                ) {
                    Text("RIDE!", fontSize = 28.sp, fontWeight = FontWeight.Black)
                }

                if (profile.rounds > 0) {
                    Text(
                        "Best %.1f%%  ·  %d wins  ·  best streak %d".format(profile.best, profile.wins, profile.bestWinStreak),
                        color = Color.White.copy(alpha = 0.7f),
                    )
                }
            }
            banner()
        }
    }
}

/** Today's login reward on a 7-day ladder that grows each day you come back. */
@Composable
private fun DailyReward(profile: Profile, videoReady: Boolean, onClaim: (video: Boolean) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color(0xFF3A1F6B), MenuCard)), RoundedCornerShape(20.dp))
            .border(2.dp, NeonYellow.copy(alpha = 0.7f), RoundedCornerShape(20.dp))
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("🎁 DAILY REWARD · DAY ${profile.dailyDay}", color = NeonYellow, fontWeight = FontWeight.Black, fontSize = 18.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            DAILY_REWARDS.forEachIndexed { k, coins ->
                val day = k + 1
                val today = day == profile.dailyDay
                Column(
                    Modifier
                        .width(38.dp)
                        .background(
                            when {
                                today -> NeonYellow
                                day < profile.dailyDay -> Color.White.copy(alpha = 0.25f)
                                else -> Color.Black.copy(alpha = 0.35f)
                            },
                            RoundedCornerShape(8.dp),
                        )
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("D$day", color = if (today) Color.Black else Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text("$coins", color = if (today) Color.Black else NeonYellow, fontSize = 12.sp, fontWeight = FontWeight.Black)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { onClaim(false) },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF13A33A), contentColor = Color.White),
            ) { Text("Claim ${profile.dailyCoins}", fontWeight = FontWeight.Black) }
            if (videoReady) {
                Button(
                    onClick = { onClaim(true) },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB000), contentColor = Color.Black),
                ) { Text("▶ Video: ${profile.dailyCoins * 3}", fontWeight = FontWeight.Black) }
            }
        }
        Text("Come back tomorrow for a bigger reward!", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
    }
}

@Composable
fun Pill(content: @Composable () -> Unit) {
    Row(
        Modifier.background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

@Composable
private fun Arrow(label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).border(2.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(50)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
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
