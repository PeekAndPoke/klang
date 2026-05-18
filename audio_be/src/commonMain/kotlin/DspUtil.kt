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

/**
 * Returns `0.0` if this value is NaN, otherwise the value unchanged.
 *
 * Used to sterilise sample values before they enter IIR / FIR state where a
 * single NaN would propagate forever (IIR: state becomes NaN; FIR: NaN smears
 * across the entire delay line until it scrolls out). Encodes the engine-wide
 * `// NaN-guard (NaN ≠ NaN)` idiom — the IEEE-754 property that `NaN != NaN`
 * is the cheapest finite NaN test.
 */
@Suppress("NOTHING_TO_INLINE")
inline fun Double.nanGuard(): Double = if (this != this) 0.0 else this

/** Flushes a value to zero if it is below the denormal threshold. */
@Suppress("NOTHING_TO_INLINE")
inline fun Double.flushDenormal(): Double = if (abs(this) < DENORMAL_THRESHOLD) 0.0 else this

/**
 * Wraps this phase into `[0, period)`.
 *
 * Fast path: when this phase overshoots by ≤1 period (the common case for stable oscillators),
 * uses a single conditional subtract — JS `%` on doubles is much slower than this for the
 * normal hot-loop case. Off-path: when this phase is way out of range (e.g. an upstream pitch
 * mod produced an extreme ratio) or non-finite, falls back to a single modulo step (and
 * recovers `0.0` for `Inf`/`NaN`) so we never enter an O(N) subtract loop or hang the audio
 * thread. See `audio/ref/numerical-safety.md`.
 */
@Suppress("NOTHING_TO_INLINE")
inline fun Double.wrapPhase(period: Double): Double {
    if (!this.isFinite()) return 0.0
    var p = this
    if (p >= 2.0 * period || p < -period) {
        // Way out of range — use modulo so we don't loop millions of times.
        p -= period * floor(p / period)
    } else {
        // Common case: at most one overshoot in either direction.
        if (p >= period) {
            p -= period
        } else if (p < 0.0) p += period
    }
    return p
}

/**
 * First-order PolyBLEP residual for anti-aliased discontinuities.
 *
 * Used in band-limited oscillators (saw, square, pulse) to smooth the signal discontinuity.
 * The receiver `t` is the normalised phase (`0..1`); [dt] is the normalised phase increment
 * per sample. Returns the correction to subtract from the naive waveform at the discontinuity.
 */
@Suppress("NOTHING_TO_INLINE")
inline fun Double.polyBlep(dt: Double): Double {
    val t = this
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
 * Typical for per-sample phase accumulators where the phase increments by a small dt each
 * sample and can overshoot [period] by at most one step. Avoids JS `%` on doubles which
 * internally computes a full floating-point division (`a - floor(a/b) * b`).
 *
 * Use this instead of `value % period` in per-sample DSP loops. NOT safe for values that
 * could overshoot by more than one period — use [wrapPhase] for those cases.
 */
@Suppress("NOTHING_TO_INLINE")
inline fun Double.smallNumFastMod(period: Double): Double {
    return if (this >= period) {
        this - period
    } else if (this < 0.0) {
        this + period
    } else {
        this
    }
}

/**
 * Treats this value as a frequency in Hz and returns the frequency shifted by
 * [detuneSemitones] semitones. E.g. `440.0.applySemitoneDetuneToFrequency(12.0) == 880.0`.
 */
fun Double.applySemitoneDetuneToFrequency(detuneSemitones: Double): Double =
    this * 2.0.pow(detuneSemitones / 12.0)