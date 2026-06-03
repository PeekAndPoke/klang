package io.peekandpoke.klang.audio_be.ignitor

import io.peekandpoke.klang.audio_be.analogSawShape

/**
 * One super-saw unison voice: phase + base increment + gain + its own analog drift, plus the
 * precomputed analog-flyback shape. **All per-sample math for the voice lives in [tick].**
 *
 * The shape is a finite-slope sawtooth (instead of an instantaneous reset): a linear **rise** −1→+1
 * over `[0, 1−rf]`, then a finite-time **flyback** +1→−1 over the last `rf` of the cycle. [setShape]
 * takes the flyback *fraction* of the cycle and bakes the reciprocal slopes, so [sampleAt] is
 * **multiply-only** (no per-sample division). The shape is zero-mean by construction (rise and
 * flyback each contribute no DC). Because callers derive `rf` from a *constant* number of samples, it
 * grows at higher pitch → high notes soften toward a triangle (the natural analog HF rolloff).
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
    // Readable so the voice-major hot loop can hoist them into locals; only [setShape] mutates them.
    var riseEnd: Double = 1.0; private set
    var riseSlope: Double = 2.0; private set
    var flySlope: Double = 0.0; private set

    /** Bake the analog-flyback shape from the flyback fraction [rf] of the cycle (`0 ≤ rf ≤ ~0.5`). */
    fun setShape(rf: Double) {
        val re = 1.0 - rf
        riseEnd = re
        riseSlope = if (re > 0.0) 2.0 / re else 0.0
        flySlope = if (rf > 0.0) 2.0 / rf else 0.0
    }

    /** Bipolar analog-flyback value at normalised phase [p] (the shared [analogSawShape], multiply-only). */
    fun sampleAt(p: Double): Double = analogSawShape(p, riseEnd, riseSlope, flySlope)
}
