/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

/**
 * One oscillator voice's state for the unified [waveTrapezoid] — the single piecewise-linear shape
 * behind saw / ramp / square / pulse / triangle and their raw (aliased) variants.
 *
 * Holds the phase, base increment, gain, analog drift, and the precomputed 4-segment shape (rise →
 * high plateau → fall → low plateau). [setSawShape] / [setPulseShape] bake the shape from a config;
 * [sampleAt] / the hot loop evaluate it via the shared multiply-only [waveTrapezoid].
 *
 * A `final` class with non-nullable `Double` fields: no boxing on Kotlin/JS, monomorphic calls.
 */
internal class WaveVoiceState {
    /** Normalised phase, `0..1`. */
    var phase: Double = 0.0

    /**
     * Base normalised phase increment per sample (pre-phaseMod, pre-drift): the per-block holder the loops read, for
     * the unison stacks and for the mono oscillator (`WaveIgnitor` writes it once per block). A holder, not an argument:
     * a non-integral double handed to a loop method V8 does not inline is a heap number per call
     * (`audio/ref/performance.md`, engine follow-up 6).
     */
    var dt: Double = 0.0

    /** Output gain (used by the unison super stack; mono ignitors leave it unused). */
    var gain: Double = 0.0

    /** The voice's gain-jitter draw (uniform 0..1), captured ONCE when the voice is added, so a
     *  mid-note voice-count change recomputes gains WITHOUT re-jittering survivors (block-framing
     *  ledger O4). 0.5 = neutral (jitter multiplier exactly 1.0). Super stack only. */
    var jitDraw: Double = 0.5

    /** Per-voice analog drift, or `null`/inactive for none. */
    var drift: AnalogDrift? = null

    // Precomputed 4-segment shape — readable so the hot loop can hoist; only the setters mutate.
    var riseEnd: Double = 0.5; private set    // end of the rise ramp
    var highEnd: Double = 0.5; private set    // end of the high plateau
    var fallEnd: Double = 1.0; private set    // end of the fall ramp (low plateau runs to 1)
    var riseSlope: Double = 2.0; private set  // 2 / rise-ramp length
    var fallSlope: Double = 0.0; private set  // 2 / fall-ramp length

    /**
     * Saw / ramp config: a linear rise over `[0, 1−rf]` then a finite flyback over `[1−rf, 1]` (no
     * plateaus). [rf] = flyback fraction (`0` = instant / raw). Reproduces the former analog saw exactly.
     */
    fun setSawShape(rf: Double) {
        val re = 1.0 - rf
        riseEnd = re
        highEnd = re                     // empty high plateau
        fallEnd = 1.0                    // fall fills the rest; empty low plateau
        riseSlope = if (re > 0.0) 2.0 / re else 0.0
        fallSlope = if (rf > 0.0) 2.0 / rf else 0.0
    }

    /**
     * Pulse / square / triangle config: a min-flank rise into a high plateau (high portion = [duty]),
     * then a min-flank fall into a low plateau. [floor] = minimum flank length in phase (`0` = raw /
     * instant edges). [riseFlank]/[fallFlank] (`0..1`) open each edge from the floor up to a full ramp;
     * both `1` at duty 0.5 ⇒ a triangle.
     */
    fun setPulseShape(duty: Double, riseFlank: Double, fallFlank: Double, floor: Double) {
        val d = duty.coerceIn(0.01, 0.99)
        val r = (riseFlank * d).coerceAtLeast(floor).coerceAtMost(d)                  // rise ramp length
        val f = (fallFlank * (1.0 - d)).coerceAtLeast(floor).coerceAtMost(1.0 - d)    // fall ramp length
        riseEnd = r
        highEnd = d
        fallEnd = d + f
        riseSlope = if (r > 0.0) 2.0 / r else 0.0
        fallSlope = if (f > 0.0) 2.0 / f else 0.0
    }

