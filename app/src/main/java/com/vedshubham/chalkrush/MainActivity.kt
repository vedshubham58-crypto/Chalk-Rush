package com.vedshubham.chalkrush

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vedshubham.chalkrush.data.Store
import com.vedshubham.chalkrush.data.coinsFor
import com.vedshubham.chalkrush.game.Game
import com.vedshubham.chalkrush.ui.ChalkRushTheme
import com.vedshubham.chalkrush.ui.GameScreen
import com.vedshubham.chalkrush.ui.HomeScreen
import com.vedshubham.chalkrush.ui.ResultsScreen
import com.vedshubham.chalkrush.ui.Sfx
import com.vedshubham.chalkrush.ui.Sound

enum class Screen { HOME, PLAYING, RESULTS }

/** Keeps the current round alive when the screen is recreated (for example a dark mode switch). */
class AppViewModel : ViewModel() {
    var screen by mutableStateOf(Screen.HOME)
    var game by mutableStateOf<Game?>(null)
    var newBest by mutableStateOf(false)
    var coinsEarned by mutableIntStateOf(0)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = Store(this)
        Sfx.init(this)

        setContent {
            ChalkRushTheme {
                val vm: AppViewModel = viewModel()
                val profile by store.profile.collectAsState()
                Sfx.muted = profile.muted

                fun play() {
                    vm.game = Game(myCharacter = profile.character)
                    vm.screen = Screen.PLAYING
                }

                BackHandler(enabled = vm.screen != Screen.HOME) { vm.screen = Screen.HOME }

                Surface(Modifier.fillMaxSize()) {
                    val game = vm.game
                    when {
                        vm.screen == Screen.PLAYING && game != null -> GameScreen(
                            game = game,
                            onRoundOver = {
                                if (vm.screen == Screen.PLAYING) {
                                    val me = game.me
                                    val percent = game.percent(me)
                                    val rank = game.myRank()
                                    vm.coinsEarned = coinsFor(percent, me.kills, rank, me.character)
                                    vm.newBest = store.saveRound(percent, vm.coinsEarned, won = rank == 1 && me.alive)
                                    vm.screen = Screen.RESULTS
                                }
                            },
                        )
                        vm.screen == Screen.RESULTS && game != null -> ResultsScreen(
                            game = game,
                            coinsEarned = vm.coinsEarned,
                            newBest = vm.newBest,
                            profile = profile,
                            onPlayAgain = ::play,
                            onHome = { vm.screen = Screen.HOME },
                            modifier = Modifier.safeDrawingPadding(),
                        )
                        else -> HomeScreen(
                            profile = profile,
                            onPlay = ::play,
                            onSelect = store::select,
                            onBuy = {
                                if (store.buy(it)) Sfx.play(Sound.COIN)
                            },
                            onToggleMute = { store.setMuted(!profile.muted) },
                            modifier = Modifier.safeDrawingPadding(),
                        )
                    }
                }
            }
        }
    }
}
