package io.peekandpoke.klang.audio_be.ignitor

import io.peekandpoke.klang.audio_be.pulseTrapezoidShape

/**
 * One pulse/rectangular oscillator's shape state — shared by `square` / `pulze` / `triangle`.
 *
 * Holds the phase, its analog drift, and the precomputed trapezoid shape (a `±1` rectangle with
 * finite-slope flanks). [setShape] bakes the edge positions and reciprocal slopes, so [sampleAt] /
 * the hot loop stay **multiply-only**. There is no PolyBLEP: every edge is at least
 * [PULSE_MIN_FLANK_SAMPLES] samples long (a floor, like the saw's flyback), so it is always
 * band-limited and high notes soften with pitch.
 *
 * - `duty` = high-fraction `0..1` (falling edge at `phase = duty`; rising edge at the wrap).
 * - `riseFlank` / `fallFlank` = fraction of the plateau, `0` (sharpest = the sample floor) … `1`
 *   (full ramp over that plateau). Both `1` at duty `0.5` ⇒ a triangle.
 *
 * A `final` class with non-nullable `Double` fields: no boxing on Kotlin/JS, monomorphic calls.
 */
internal class PulseWaveState {
    /** Normalised phase, `0..1`. */
    var phase: Double = 0.0

    /** Analog drift, or `null`/inactive for none. */
    var drift: AnalogDrift? = null

    // Precomputed shape — readable so the hot loop can hoist them; only [setShape] mutates them.
    var fallStart: Double = 0.5; private set   // end of the high plateau / start of the fall ramp
    var fallEdge: Double = 0.5; private set     // the falling edge = duty
    var riseStart: Double = 1.0; private set    // end of the low plateau / start of the rise ramp
    var fallSlope: Double = 0.0; private set    // 2 / fall-flank-length
    var riseSlope: Double = 0.0; private set    // 2 / rise-flank-length

    /**
     * Bake the trapezoid from [duty] (high-fraction), the two flank fractions (`0..1`), and the base
     * phase increment [dt] (= freq/sr) used to apply the [PULSE_MIN_FLANK_SAMPLES] floor. Each flank
     * is `max(fraction · plateau, floor)`, clamped to its plateau — so it's always > 0 (no div-by-0,
     * no instant edge). The only divisions live here.
     */
    fun setShape(duty: Double, riseFlank: Double, fallFlank: Double, dt: Double) {
        val d = duty.coerceIn(0.01, 0.99)
        val floor = PULSE_MIN_FLANK_SAMPLES * dt
        val fd = (fallFlank * d).coerceAtLeast(floor).coerceAtMost(d)              // fall flank length
        val rd = (riseFlank * (1.0 - d)).coerceAtLeast(floor).coerceAtMost(1.0 - d) // rise flank length
        fallEdge = d
        fallStart = d - fd
        riseStart = 1.0 - rd
        fallSlope = if (fd > 0.0) 2.0 / fd else 0.0
        riseSlope = if (rd > 0.0) 2.0 / rd else 0.0
    }

    /** Bipolar trapezoid value at normalised phase [p] (the shared [pulseTrapezoidShape]). */
    fun sampleAt(p: Double): Double = pulseTrapezoidShape(p, fallStart, fallEdge, riseStart, fallSlope, riseSlope)
}
