package io.peekandpoke.klang.audio_be

import kotlin.math.abs
import kotlin.math.sin

/**
 * Collection of optimized shaping functions for distortion and saturation.
 *
 * Each function applies a different transfer curve (waveshaper) to an input sample 'x'.
 * These are used to create various "colors" of distortion, from warm saturation to harsh digital clipping.
 */
@Suppress("NOTHING_TO_INLINE", "unused")
object ClippingFuncs {

    /**
     * Fast approximation of tanh using a Padé approximant.
     *
     * **Sonic Character:** Warm, analog-style saturation. Smoothly rounds off peaks.
     * **Use Case:** General purpose overdrive, guitar-pedal style distortion.
     * **Performance:** Significantly faster than kotlin.math.tanh (~5x) with negligible error for audio.
     * **Range:** Accurate within [-3, 3], clamped outside that range.
     */
    inline fun fastTanh(x: Double): Double {
        if (x < -3.0) return -1.0
        if (x > 3.0) return 1.0
        val x2 = x * x
        return x * (27.0 + x2) / (27.0 + 9.0 * x2)
    }

    /**
     * Hard Clipping (Digital Clip).
     *
     * **Sonic Character:** Harsh, aggressive, "buzzy". Introduces significant odd harmonics and aliasing at high drive.
     * **Use Case:** Industrial sounds, "bit-crush" adjacent effects, or as a safety brick-wall limiter.
     * **Math:** Simply clamps the signal between -1.0 and 1.0.
     */
    inline fun hardClip(x: Double): Double {
        return x.coerceIn(-1.0, 1.0)
    }

    /**
     * Rational Soft Clipper (Algebraic Sigmoid).
     *
     * **Sonic Character:** Very similar to tanh but slightly "softer" knee.
     * **Use Case:** A cheaper alternative to fastTanh if CPU is extremely tight, though the difference is minimal on modern CPUs.
     * **Math:** x / (1 + |x|)
     */
    inline fun softClip(x: Double): Double {
        return x / (1.0 + abs(x))
    }

    /**
     * Cubic Soft Clipper (Tube Simulation).
     *
     * **Sonic Character:** Emulates the saturation of a vacuum tube amplifier.
     * Compresses dynamic range gently before clipping. Emphasizes the 3rd harmonic.
     * **Use Case:** Warmth, boosting perceived loudness without obvious distortion.
     * **Math:** f(x) = x - x^3/3 (clamped at x=1.5 to prevent foldover)
     */
    inline fun cubicClip(x: Double): Double {
        if (x < -1.5) return -1.0
        if (x > 1.5) return 1.0
        // standard cubic soft clip is often defined up to 1.0, but extending it to 1.5 gives a bit more headroom
        // normalizing factor 1.5 - 1.5^3/3 = 1.5 - 3.375/3 = 1.5 - 1.125 = 0.375 ??
        // Let's stick to the standard range [-1, 1] where derivative becomes 0
        val xc = x.coerceIn(-1.0, 1.0)
        // 1.5x - 0.5x^3 allows it to reach 1.0 at input 1.0
        return 1.5 * xc - 0.5 * xc * xc * xc
    }

    /**
     * Sine Wavefolder.
     *
     * **Sonic Character:** Metallic, sci-fi, "FM-like".
     * Instead of clipping peaks, it maps them back into the range using a sine function.
     * **Use Case:** West-coast synthesis, experimental sounds, metallic basses.
     * **Math:** sin(x)
     */
    inline fun sineFold(x: Double): Double {
        return sin(x)
    }

    /**
     * Standard Hyperbolic Tangent.
     *
     * **Sonic Character:** The "gold standard" for soft clipping math.
     * **Use Case:** Reference implementation. Use fastTanh for production code.
     */
    inline fun nativeTanh(x: Double): Double {
        return kotlin.math.tanh(x)
    }
}
