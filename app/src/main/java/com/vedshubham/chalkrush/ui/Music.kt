package com.vedshubham.chalkrush.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.PlaybackParams
import com.vedshubham.chalkrush.R

enum class Track(val res: Int) {
    MENU(R.raw.music_menu),
    RACE(R.raw.music_race),
}

/**
 * Looping background music: a calm synthwave theme on the menus and a faster
 * one while riding, which speeds up a little in the last seconds of a round.
 */
object Music {
    private var player: MediaPlayer? = null
    private var current: Track? = null
    private var appContext: Context? = null
    private var intense = false
    private var paused = false
    var muted = false
        set(value) {
            field = value
            if (value) player?.pause() else if (!paused) player?.start()
        }

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun play(track: Track) {
        if (current == track && player != null) return
        val context = appContext ?: return
        player?.release()
        intense = false
        current = track
        player = MediaPlayer.create(context, track.res)?.apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            isLooping = true
            setVolume(0.55f, 0.55f)
            if (!muted && !paused) start()
        }
    }

    /** Speeds the music up for the final countdown of a round. */
    fun setIntense(on: Boolean) {
        if (on == intense) return
        intense = on
        val p = player ?: return
        runCatching {
            val wasPlaying = p.isPlaying
            p.playbackParams = PlaybackParams().setSpeed(if (on) 1.12f else 1f).setPitch(if (on) 1.06f else 1f)
            if (!wasPlaying) p.pause()
        }
    }

    /** Called when the app goes to the background and comes back. */
    fun onPause() {
        paused = true
        player?.pause()
    }

    fun onResume() {
        paused = false
        if (!muted) player?.start()
    }
}
