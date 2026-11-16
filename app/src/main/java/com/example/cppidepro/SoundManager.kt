package com.example.cppidepro

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack

class SoundManager {
    private val sampleRate = 44100
    
    // Generate high-quality audio buffers on startup
    private val chalkTrack = createTrack(generateChalkSound())
    private val eraserTrack = createTrack(generateEraserSound())
    private val keyboardTrack = createTrack(generateKeyboardSound())

    private var lastPlayTime = 0L

    fun playDrawSound() {
        val now = System.currentTimeMillis()
        if (now - lastPlayTime > 25) { // Throttle to 25ms for smooth scratching
            playTrack(chalkTrack)
            lastPlayTime = now
        }
    }

    fun playEraseSound() {
        val now = System.currentTimeMillis()
        if (now - lastPlayTime > 50) { // Throttle to 50ms for rubbing
            playTrack(eraserTrack)
            lastPlayTime = now
        }
    }

    fun playTypeSound() {
        val now = System.currentTimeMillis()
        if (now - lastPlayTime > 40) {
            playTrack(keyboardTrack)
            lastPlayTime = now
        }
    }

    private fun playTrack(track: AudioTrack?) {
        try {
            track?.stop()
            track?.reloadStaticData()
            track?.play()
        } catch (e: Exception) {}
    }

    private fun createTrack(buffer: ShortArray): AudioTrack? {
        return try {
            val track = AudioTrack(
                AudioManager.STREAM_MUSIC,
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                buffer.size * 2,
                AudioTrack.MODE_STATIC
            )
            track.write(buffer, 0, buffer.size)
            track
        } catch (e: Exception) {
            null
        }
    }

    // --- AUDIO SYNTHESIS ENGINE ---

    private fun generateChalkSound(): ShortArray {
        val durationMs = 30
        val numSamples = (sampleRate * (durationMs / 1000.0)).toInt()
        val buffer = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            // White noise for scratchiness
            val noise = Math.random() * 2.0 - 1.0
            // Fast decay envelope (sharp scratch)
            val envelope = 1.0 - (i.toDouble() / numSamples)
            buffer[i] = (noise * Short.MAX_VALUE * 0.15 * envelope * envelope).toInt().toShort()
        }
        return buffer
    }

    private fun generateEraserSound(): ShortArray {
        val durationMs = 60
        val numSamples = (sampleRate * (durationMs / 1000.0)).toInt()
        val buffer = ShortArray(numSamples)
        var prev = 0.0
        for (i in 0 until numSamples) {
            val noise = Math.random() * 2.0 - 1.0
            // Low-pass filter for a muffled rubber sound
            val filtered = 0.15 * noise + 0.85 * prev
            prev = filtered
            
            val envelope = 1.0 - (i.toDouble() / numSamples)
            buffer[i] = (filtered * Short.MAX_VALUE * 0.3 * envelope).toInt().toShort()
        }
        return buffer
    }

    private fun generateKeyboardSound(): ShortArray {
        val durationMs = 20
        val numSamples = (sampleRate * (durationMs / 1000.0)).toInt()
        val buffer = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            // Mix of high-frequency sine and noise for a mechanical click
            val sine = Math.sin(2.0 * Math.PI * 2500.0 * i / sampleRate)
            val noise = Math.random() * 2.0 - 1.0
            val envelope = 1.0 - (i.toDouble() / numSamples)
            val sample = (sine * 0.4 + noise * 0.6) * envelope
            buffer[i] = (sample * Short.MAX_VALUE * 0.25).toInt().toShort()
        }
        return buffer
    }

    fun release() {
        try {
            chalkTrack?.release()
            eraserTrack?.release()
            keyboardTrack?.release()
        } catch (e: Exception) {}
    }
}
