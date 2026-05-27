package io.peekandpoke.klang.audio_be.ignitor

import kotlin.random.Random

/**
 * Polyphonic analog drift — independent two-timescale Ornstein–Uhlenbeck
 * drift per unison voice.
 *
 * The single-voice [AnalogDrift] is fine for mono oscillators, but in
 * unison oscillators (SuperSaw, SuperSine, etc.) a single shared drift
 * causes all unison voices to wobble in lockstep. That kills the analog
 * feel — real analog supersaws sound wide and organic precisely because
 * each VCO has its own independent pitch instability.
 *
 * This class allocates per-voice state for two layered drift sources:
 * - a **fast jitter** (~50 ms time constant, ±0.2 cents per unit `analog`);
 * - a **slow OU drift** (~10 s with mild mean reversion, ±0.8 cents per
 *   unit `analog`).
 *
 * Total drift peak ≈ ±`analog` cents per voice (clean linear mapping).
 * Tuning constants live in [AnalogDriftCoeffs] — single source of truth
 * shared with the mono [AnalogDrift].
 *
 * Each voice has its own RNG state and its own smoother state, so voices
 * decorrelate from sample 0 and never re-sync. State is seeded at
 * construction from the steady-state Gaussian distribution so drift is
 * immediate after note-on (no ramp-up).
 *
 * Usage in the audio hot path:
 *
 * ```kotlin
 * val muls = drift.multipliers           // hoist reference once
 * for (i in offset until end) {
 *     drift.advanceAll()                 // updates muls[0..v-1] in place
 *     for (n in 0 until v) {
 *         val dt = baseDt[n] * muls[n]
 *         // … use dt for voice n …
 *     }
 * }
 * ```
 *
 * [advanceAll] is `inline` so on Kotlin/JS its body expands at the call
 * site — zero method dispatch in the per-sample loop. The internal state
 * is marked `@PublishedApi internal` to allow inlining without leaking
 * those fields to the public surface.
 *
 * When [active] is false, callers should skip [advanceAll] — multipliers
 * stay at 1.0 (their initial value), so any later `dt * multipliers[n]`
 * is a no-op multiply that the JIT/optimizer can eliminate.
 *
 * Per-sample-per-voice cost: 3 xorshift ops + 4 muls + 4 adds. No allocations.
 */
class PolyAnalogDrift(
    analog: Double,
    voiceCount: Int,
    sampleRate: Int,
    rng: Random = Random,
) {
    /** Whether analog drift is active. When false, [advanceAll] need not be called. */
    val active: Boolean = analog > 0.0

    /**
     * Per-voice phase-increment multipliers. Populated by [advanceAll].
     *
     * Initialised to `1.0` so callers that skip [advanceAll] (inactive case)
     * can still multiply by these without effect.
     */
    val multipliers: DoubleArray = DoubleArray(voiceCount) { 1.0 }

    @PublishedApi
    internal val alphaFast: Double
    @PublishedApi
    internal val alphaSlow: Double
    @PublishedApi
    internal val betaSlow: Double
    @PublishedApi
    internal val scaleFast: Double
    @PublishedApi
    internal val scaleSlow: Double

    /** Per-voice state of the fast jitter smoother. */
    @PublishedApi
    internal val yFast: DoubleArray

    /** Per-voice state of the slow OU drift. */
    @PublishedApi
    internal val ySlow: DoubleArray

    /** Per-voice xorshift32 RNG state. Non-zero by construction. */
    @PublishedApi
    internal val rngState: IntArray

    init {
        val coeffs = AnalogDriftCoeffs(analog, sampleRate)
        alphaFast = coeffs.alphaFast
        alphaSlow = coeffs.alphaSlow
        betaSlow = coeffs.betaSlow
        scaleFast = coeffs.scaleFast
        scaleSlow = coeffs.scaleSlow

        // Seed each voice from the steady-state Gaussian. Slow layer would
        // otherwise need ~30 seconds to reach steady state.
        yFast = DoubleArray(voiceCount) { analogDriftGaussian(rng) * coeffs.sigmaYFast }
        ySlow = DoubleArray(voiceCount) { analogDriftGaussian(rng) * coeffs.sigmaYSlow }

        rngState = IntArray(voiceCount) {
            var s = rng.nextInt()
            if (s == 0) s = 1 // xorshift32 doesn't tolerate a zero seed
            s
        }
    }

    /**
     * Advance the drift for all voices by one sample. Populates [multipliers].
     *
     * `inline` so on Kotlin/JS the body expands at the caller, removing
     * per-sample method dispatch in the hot loop. The JVM JIT inlines simple
     * final methods regardless.
     *
     * Only meaningful to call when [active] is true.
     */
    @Suppress("NOTHING_TO_INLINE")
    inline fun advanceAll() {
        val muls = multipliers
        val n = muls.size
        val yF = yFast
        val yS = ySlow
        val states = rngState
        val aF = alphaFast
        val aS = alphaSlow
        val bS = betaSlow
        val sF = scaleFast
        val sS = scaleSlow

        for (idx in 0 until n) {
            // xorshift32 — inline, no Random dispatch
            var s = states[idx]
            s = s xor (s shl 13)
            s = s xor (s ushr 17)
            s = s xor (s shl 5)
            states[idx] = s
            val x = s * ANALOG_INT_INV // uniform ≈ [-1, 1]

            val newYF = yF[idx] + aF * (x - yF[idx])
            val newYS = yS[idx] + aS * (x - yS[idx]) - bS * yS[idx]
            yF[idx] = newYF
            yS[idx] = newYS
            muls[idx] = 1.0 + newYF * sF + newYS * sS
        }
    }
}
