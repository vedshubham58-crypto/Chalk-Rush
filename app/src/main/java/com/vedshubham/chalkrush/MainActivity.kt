package com.vedshubham.chalkrush

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vedshubham.chalkrush.data.Store
import com.vedshubham.chalkrush.game.Game
import com.vedshubham.chalkrush.ui.ChalkRushTheme
import com.vedshubham.chalkrush.ui.GameScreen
import com.vedshubham.chalkrush.ui.HomeScreen
import com.vedshubham.chalkrush.ui.ResultsScreen

enum class Screen { HOME, PLAYING, RESULTS }

/** Keeps the current round alive when the screen is recreated (for example a dark mode switch). */
class AppViewModel : ViewModel() {
    var screen by mutableStateOf(Screen.HOME)
    var game by mutableStateOf<Game?>(null)
    var newBest by mutableStateOf(false)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = Store(this)

        setContent {
            ChalkRushTheme {
                val vm: AppViewModel = viewModel()
                fun play() {
                    vm.game = Game()
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
                                    vm.newBest = store.saveRound(game.percent(game.me))
                                    vm.screen = Screen.RESULTS
                                }
                            },
                        )
                        vm.screen == Screen.RESULTS && game != null -> ResultsScreen(
                            game = game,
                            newBest = vm.newBest,
                            onPlayAgain = ::play,
                            onHome = { vm.screen = Screen.HOME },
                            modifier = Modifier.safeDrawingPadding(),
                        )
                        else -> HomeScreen(
                            best = store.best,
                            rounds = store.rounds,
                            onPlay = ::play,
                            modifier = Modifier.safeDrawingPadding(),
                        )
                    }
                }
            }
        }
    }
}
