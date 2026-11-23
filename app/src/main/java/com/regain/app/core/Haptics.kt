package com.regain.app.core

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * One haptic vocabulary for the whole app. Every moment that buzzes means
 * something specific, so the feel stays consistent.
 *
 * Uses rich composition primitives where the motor supports them (Android 11+)
 * and falls back to short tuned waveforms otherwise.
 */
object Haptics {

    enum class Kind {
        /** Light selection change: tab switch, toggle, keypad press. */
        Tick,
        /** Correct answer, step completed. */
        Success,
        /** Wrong answer. */
        Error,
        /** The reward for choosing to quit an app. */
        Reward,
        /** Five minutes left. */
        Warning,
        /** Time's up. */
        TimesUp,
        /** Picked the phone up during focus. */
        Sad,
        /** Breathing guide: inhale peak. */
        Breath,
    }

    @Volatile private var vibrator: Vibrator? = null
    @Volatile var enabled: Boolean = true

    fun init(context: Context) {
        vibrator = if (Build.VERSION.SDK_INT >= 31) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    fun play(kind: Kind) {
        if (!enabled) return
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        val effect = composed(v, kind) ?: fallback(kind)
        runCatching { v.vibrate(effect) }
    }

    private fun composed(v: Vibrator, kind: Kind): VibrationEffect? {
        if (Build.VERSION.SDK_INT < 30) return null
        fun supported(vararg ids: Int) = v.areAllPrimitivesSupported(*ids)
        val c = VibrationEffect.startComposition()
        when (kind) {
            Kind.Tick -> {
                if (!supported(VibrationEffect.Composition.PRIMITIVE_TICK)) return null
                c.addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.6f)
            }
            Kind.Success -> {
                if (!supported(VibrationEffect.Composition.PRIMITIVE_CLICK)) return null
                c.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.5f)
                c.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.9f, 60)
            }
            Kind.Error -> {
                if (!supported(VibrationEffect.Composition.PRIMITIVE_THUD)) return null
                c.addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 0.9f)
            }
            Kind.Reward -> {
                if (!supported(
                        VibrationEffect.Composition.PRIMITIVE_QUICK_RISE,
                        VibrationEffect.Composition.PRIMITIVE_CLICK,
                    )
                ) return null
                c.addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, 0.7f)
                c.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1f, 40)
            }
            Kind.Warning -> {
                if (!supported(VibrationEffect.Composition.PRIMITIVE_CLICK)) return null
                c.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.4f)
                c.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.4f, 120)
            }
            Kind.TimesUp -> {
                if (!supported(
                        VibrationEffect.Composition.PRIMITIVE_SLOW_RISE,
                        VibrationEffect.Composition.PRIMITIVE_THUD,
                    )
                ) return null
                c.addPrimitive(VibrationEffect.Composition.PRIMITIVE_SLOW_RISE, 0.8f)
                c.addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 1f, 30)
            }
            Kind.Sad -> {
                if (!supported(VibrationEffect.Composition.PRIMITIVE_QUICK_FALL)) return null
                c.addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_FALL, 0.8f)
                c.addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_FALL, 0.4f, 90)
            }
            Kind.Breath -> {
                if (!supported(VibrationEffect.Composition.PRIMITIVE_LOW_TICK)) return null
                c.addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, 0.5f)
            }
        }
        return c.compose()
    }

    private fun fallback(kind: Kind): VibrationEffect = when (kind) {
        Kind.Tick -> VibrationEffect.createOneShot(8, 90)
        Kind.Breath -> VibrationEffect.createOneShot(12, 60)
        Kind.Success -> wave(longArrayOf(0, 14, 50, 18), intArrayOf(0, 120, 0, 200))
        Kind.Error -> wave(longArrayOf(0, 35), intArrayOf(0, 255))
        Kind.Reward -> wave(longArrayOf(0, 20, 30, 20, 30, 40), intArrayOf(0, 80, 0, 150, 0, 255))
        Kind.Warning -> wave(longArrayOf(0, 15, 110, 15), intArrayOf(0, 110, 0, 110))
        Kind.TimesUp -> wave(longArrayOf(0, 120, 60, 60), intArrayOf(0, 140, 0, 255))
        Kind.Sad -> wave(longArrayOf(0, 40, 80, 25), intArrayOf(0, 180, 0, 80))
    }

    private fun wave(t: LongArray, a: IntArray) = VibrationEffect.createWaveform(t, a, -1)
}
