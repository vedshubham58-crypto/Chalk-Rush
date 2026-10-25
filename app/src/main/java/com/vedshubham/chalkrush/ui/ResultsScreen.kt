package com.vedshubham.chalkrush.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vedshubham.chalkrush.game.Game
import com.vedshubham.chalkrush.game.Knockout

@Composable
fun ResultsScreen(
    game: Game,
    newBest: Boolean,
    onPlayAgain: () -> Unit,
    onHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val me = game.me
    val by = me.knockedOutBy ?: "Someone"
    val reason = when (me.knockout) {
        null -> null
        Knockout.OWN_LINE -> "You ran into your own line."
        Knockout.EDGE -> "You went off the edge of the sidewalk."
        Knockout.LINE_CUT -> "$by cut your line."
        Knockout.LAND_TAKEN -> "$by took all your land."
    }
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Text(
            if (reason == null) "Time's up!" else "Knocked out!",
            fontSize = 36.sp,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.primary,
        )
        if (reason != null) Text(reason, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text("%.1f%%".format(game.percent(me)), fontSize = 64.sp, fontWeight = FontWeight.Black)
        Text("of the sidewalk is yours", color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (newBest) Text("New best!", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
        Text("Place: #${game.myRank()} of ${game.players.size}   ·   Knockouts: ${me.kills}")
        Spacer(Modifier.height(16.dp))
        Button(onClick = onPlayAgain, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text("Play again", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        OutlinedButton(onClick = onHome, modifier = Modifier.fillMaxWidth()) { Text("Home") }
    }
}