    /** Bipolar value at normalised phase [p] (the shared [waveTrapezoid]). */
    fun sampleAt(p: Double): Double = waveTrapezoid(p = p, riseEnd = riseEnd, highEnd = highEnd, fallEnd = fallEnd, riseSlope = riseSlope, fallSlope = fallSlope)
}

/**
 * The **one** piecewise-linear waveform shape behind saw / ramp / square / pulse / triangle (and their
 * raw variants). A `±1` trapezoid in four segments: a **rise** ramp `−1→+1` over `[0, riseEnd]`, a
 * **high** plateau `+1` to [highEnd], a **fall** ramp `+1→−1` to [fallEnd], then a **low** plateau `−1`.
 *
 * Slopes are precomputed by the caller (`riseSlope = 2/riseEnd`, `fallSlope = 2/fallLen`) so this is
 * **multiply-only**. A segment of zero length is simply skipped (empty plateaus → saw/triangle; empty
 * ramps → instant/raw edges). A finite-slope edge alone is NOT band-limited: a linear flank of W samples is the
 * ideal edge through a W-sample box, whose response is still 0.64 at Nyquist for W = 1 (audit finding L3-02). The
 * band-limited oscillators read the shape through [waveTrapezoidBoxed]; this plain form is the raw variants', the
 * triangle's and the LFOs'.
 *
 * Configs (see `WaveVoiceState`): saw = empty plateaus (`highEnd = riseEnd`, `fallEnd = 1`); square/pulse
 * = min-flank ramps + plateaus; triangle = `riseEnd = 0.5`, `fallEnd = 1`; raw = ramp length 0.
 */
@Suppress("NOTHING_TO_INLINE")
internal inline fun waveTrapezoid(
    p: Double,
    riseEnd: Double,
    highEnd: Double,
    fallEnd: Double,
    riseSlope: Double,
    fallSlope: Double,
): Double = when {
    p < riseEnd -> -1.0 + p * riseSlope                   // rise −1 → +1
    p < highEnd -> 1.0                                    // high plateau
    p < fallEnd -> 1.0 - (p - highEnd) * fallSlope        // fall +1 → −1
    else -> -1.0                                          // low plateau
}

/**
 * [waveTrapezoid] seen through a centred box one sample wide: the shape averaged over `[p - half, p + half]`, where
 * [half] is half the sample's own phase increment (`|inc| / 2`, pitch modulation and drift included), which
 * band-limits every corner of the shape (audit finding L3-02, KLANG-00430). On a straight stretch the average is the
 * shape itself, so only a phase within [half] of a corner gets a residual ([trapezoidCornerResidual]); everywhere else
 * this costs a few compares.
 *
 * The edge's kernel is the flank's box times this one: the saw's one-sample flyback becomes a triangle two samples
 * wide, which is exactly the two-point PolyBLEP (Välimäki and Huovilainen): measured on the bare saw, 22 to 29 dB less
 * aliasing below 5 kHz from 110 Hz to 7 kHz, at 1.3 dB of droop at 10 kHz where the box alone had 0.6. The square's
 * flank is one sample too (`PULSE_MIN_FLANK_SAMPLES`, the saw's edge width since 2026-11-11; it was two samples, a
 * trapezoid kernel three samples wide, 3.3 dB at 10 kHz), so its kernel is the saw's: -1.4 dB at 11 kHz where the saw
 * has -1.2. Brighter, its total non-harmonic energy rose 3 to 12 dB (the aliasing meter, 110 Hz to 7 kHz), all of it
 * above 5 kHz: below 5 kHz it is where it was within 0.7 dB. The flank is sized from the block's unmodulated increment; the box follows the per-sample one, so an
 * upward pitch stage widens the box with the step (the saw at +12 semitones of pitch stage, 1244.5 Hz: -50.6 dB below 5
 * kHz where it was -26.4).
 *
 * [half] 0 reads the plain [waveTrapezoid] bit for bit (no corner lies closer than 0, so no residual applies). A NaN
 * [half] fails every compare below and reads as the plain shape too (NaN-guard). The oscillators that stay plain call
 * [waveTrapezoid] itself (a loop-invariant choice at the call: the corner tests cost them nothing). The caller keeps
 * [half] at most 0.25 (a box of half a cycle), so each corner is met once.
 */
