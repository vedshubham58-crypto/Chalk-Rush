package com.vedshubham.chalkrush.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vedshubham.chalkrush.data.Profile
import com.vedshubham.chalkrush.game.Characters
import com.vedshubham.chalkrush.game.Game
import com.vedshubham.chalkrush.game.Knockout
import com.vedshubham.chalkrush.game.Maps
import kotlinx.coroutines.delay

private val KoRed = Color(0xFFFF4F5E)

/** "Keep riding?" after a knockout: watch a video to come back once per round. */
@Composable
fun ReviveScreen(game: Game, onRevive: () -> Unit, onSkip: () -> Unit) {
    val time = rememberClock()
    var left by remember { mutableFloatStateOf(6f) }
    var watching by remember { mutableStateOf(false) }
    LaunchedEffect(watching) {
        if (watching) return@LaunchedEffect
        while (left > 0f) {
            delay(50)
            left -= 0.05f
        }
        onSkip()
    }
    val me = game.me
    MenuBackdrop {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
        ) {
            BikePortrait(me.character.id, Modifier.size(200.dp, 150.dp), mood = Mood.KNOCKED_OUT, time = time)
            Text("KNOCKED OUT!", color = KoRed, fontSize = 36.sp, fontWeight = FontWeight.Black)
            Text("${me.knockedOutBy ?: "Someone"} cut your trail.", color = Color.White)
            Text("Keep your %.1f%% and get back on the grid?".format(game.percent(me)), color = Color.White.copy(alpha = 0.8f), textAlign = TextAlign.Center)
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(progress = { left / 6f }, color = NeonCyan, modifier = Modifier.size(64.dp), strokeWidth = 6.dp)
                Text("${left.toInt() + 1}", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
            }
            Button(
                onClick = {
                    watching = true
                    onRevive()
                },
                enabled = !watching,
                modifier = Modifier.fillMaxWidth().height(64.dp),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF13A33A), contentColor = Color.White),
            ) {
                Text("▶  WATCH VIDEO TO REVIVE", fontSize = 18.sp, fontWeight = FontWeight.Black)
            }
            OutlinedButton(onClick = onSkip) { Text("No thanks", color = Color.White) }
        }
    }
}

@Composable
fun ResultsScreen(
    game: Game,
    mapIndex: Int,
    coinsEarned: Int,
    doubled: Boolean,
    canDouble: Boolean,
    newBest: Boolean,
    profile: Profile,
    onDouble: () -> Unit,
    onPlayAgain: () -> Unit,
    onHome: () -> Unit,
    modifier: Modifier = Modifier,
    banner: @Composable () -> Unit = {},
) {
    val time = rememberClock()
    val me = game.me
    val rank = game.myRank()
    val won = game.won
    val reason = when (me.knockout) {
        null -> null
        Knockout.LINE_CUT -> "${me.knockedOutBy ?: "Someone"} cut your trail!"
        Knockout.LAND_TAKEN -> "${me.knockedOutBy ?: "Someone"} took all your land!"
    }
    val lastMap = mapIndex == Maps.all.lastIndex
    val title = when {
        won && lastMap -> "YOU RULE THE CITY!"
        won -> "MAP CLEARED!"
        reason != null -> "KNOCKED OUT!"
        else -> "SO CLOSE!"
    }
    val total = if (doubled) coinsEarned * 2 else coinsEarned

    // Count the coins up with a little jingle.
    var shownCoins by remember { mutableIntStateOf(0) }
    LaunchedEffect(total) {
        delay(300)
        val from = shownCoins
        val steps = 20
        for (k in 1..steps) {
            shownCoins = from + (total - from) * k / steps
            if (k % 4 == 0) Sfx.play(Sound.COIN, volume = 0.5f)
            delay(35)
        }
    }

    val next = Characters.all.firstOrNull { it.id !in profile.owned }

    MenuBackdrop {
        Column(modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
            ) {
                BikePortrait(
                    me.character.id,
                    Modifier.size(180.dp, 130.dp),
                    mood = if (won) Mood.HAPPY else if (reason != null) Mood.KNOCKED_OUT else Mood.SCARED,
                    time = time,
                )
                Text(title, fontSize = 34.sp, fontWeight = FontWeight.Black, color = if (won) NeonYellow else KoRed, textAlign = TextAlign.Center)
                when {
                    won && !lastMap -> Text("Next up: Map ${mapIndex + 2}, ${Maps.byIndex(mapIndex + 1).name}", color = themeOf(mapIndex + 1).accent, fontWeight = FontWeight.Bold)
                    reason != null -> Text(reason, color = Color.White, textAlign = TextAlign.Center)
                    else -> Text("Finish with the most land to win. You were #$rank.", color = Color.White, textAlign = TextAlign.Center)
                }
                if (won && profile.winStreak > 1) {
                    Text("🔥 ${profile.winStreak} WINS IN A ROW", color = Color(0xFFFF9F1C), fontWeight = FontWeight.Black, fontSize = 20.sp)
                }
                Text("%.1f%%".format(game.percent(me)), fontSize = 60.sp, fontWeight = FontWeight.Black, color = Color.White)
                if (newBest) Text("★ New best! ★", fontWeight = FontWeight.Black, color = NeonPink, fontSize = 18.sp)

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Stat("Place", "#$rank")
                    Stat("Knockouts", "${me.kills}")
                    Stat("Combo", "${game.bestStreak}")
                }

                Pill {
                    CoinIcon(Modifier.size(26.dp))
                    Text("  +$shownCoins coins", color = NeonYellow, fontSize = 24.sp, fontWeight = FontWeight.Black)
                }
                if (canDouble && !doubled) {
                    Button(
                        onClick = onDouble,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB000), contentColor = Color.Black),
                    ) {
                        Text("▶  Watch video: DOUBLE COINS", fontWeight = FontWeight.Black)
                    }
                }

                if (next != null) {
                    val progress = (profile.coins.toFloat() / next.cost).coerceIn(0f, 1f)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            if (profile.coins >= next.cost) "You can unlock ${next.name} now!" else "Next rider: ${next.name}, ${next.cost - profile.coins} more coins",
                            color = Color.White,
                        )
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            color = colorOf(next.id).fill,
                            trackColor = Color.White.copy(alpha = 0.15f),
                            modifier = Modifier.fillMaxWidth(0.7f).height(10.dp),
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onPlayAgain,
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonPink, contentColor = Color.White),
                ) {
                    Text(if (won && !lastMap) "NEXT MAP" else "RIDE AGAIN", fontSize = 22.sp, fontWeight = FontWeight.Black)
                }
                OutlinedButton(onClick = onHome, modifier = Modifier.fillMaxWidth()) {
                    Text("Riders & maps", color = Color.White)
                }
            }
            banner()
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Box(Modifier.background(MenuCard, RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 8.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
            Text(label, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
        }
    }
}
