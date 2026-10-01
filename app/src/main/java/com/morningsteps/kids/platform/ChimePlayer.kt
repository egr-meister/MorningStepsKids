package com.morningsteps.kids.platform

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import com.morningsteps.kids.R

/**
 * Plays the short bundled chime. Only ever called from a visible screen, never in the background.
 * Uses the sonification usage so the device's volume and ringer mode decide whether it is heard;
 * it stays silent in silent or vibrate mode.
 */
class ChimePlayer(context: Context) {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(AudioManager::class.java)
    private var soundPool: SoundPool? = null
    private var soundId: Int = 0
    private var loaded = false

    private fun ensureLoaded() {
        if (soundPool != null) return
        val pool = SoundPool.Builder()
            .setMaxStreams(1)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .build()
        pool.setOnLoadCompleteListener { _, _, status -> loaded = status == 0 }
        soundId = pool.load(appContext, R.raw.timer_chime, 1)
        soundPool = pool
    }

    /** Preload so the first chime is not missed. */
    fun prepare() = ensureLoaded()

    fun play() {
        if (audioManager?.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        ensureLoaded()
        if (loaded) soundPool?.play(soundId, 0.6f, 0.6f, 1, 0, 1f)
    }

    fun release() {
        soundPool?.release()
        soundPool = null
        loaded = false
    }
}
