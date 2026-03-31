package io.peekandpoke.klang.audio_be

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow

// ── DSP Constants ────────────────────────────────────────────────────────────

const val TWO_PI = PI * 2.0

const val ONE_OVER_TWELVE: Double = 1.0 / 12.0

// ── DSP Utilities ────────────────────────────────────────────────────────────

/** Threshold below which filter state is flushed to zero to avoid denormal slowdowns. */
const val DENORMAL_THRESHOLD = 1e-15

/** Flushes a value to zero if it is below the denormal threshold. */
@Suppress("NOTHING_TO_INLINE")
inline fun flushDenormal(v: Double): Double = if (abs(v) < DENORMAL_THRESHOLD) 0.0 else v

/** Wraps phase into [0, period). Uses subtraction instead of modulo (%) because
 *  JS `%` on doubles is much slower than a conditional subtract for the common case
 *  where phase overshoots by exactly one period per sample. */
@Suppress("NOTHING_TO_INLINE")
inline fun wrapPhase(phase: Double, period: Double): Double {
    var p = phase
    while (p >= period) p -= period
    while (p < 0.0) p += period
    return p
}

/**
 * First-order PolyBLEP residual for anti-aliased discontinuities.
 *
 * Used in band-limited oscillators (saw, square, pulse) to smooth the signal discontinuity.
 * [t] is the normalized phase (0..1), [dt] is the normalized phase increment per sample.
 * Returns the correction to subtract from the naive waveform at the discontinuity.
 */
@Suppress("NOTHING_TO_INLINE")
inline fun polyBlep(t: Double, dt: Double): Double {
    var correction = 0.0
    if (t < dt) {
        val r = t / dt
        correction += r + r - r * r - 1.0
    }
    if (t > 1.0 - dt) {
        val r = (t - 1.0) / dt
        correction += r * r + r + r + 1.0
    }
    return correction
}

/**
 * Converts a semitone detune offset to a frequency multiplier.
 * E.g., +12 semitones = 2x frequency (one octave up).
 */
fun applySemitoneDetuneToFrequency(frequency: Double, detuneSemitones: Double): Double =
    frequency * 2.0.pow(detuneSemitones / 12.0)
