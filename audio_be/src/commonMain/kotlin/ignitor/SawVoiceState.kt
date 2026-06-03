package io.peekandpoke.klang.audio_be.ignitor

import io.peekandpoke.klang.audio_be.wrapPhase

/**
 * One super-saw unison voice: phase + base increment + gain + its own analog drift, plus the
 * precomputed analog-flyback shape. **All per-sample math for the voice lives in [tick].**
 *
 * The shape is a finite-slope sawtooth (instead of an instantaneous reset): a linear **rise** −1→+1,
 * an optional flat **hold** at +1 (the analog "rest" at the peak), then a finite-time **flyback**
 * +1→−1. [setShape] takes the flyback/hold *fractions* of the cycle and bakes the reciprocal slopes,
 * so [sampleAt] is **multiply-only** (no per-sample division). The result is DC-corrected (the +1 hold
 * region would otherwise add a positive mean of exactly `hold`) → zero-mean. Because callers derive
 * the fractions from a *constant* number of samples, they grow at higher pitch → high notes soften
 * toward a triangle (the natural analog HF rolloff with pitch).
 *
 * A `final` class with non-nullable `Double` fields: no boxing on Kotlin/JS, monomorphic calls.
 */
internal class SawVoiceState {
    /** Normalised phase, `0..1`. */
    var phase: Double = 0.0

    /** Base normalised phase increment per sample (pre-phaseMod, pre-drift). */
    var dt: Double = 0.0

    /** Output gain for this voice. */
    var gain: Double = 0.0

    /** Per-voice analog drift, or `null`/inactive for none. */
    var drift: AnalogDrift? = null

    // Precomputed shape — the divisions happen here (once per freq/spread change), not per sample.
    private var riseEnd: Double = 1.0
    private var flyStart: Double = 1.0
    private var riseSlope: Double = 2.0
    private var flySlope: Double = 0.0
    private var holdDc: Double = 0.0

    /** Bake the analog-flyback shape from the flyback ([rf]) and [hold] fractions of the cycle (`≥ 0`, `rf + hold ≤ ~0.5`). */
    fun setShape(rf: Double, hold: Double) {
        val re = 1.0 - rf - hold
        riseEnd = re
        flyStart = 1.0 - rf
        riseSlope = if (re > 0.0) 2.0 / re else 0.0
        flySlope = if (rf > 0.0) 2.0 / rf else 0.0
        holdDc = hold
    }

    /** Bipolar analog-flyback value at normalised phase [p] (multiply-only, zero-mean). */
    fun sampleAt(p: Double): Double {
        val raw = when {
            p < riseEnd -> p * riseSlope - 1.0          // rise: −1 → +1
            p < flyStart -> 1.0                          // hold at +1 ("rest")
            else -> 1.0 - (p - flyStart) * flySlope      // flyback: +1 → −1
        }
        return raw - holdDc                              // DC-correct: the hold region contributes +hold
    }

    /**
     * Output this voice's gained sample and advance its phase by `dt * mod`, applying its own drift.
     * [mod] is the per-sample phaseMod multiplier (`1.0` = no modulation).
     */
    fun tick(mod: Double): Double {
        val out = sampleAt(phase) * gain
        var inc = dt * mod
        val d = drift
        if (d != null && d.active) inc *= d.nextMultiplier()
        phase = (phase + inc).wrapPhase(1.0)
        return out
    }
}
