package io.peekandpoke.klang.audio_be

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
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

/**
 * Wraps phase into [0, period).
 *
 * Fast path: when [phase] overshoots by ≤1 period (the common case for stable oscillators),
 * uses a single conditional subtract — JS `%` on doubles is much slower than this for the
 * normal hot-loop case. Off-path: when [phase] is way out of range (e.g. an upstream pitch
 * mod produced an extreme ratio) or non-finite, falls back to a single modulo step (and
 * recovers `0.0` for `Inf`/`NaN`) so we never enter an O(N) subtract loop or hang the audio
 * thread. See `audio/ref/numerical-safety.md`.
 */
@Suppress("NOTHING_TO_INLINE")
inline fun wrapPhase(phase: Double, period: Double): Double {
    if (!phase.isFinite()) return 0.0
    var p = phase
    if (p >= 2.0 * period || p < -period) {
        // Way out of range — use modulo so we don't loop millions of times.
        p -= period * floor(p / period)
    } else {
        // Common case: at most one overshoot in either direction.
        if (p >= period) p -= period
        else if (p < 0.0) p += period
    }
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
 * Fast modulo for values that overshoot by at most one period.
 *
 * Typical for per-sample phase accumulators where the phase increments by a small dt each sample
 * and can overshoot [period] by at most one step. Avoids JS `%` on doubles which internally
 * computes a full floating-point division (`a - floor(a/b) * b`).
 *
 * Use this instead of `value % period` in per-sample DSP loops. NOT safe for values that
 * could overshoot by more than one period — use [wrapPhase] for those cases.
 */
@Suppress("NOTHING_TO_INLINE")
inline fun smallNumFastMod(value: Double, period: Double): Double {
    return if (value >= period) value - period
    else if (value < 0.0) value + period
    else value
}

/**
 * Converts a semitone detune offset to a frequency multiplier.
 * E.g., +12 semitones = 2x frequency (one octave up).
 */
fun applySemitoneDetuneToFrequency(frequency: Double, detuneSemitones: Double): Double =
    frequency * 2.0.pow(detuneSemitones / 12.0)
