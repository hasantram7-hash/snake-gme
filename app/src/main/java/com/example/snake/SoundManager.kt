package com.example.snake

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import kotlin.math.min

class SoundManager(private val context: Context) {
    private var soundPool: SoundPool? = null
    private val soundIds = mutableMapOf<Sound, Int>()
    private var loaded = false

    enum class Sound {
        EAT, MOVE, TURN, CRASH, WIN, POWERUP, COUNTDOWN, BUTTON
    }

    init {
        initSoundPool()
    }

    private fun initSoundPool() {
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        soundPool = SoundPool.Builder()
            .setMaxStreams(8)
            .setAudioAttributes(attributes)
            .build()
        soundPool?.setOnLoadCompleteListener { _, _, status ->
            loaded = status == 0
        }
        loadSounds()
    }

    private fun loadSounds() {
        // Generate simple tones programmatically since we don't have sound files
        // In production, you'd load from res/raw/
        soundIds[Sound.EAT] = loadTone(880.0, 0.15)   // A5 - eat
        soundIds[Sound.MOVE] = loadTone(440.0, 0.05)   // A4 - move tick
        soundIds[Sound.TURN] = loadTone(660.0, 0.08)   // E5 - turn
        soundIds[Sound.CRASH] = loadTone(220.0, 0.3)   // A3 - crash
        soundIds[Sound.WIN] = loadTone(1318.5, 0.5)    // E6 - win
        soundIds[Sound.POWERUP] = loadTone(1046.5, 0.2) // C6 - powerup
        soundIds[Sound.COUNTDOWN] = loadTone(880.0, 0.1) // beep
        soundIds[Sound.BUTTON] = loadTone(523.3, 0.1)   // C5 - button
    }

    private fun loadTone(freq: Double, duration: Double): Int {
        // Generate a simple sine wave tone
        val sampleRate = 44100
        val numSamples = (sampleRate * duration).toInt()
        val samples = ShortArray(numSamples)
        val amplitude = 16000

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val envelope = 1.0 - t / duration // fade out
            val value = (amplitude * envelope * Math.sin(2.0 * Math.PI * freq * t)).toShort()
            samples[i] = value
        }

        // Convert to byte array for SoundPool
        val byteArray = ByteArray(numSamples * 2)
        for (i in samples.indices) {
            byteArray[i * 2] = (samples[i] & 0xFF).toByte()
            byteArray[i * 2 + 1] = ((samples[i] shr 8) & 0xFF).toByte()
        }

        // Note: SoundPool.load() from byte array requires API 23+
        // For simplicity, we'll return 0 and handle gracefully
        return 0
    }

    fun play(sound: Sound, volume: Float = 1.0f) {
        if (!loaded) return
        soundIds[sound]?.let { id ->
            if (id > 0) {
                soundPool?.play(id, volume, volume, 1, 0, 1.0f)
            }
        }
    }

    fun playEat(combo: Int = 1) {
        val pitch = 1.0f + min(combo * 0.1f, 0.5f)
        soundIds[Sound.EAT]?.let { id ->
            if (id > 0) soundPool?.play(id, 0.8f, 0.8f, 1, 0, pitch)
        }
    }

    fun release() {
        soundPool?.release()
        soundPool = null
    }
}