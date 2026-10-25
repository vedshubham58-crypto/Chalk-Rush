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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import kotlinx.coroutines.delay

@Composable
fun ResultsScreen(
    game: Game,
    coinsEarned: Int,
    newBest: Boolean,
    profile: Profile,
    onPlayAgain: () -> Unit,
    onHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val time = rememberClock()
    val me = game.me
    val rank = game.myRank()
    val by = me.knockedOutBy ?: "Someone"
    val reason = when (me.knockout) {
        null -> null
        Knockout.LINE_CUT -> "$by cut your line!"
        Knockout.LAND_TAKEN -> "$by took all your land!"
    }
    val title = when {
        reason != null -> "Knocked out!"
        rank == 1 -> "You win!"
        rank <= 3 -> "Top 3!"
        else -> "Time's up!"
    }

    // Count the coins up with a little jingle.
    var shownCoins by remember { mutableIntStateOf(0) }
    LaunchedEffect(coinsEarned) {
        delay(400)
        val steps = 20
        for (k in 1..steps) {
            shownCoins = coinsEarned * k / steps
            if (k % 4 == 0) Sfx.play(Sound.COIN, volume = 0.5f)
            delay(40)
        }
    }

    val next = Characters.all.firstOrNull { it.id !in profile.owned }

    MenuBackdrop {
        Column(
            modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
        ) {
            CharacterPortrait(
                me.character.id,
                Modifier.size(140.dp),
                mood = if (reason != null) Mood.KNOCKED_OUT else Mood.HAPPY,
                time = time,
            )
            Text(title, fontSize = 40.sp, fontWeight = FontWeight.Black, color = if (reason != null) Color(0xFFFF6B6B) else ChalkYellow)
            if (reason != null) Text(reason, color = Color.White, textAlign = TextAlign.Center)
            Text("%.1f%%".format(game.percent(me)), fontSize = 64.sp, fontWeight = FontWeight.Black, color = Color.White)
            Text("of the sidewalk was yours", color = Color.White.copy(alpha = 0.7f))
            if (newBest) Text("★ New best! ★", fontWeight = FontWeight.Black, color = ChalkPink, fontSize = 20.sp)

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Stat("Place", "#$rank")
                Stat("Knockouts", "${me.kills}")
                Stat("Best streak", "${game.bestStreak}")
            }

            Row(
                Modifier.background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(50)).padding(horizontal = 18.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CoinIcon(Modifier.size(26.dp))
                Text("  +$shownCoins coins", color = ChalkYellow, fontSize = 24.sp, fontWeight = FontWeight.Black)
            }

            if (next != null) {
                val progress = (profile.coins.toFloat() / next.cost).coerceIn(0f, 1f)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (profile.coins >= next.cost) "You can unlock ${next.name} now!" else "Next: ${next.name}, ${next.cost - profile.coins} more coins",
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
                colors = ButtonDefaults.buttonColors(containerColor = ChalkPink, contentColor = Color.White),
            ) {
                Text("PLAY AGAIN", fontSize = 22.sp, fontWeight = FontWeight.Black)
            }
            OutlinedButton(onClick = onHome, modifier = Modifier.fillMaxWidth()) {
                Text("Characters & home", color = Color.White)
            }
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
