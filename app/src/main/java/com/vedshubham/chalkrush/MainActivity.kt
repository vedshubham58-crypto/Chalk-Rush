package com.vedshubham.chalkrush

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vedshubham.chalkrush.ads.Ads
import com.vedshubham.chalkrush.ads.BannerAd
import com.vedshubham.chalkrush.data.Store
import com.vedshubham.chalkrush.data.coinsFor
import com.vedshubham.chalkrush.game.Game
import com.vedshubham.chalkrush.ui.BonusScreen
import com.vedshubham.chalkrush.ui.ChalkRushTheme
import com.vedshubham.chalkrush.ui.GameScreen
import com.vedshubham.chalkrush.ui.HomeScreen
import com.vedshubham.chalkrush.ui.ResultsScreen
import com.vedshubham.chalkrush.ui.ReviveScreen
import com.vedshubham.chalkrush.ui.Sfx
import com.vedshubham.chalkrush.ui.Sound

enum class Screen { HOME, PLAYING, REVIVE, BONUS, RESULTS }

/**
 * Full-screen ad between rounds, every 2nd round, and never right after a video
 * the player chose to watch. Ads never interrupt a round: the mid-round Power
 * Drop is an optional video the player taps for a reward.
 */
private const val ROUNDS_PER_AD = 2

/** Keeps the current round alive when the screen is recreated. */
class AppViewModel : ViewModel() {
    var screen by mutableStateOf(Screen.HOME)
    var game by mutableStateOf<Game?>(null)
    var mapIndex by mutableIntStateOf(0)
    var newBest by mutableStateOf(false)
    var coinsEarned by mutableIntStateOf(0)
    var doubled by mutableStateOf(false)
    var bonusCoins by mutableIntStateOf(0)
    var paused by mutableStateOf(false)
    var watchedVideo = false
    var roundsSinceAd = 0
}

