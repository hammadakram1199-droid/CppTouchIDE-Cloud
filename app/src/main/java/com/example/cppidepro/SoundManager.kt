package com.example.cppidepro

import android.content.Context
import android.media.AudioManager

class SoundManager(context: Context) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    fun playDrawSound() {
        // Uses system navigation sound for a soft pen scratch
        audioManager.playSoundEffect(AudioManager.FX_FOCUS_NAVIGATION_DOWN, 0.4f)
    }

    fun playEraseSound() {
        // Uses system navigation up sound for erasing
        audioManager.playSoundEffect(AudioManager.FX_FOCUS_NAVIGATION_UP, 0.6f)
    }

    fun playTypeSound() {
        // Uses system keyboard click sound
        audioManager.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD, 0.5f)
    }
}
