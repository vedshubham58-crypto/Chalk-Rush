package com.vedshubham.chalkrush.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vedshubham.chalkrush.data.MAX_UPGRADE
import com.vedshubham.chalkrush.data.Profile
import com.vedshubham.chalkrush.data.upgradeCost
import com.vedshubham.chalkrush.game.Maps

/** The XP bar: your level and how far into it you are. */
@Composable
fun LevelBar(profile: Profile, modifier: Modifier = Modifier) {
    val (into, needed) = profile.levelProgress
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("LV ${profile.level}", color = NeonYellow, fontWeight = FontWeight.Black, fontSize = 14.sp)
            Spacer(Modifier.width(6.dp))
            Text("$into / $needed XP", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
        }
        Box(Modifier.fillMaxWidth().height(8.dp).background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp))) {
            Box(
                Modifier.fillMaxWidth((into.toFloat() / needed).coerceIn(0f, 1f)).height(8.dp)
                    .background(Brush.horizontalGradient(listOf(NeonCyan, NeonYellow)), RoundedCornerShape(4.dp)),
            )
        }
    }
}

/**
 * Your rider profile: username, emblem, level, stats, conquered lands and
 * bike upgrades. With [firstTime] it's the welcome screen that asks for a name.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(
    profile: Profile,
    firstTime: Boolean,
    onSave: (name: String, emblem: Int) -> Unit,
    onUpgrade: (engine: Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val time = rememberClock()
    var name by remember { mutableStateOf(profile.name) }
    var emblem by remember { mutableIntStateOf(profile.emblem) }
    val color = colorOf(profile.selected).fill
    MenuBackdrop {
        Column(
            modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(if (firstTime) "CREATE YOUR RIDER" else "YOUR PROFILE", color = NeonCyan, fontSize = 28.sp, fontWeight = FontWeight.Black)
            Box(contentAlignment = Alignment.BottomEnd) {
                BikePortrait(profile.selected, Modifier.size(180.dp, 130.dp), time = time)
                EmblemBadge(emblem, color, Modifier.size(48.dp))
            }
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(14) },
                label = { Text("Username") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = NeonCyan,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.4f),
                    focusedLabelColor = NeonCyan,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            Text("Pick your emblem", color = Color.White, fontWeight = FontWeight.Bold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Emblems.names.indices.forEach { id ->
                    val need = Emblems.levelFor(id)
                    val open = profile.level >= need
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        EmblemBadge(
                            id, color,
                            Modifier
                                .size(48.dp)
                                .border(if (id == emblem) 3.dp else 0.dp, if (id == emblem) NeonYellow else Color.Transparent, RoundedCornerShape(50))
                                .clickable(enabled = open) { emblem = id },
                            locked = !open,
                        )
                        Text(if (open) Emblems.names[id] else "LV $need", color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp)
                    }
                }
            }

            Button(
                onClick = { onSave(name.ifBlank { "Rider" }, emblem) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = NeonPink, contentColor = Color.White),
            ) {
                Text(if (firstTime) "LET'S RIDE!" else "SAVE", fontSize = 20.sp, fontWeight = FontWeight.Black)
            }

            if (!firstTime) {
                LevelBar(profile, Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatChip("Wins", "${profile.wins}")
                    StatChip("Rounds", "${profile.rounds}")
                    StatChip("KOs", "${profile.kills}")
                    StatChip("Streak", "${profile.bestWinStreak}")
                }

                Text("LANDS CONQUERED  ${profile.conquered.size}/${Maps.all.size}", color = NeonYellow, fontWeight = FontWeight.Black)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (m in Maps.all) {
                        val won = m.index in profile.conquered
                        Text(
                            (if (won) "★ " else "") + m.name,
                            color = if (won) Color.Black else Color.White.copy(alpha = 0.5f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .background(if (won) themeOf(m.index).accent else Color.Black.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                }

                Text("BIKE UPGRADES", color = NeonCyan, fontWeight = FontWeight.Black)
                UpgradeRow("⚡ Engine", "Faster bike, +3% speed per level", profile.engine, profile.coins) { onUpgrade(true) }
                UpgradeRow("🛡 Armor", "Longer shield when you spawn", profile.armor, profile.coins) { onUpgrade(false) }

                Button(onClick = onBack, colors = ButtonDefaults.buttonColors(containerColor = MenuCard, contentColor = Color.White)) {
                    Text("Back")
                }
            }
        }
    }
}

@Composable
private fun StatChip(label: String, value: String) {
    Column(
        Modifier.background(MenuCard, RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp)
        Text(label, color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
    }
}

@Composable
private fun UpgradeRow(title: String, about: String, level: Int, coins: Int, onBuy: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(MenuCard, RoundedCornerShape(16.dp)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Black)
            Text(about, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
                repeat(MAX_UPGRADE) {
                    Box(Modifier.size(16.dp, 6.dp).background(if (it < level) NeonCyan else Color.White.copy(alpha = 0.2f), RoundedCornerShape(3.dp)))
                }
            }
        }
        if (level >= MAX_UPGRADE) {
            Text("MAX", color = NeonYellow, fontWeight = FontWeight.Black)
        } else {
            val cost = upgradeCost(level)
            Button(
                onClick = onBuy,
                enabled = coins >= cost,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF13A33A), contentColor = Color.White),
            ) {
                Text("$cost", fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
            }
        }
    }
}
