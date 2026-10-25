package com.vedshubham.chalkrush.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.vedshubham.chalkrush.R

enum class Sound(val res: Int) {
    CAPTURE(R.raw.capture),
    CAPTURE_BIG(R.raw.capture_big),
    KNOCKOUT(R.raw.knockout),
    LOSE(R.raw.lose),
    POWER_UP(R.raw.powerup),
    BEEP(R.raw.beep),
    GO(R.raw.go),
    BLOCK(R.raw.block),
    COIN(R.raw.coin),
}

/** Short sound effects, loaded once when the app starts. */
object Sfx {
    private var pool: SoundPool? = null
    private val ids = HashMap<Sound, Int>()
    var muted = false

    fun init(context: Context) {
        if (pool != null) return
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val p = SoundPool.Builder().setMaxStreams(6).setAudioAttributes(attrs).build()
        for (s in Sound.entries) ids[s] = p.load(context, s.res, 1)
        pool = p
    }

    fun play(sound: Sound, volume: Float = 1f, rate: Float = 1f) {
        if (muted) return
        val id = ids[sound] ?: return
        pool?.play(id, volume, volume, 1, 0, rate.coerceIn(0.5f, 2f))
    }
}
