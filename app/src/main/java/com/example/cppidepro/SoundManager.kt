package com.example.cppidepro

import android.media.AudioManager
import android.media.ToneGenerator

class SoundManager {
    private var toneGenerator: ToneGenerator? = null

    init {
        try {
            // Use STREAM_MUSIC so it ignores system touch sound settings, at 50% volume
            toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 50)
        } catch (e: Exception) {
            toneGenerator = null
        }
    }

    fun playDrawSound() {
        // Short, quiet beep
        toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 20)
    }

    fun playEraseSound() {
        // Slightly different beep for erasing
        toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP2, 30)
    }

    fun playTypeSound() {
        // Keyboard click tone
        toneGenerator?.startTone(ToneGenerator.TONE_CDMA_KEYPAD_VOLUME_KEY_LITE, 15)
    }

    fun release() {
        toneGenerator?.release()
        toneGenerator = null
    }
}