@Composable
private fun AdBanner(ready: Boolean) {
    if (ready) Box(Modifier.navigationBarsPadding()) { BannerAd() }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = Store(this)
        Sfx.init(this)
        Ads.setUp(this)

        setContent {
            ChalkRushTheme {
                val vm: AppViewModel = viewModel()
                val profile by store.profile.collectAsState()
                val adsReady by Ads.ready.collectAsState()
                Sfx.muted = profile.muted

                fun startRound() {
                    vm.mapIndex = store.profile.value.currentMap
                    vm.game = Game(store.profile.value.map, store.profile.value.character)
                    vm.watchedVideo = false
                    vm.doubled = false
                    vm.bonusCoins = 0
                    vm.paused = false
                    vm.screen = Screen.PLAYING
                }

                fun play() {
                    vm.roundsSinceAd++
                    if (vm.roundsSinceAd > ROUNDS_PER_AD && !vm.watchedVideo) {
                        vm.roundsSinceAd = 1
                        Ads.showInterstitial(this@MainActivity) { startRound() }
                    } else {
                        startRound()
                    }
                }

                fun finishRound(game: Game) {
                    val me = game.me
                    val percent = game.percent(me)
                    val rank = game.myRank()
                    val streakAfter = if (game.won) profile.winStreak + 1 else 0
                    vm.coinsEarned = coinsFor(percent, me.kills, rank, game.won, streakAfter, me.character)
                    vm.newBest = store.saveRound(percent, vm.coinsEarned, game.won)
                    Sfx.play(if (game.won) Sound.CAPTURE_BIG else Sound.COIN)
                    // A win earns the Bonus Rush before the results.
                    vm.screen = if (game.won) Screen.BONUS else Screen.RESULTS
                }

                fun noVideo() {
                    Toast.makeText(this@MainActivity, "No video ready yet. Try again in a moment.", Toast.LENGTH_SHORT).show()
                }

                BackHandler(enabled = vm.screen != Screen.HOME) { vm.screen = Screen.HOME }

                Surface(Modifier.fillMaxSize()) {
                    val game = vm.game
                    when {
                        vm.screen == Screen.PLAYING && game != null -> GameScreen(
                            game = game,
                            mapIndex = vm.mapIndex,
                            onRoundOver = {
                                if (vm.screen == Screen.PLAYING) {
                                    val canRevive = !game.me.alive && !game.revived && Ads.rewardedAvailable()
                                    if (canRevive) vm.screen = Screen.REVIVE else finishRound(game)
                                }
                            },
                            paused = vm.paused,
                            powerDropReady = adsReady,
                            onPowerDrop = {
                                if (!vm.paused && game.powerDropOffered) {
                                    vm.paused = true
                                    var earned = false
                                    val shown = Ads.showRewarded(
                                        this@MainActivity,
                                        onClosed = {
                                            if (earned) {
                                                vm.watchedVideo = true
                                                game.powerDrop()
                                            } else {
                                                game.skipPowerDrop()
                                            }
                                            vm.paused = false
                                        },
                                    ) { earned = true }
                                    if (!shown) {
                                        game.skipPowerDrop()
                                        vm.paused = false
                                        noVideo()
                                    }
                                }
                            },
                        )
                        vm.screen == Screen.REVIVE && game != null -> ReviveScreen(
                            game = game,
                            onRevive = {
                                var earned = false
                                val shown = Ads.showRewarded(
                                    this@MainActivity,
                                    onClosed = {
                                        if (earned) {
                                            vm.watchedVideo = true
                                            game.revive()
                                            vm.screen = Screen.PLAYING
                                        } else {
                                            finishRound(game)
                                        }
                                    },
                                ) { earned = true }
                                if (!shown) finishRound(game)
                            },
                            onSkip = { if (vm.screen == Screen.REVIVE) finishRound(game) },
                        )
                        vm.screen == Screen.BONUS && game != null -> BonusScreen(
                            videoReady = adsReady,
                            onGoldenBox = { onWatched ->
                                val shown = Ads.showRewarded(this@MainActivity) {
                                    vm.watchedVideo = true
                                    onWatched()
                                }
                                if (!shown) noVideo()
                            },
                            onDone = { coins ->
                                if (vm.screen == Screen.BONUS) {
                                    store.addCoins(coins)
                                    vm.bonusCoins = coins
                                    vm.screen = Screen.RESULTS
                                }
                            },
                        )
                        vm.screen == Screen.RESULTS && game != null -> ResultsScreen(
                            game = game,
                            mapIndex = vm.mapIndex,
                            coinsEarned = vm.coinsEarned,
                            doubled = vm.doubled,
                            canDouble = adsReady,
                            newBest = vm.newBest,
                            profile = profile,
                            bonusCoins = vm.bonusCoins,
                            onDouble = {
                                val shown = Ads.showRewarded(this@MainActivity) {
                                    vm.watchedVideo = true
                                    vm.doubled = true
                                    store.addCoins(vm.coinsEarned)
                                }
                                if (!shown) noVideo()
                            },
                            onPlayAgain = ::play,
                            onHome = { vm.screen = Screen.HOME },
                            modifier = Modifier.safeDrawingPadding(),
                            banner = { AdBanner(adsReady) },
                        )
                        else -> HomeScreen(
                            profile = profile,
                            onPlay = ::play,
                            onSelect = store::select,
                            onBuy = {
                                if (store.buy(it)) Sfx.play(Sound.COIN)
                            },
                            onSelectMap = store::selectMap,
                            onToggleMute = { store.setMuted(!profile.muted) },
                            onClaimDaily = { video ->
                                if (video) {
                                    val shown = Ads.showRewarded(this@MainActivity) {
                                        store.claimDaily(multiplier = 3)
                                        Sfx.play(Sound.CAPTURE_BIG)
                                    }
                                    if (!shown) noVideo()
                                } else if (store.claimDaily() > 0) {
                                    Sfx.play(Sound.COIN)
                                }
                            },
                            videoReady = adsReady,
                            modifier = Modifier.safeDrawingPadding(),
                            banner = { AdBanner(adsReady) },
                        )
                    }
                }
            }
        }
    }
}
