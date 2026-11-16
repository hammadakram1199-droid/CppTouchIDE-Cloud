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

    companion object {
        // Global mute state shared across the whole app
        var isMuted = false
    }

    fun playDrawSound() {
        if (isMuted) return
        val now = System.currentTimeMillis()
        if (now - lastPlayTime > 25) { 
            playTrack(chalkTrack)
            lastPlayTime = now
        }
    }

    fun playEraseSound() {
        if (isMuted) return
        val now = System.currentTimeMillis()
        if (now - lastPlayTime > 50) { 
            playTrack(eraserTrack)
            lastPlayTime = now
        }
    }

    fun playTypeSound() {
        if (isMuted) return
        val now = System.currentTimeMillis()
        if (now - lastPlayTime > 30) {
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

    // --- IMPROVED AUDIO SYNTHESIS ENGINE ---

    private fun generateChalkSound(): ShortArray {
        val durationMs = 35
        val numSamples = (sampleRate * (durationMs / 1000.0)).toInt()
        val buffer = ShortArray(numSamples)
        var lastNoise = 0.0
        
        for (i in 0 until numSamples) {
            val noise = Math.random() * 2.0 - 1.0
            // Low-pass filter to make it sound like scratching rather than harsh static
            lastNoise = 0.4 * noise + 0.6 * lastNoise
            
            // Sharp attack, quick decay
            val envelope = Math.exp(-12.0 * (i.toDouble() / numSamples))
            
            // Mix in a tiny bit of high-frequency sine for the "chalk" grit
            val grit = Math.sin(2.0 * Math.PI * 3500.0 * i / sampleRate) * 0.1
            
            val sample = (lastNoise * 0.8 + grit) * envelope
            buffer[i] = (sample * Short.MAX_VALUE * 0.2).toInt().toShort()
        }
        return buffer
    }

    private fun generateEraserSound(): ShortArray {
        val durationMs = 70
        val numSamples = (sampleRate * (durationMs / 1000.0)).toInt()
        val buffer = ShortArray(numSamples)
        var lastNoise = 0.0
        
        for (i in 0 until numSamples) {
            val noise = Math.random() * 2.0 - 1.0
            // Heavy low-pass filter for the muffled rubber rubbing sound
            lastNoise = 0.1 * noise + 0.9 * lastNoise
            
            // Slower attack and decay to simulate continuous rubbing
            val envelope = 1.0 - Math.pow((i.toDouble() / numSamples) - 0.5, 2.0) 
            
            val sample = lastNoise * envelope
            buffer[i] = (sample * Short.MAX_VALUE * 0.35).toInt().toShort()
        }
        return buffer
    }

    private fun generateKeyboardSound(): ShortArray {
        val durationMs = 25
        val numSamples = (sampleRate * (durationMs / 1000.0)).toInt()
        val buffer = ShortArray(numSamples)
        
        for (i in 0 until numSamples) {
            val time = i.toDouble() / sampleRate
            
            // High frequency "click"
            val click = Math.sin(2.0 * Math.PI * 4000.0 * time) * Math.exp(-80.0 * time)
            // Low frequency "thump" (mechanical switch)
            val thump = Math.sin(2.0 * Math.PI * 250.0 * time) * Math.exp(-30.0 * time)
            
            // Combine them and add a tiny bit of noise for realism
            val noise = (Math.random() * 2.0 - 1.0) * Math.exp(-100.0 * time) * 0.2
            val sample = click * 0.4 + thump * 0.5 + noise
            
            buffer[i] = (sample * Short.MAX_VALUE * 0.3).toInt().toShort()
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