@Suppress("NOTHING_TO_INLINE")
internal inline fun waveTrapezoidBoxed(
    p: Double,
    half: Double,
    riseEnd: Double,
    highEnd: Double,
    fallEnd: Double,
    riseSlope: Double,
    fallSlope: Double,
): Double {
    val v: Double
    val near: Boolean

    // The plain shape, and whether a corner of p's own segment lies within `half`: the segment's ends are the corners
    // nearest to p, so when neither is near, none is.
    if (p < riseEnd) {
        v = -1.0 + p * riseSlope
        near = p < half || riseEnd - p < half
    } else if (p < highEnd) {
        v = 1.0
        near = p - riseEnd < half || highEnd - p < half
    } else if (p < fallEnd) {
        v = 1.0 - (p - highEnd) * fallSlope
        near = p - highEnd < half || fallEnd - p < half
    } else {
        v = -1.0
        near = p - fallEnd < half || 1.0 - p < half
    }

    if (near) {
        return v + trapezoidCornerResidual(p = p, half = half, riseEnd = riseEnd, highEnd = highEnd, fallEnd = fallEnd, riseSlope = riseSlope, fallSlope = fallSlope)
    }

    return v
}

/**
 * What the centred box of [waveTrapezoidBoxed] adds at phase [p]: each of the shape's four corners (the rise starts at 0,
 * ends at `riseEnd`; the fall starts at `highEnd`, ends at `fallEnd`) contributes its change of slope times the box's
 * residual on a kink ([boxKink]), at the corner's distance from [p] taken round the cycle. A corner shared by two
 * segments (the saw's `riseEnd == highEnd`, its `fallEnd == 1`, which is the corner at 0) sums its two parts. Called
 * only near a corner, so [half] is above 0 here: a phase in `[0, 1)` lies within [half] of a corner only when [half]
 * is positive.
 */
internal fun trapezoidCornerResidual(
    p: Double,
    half: Double,
    riseEnd: Double,
    highEnd: Double,
    fallEnd: Double,
    riseSlope: Double,
    fallSlope: Double,
): Double {
    val inv = 0.25 / half

    return riseSlope * (boxKink(x = cornerOffset(p), half = half, inv = inv) - boxKink(x = cornerOffset(p - riseEnd), half = half, inv = inv)) +
        fallSlope * (boxKink(x = cornerOffset(p - fallEnd), half = half, inv = inv) - boxKink(x = cornerOffset(p - highEnd), half = half, inv = inv))
}

/** A phase difference in `(-1, 1)` taken to the nearest turn: `[-0.5, 0.5)`. */
private fun cornerOffset(x: Double): Double = if (x >= 0.5) x - 1.0 else if (x < -0.5) x + 1.0 else x

/**
 * The residual of the unit kink `max(x, 0)` through a centred box of width `2 half`: the box's average
 * `(x + half)^2 / (4 half)` less the kink itself, within [half] of the corner, else 0. [inv] is `1 / (4 half)`.
 */
private fun boxKink(x: Double, half: Double, inv: Double): Double {
    if (x > -half && x < half) {
        val u = x + half

        return u * u * inv - (if (x > 0.0) x else 0.0)
    }

    return 0.0
}

/**
 * The largest half box [waveTrapezoidBoxed] is given: half a cycle wide, reached at an increment of half a cycle per
 * sample (Nyquist). Above it the box stays at half a cycle; such a tone is aliased whatever the box.
 */
internal const val TRAPEZOID_BOX_HALF_MAX: Double = 0.25
