package com.example.ui

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

class SoundHapticManager(private val context: Context) {

    private var toneGenerator: ToneGenerator? = null
    private var vibrator: Vibrator? = null

    init {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
            val vContext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                context.createAttributionContext("haptic_feedback")
            } else {
                context
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = vContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibrator = vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                vibrator = vContext.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // --- Legacy Sound Methods ---
    fun playTick() { toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 50) }
    fun playTimeOut() { toneGenerator?.startTone(ToneGenerator.TONE_CDMA_ABBR_ALERT, 200) }
    fun playClick() { toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP2, 15) }
    fun playExamTick() { toneGenerator?.startTone(ToneGenerator.TONE_DTMF_D, 50) }
    fun playExamTimeOut() { toneGenerator?.startTone(ToneGenerator.TONE_CDMA_HIGH_SS, 400) }
    fun playCorrect() { toneGenerator?.startTone(ToneGenerator.TONE_SUP_CONFIRM, 150) }
    fun playWrong() { toneGenerator?.startTone(ToneGenerator.TONE_SUP_ERROR, 150) }

    // --- Helper to execute vibration with modern attributes ---
    @android.annotation.SuppressLint("NewApi")
    private fun executeVibration(effect: VibrationEffect) {
        if (vibrator?.hasVibrator() != true) return
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                val attrs = android.os.VibrationAttributes.Builder()
                    .setUsage(android.os.VibrationAttributes.USAGE_TOUCH)
                    .build()
                vibrator?.vibrate(effect, attrs)
            } else {
                @Suppress("DEPRECATION")
                val audioAttrs = android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                @Suppress("DEPRECATION")
                vibrator?.vibrate(effect, audioAttrs)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun executeLegacyVibration(pattern: LongArray, amplitudes: IntArray) {
        if (vibrator?.hasVibrator() != true) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                executeVibration(VibrationEffect.createWaveform(pattern, amplitudes, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, -1)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // --- Semantic Haptic Methods ---

    fun hapticCorrect() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (vibrator?.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE) == true) {
                val comp = VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, 1.0f, 0)
                executeVibration(comp.compose())
                return
            }
        }
        executeLegacyVibration(longArrayOf(0, 20, 40, 20), intArrayOf(0, 150, 0, 255))
    }

    fun hapticIncorrect() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (vibrator?.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_THUD) == true) {
                val comp = VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 1.0f, 0)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 1.0f, 150)
                executeVibration(comp.compose())
                return
            }
        }
        // Distinct long double-buzz for incorrect to easily differentiate from the sharp clicks of correct.
        executeLegacyVibration(longArrayOf(0, 120, 80, 120), intArrayOf(0, 255, 0, 255))
    }

    fun hapticButtonPress() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            executeVibration(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
        } else {
            executeLegacyVibration(longArrayOf(0, 15), intArrayOf(0, 100))
        }
    }

    fun hapticLongPress() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            executeVibration(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
        } else {
            executeLegacyVibration(longArrayOf(0, 40), intArrayOf(0, 255))
        }
    }

    fun hapticToggle() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            executeVibration(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
        } else {
            executeLegacyVibration(longArrayOf(0, 15), intArrayOf(0, 100))
        }
    }

    fun hapticBookmark() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            executeVibration(VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK))
        } else {
            executeLegacyVibration(longArrayOf(0, 15, 40, 15), intArrayOf(0, 150, 0, 200))
        }
    }

    fun hapticNavigation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (vibrator?.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_LOW_TICK) == true) {
                val comp = VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, 1.0f, 0)
                executeVibration(comp.compose())
                return
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            executeVibration(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
        } else {
            executeLegacyVibration(longArrayOf(0, 10), intArrayOf(0, 80))
        }
    }

    fun hapticQuizComplete() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (vibrator?.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_QUICK_FALL, VibrationEffect.Composition.PRIMITIVE_CLICK) == true) {
                val comp = VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_FALL, 1.0f, 0)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1.0f, 100)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1.0f, 100)
                executeVibration(comp.compose())
                return
            }
        }
        executeLegacyVibration(longArrayOf(0, 30, 80, 40, 80, 50), intArrayOf(0, 200, 0, 255, 0, 255))
    }

    fun hapticAchievement() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (vibrator?.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, VibrationEffect.Composition.PRIMITIVE_CLICK) == true) {
                val comp = VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, 1.0f, 0)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1.0f, 150)
                executeVibration(comp.compose())
                return
            }
        }
        executeLegacyVibration(longArrayOf(0, 60, 100, 40), intArrayOf(0, 150, 0, 255))
    }

    fun hapticWarning() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (vibrator?.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_THUD) == true) {
                val comp = VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 1.0f, 0)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 1.0f, 150)
                executeVibration(comp.compose())
                return
            }
        }
        executeLegacyVibration(longArrayOf(0, 50, 100, 50), intArrayOf(0, 255, 0, 255))
    }

    fun hapticSuccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (vibrator?.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE) == true) {
                val comp = VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, 0.8f, 0)
                executeVibration(comp.compose())
                return
            }
        }
        executeLegacyVibration(longArrayOf(0, 40, 60, 50), intArrayOf(0, 150, 0, 255))
    }

    fun release() {
        toneGenerator?.release()
        toneGenerator = null
    }
}
