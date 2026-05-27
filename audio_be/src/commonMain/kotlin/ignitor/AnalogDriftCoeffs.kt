package io.peekandpoke.klang.audio_be.ignitor

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.random.Random

// ─────────────────────────────────────────────────────────────────────────────
// Shared constants and helpers for [AnalogDrift] (mono) and [PolyAnalogDrift]
// (per-voice). Both implement the same two-layer OU drift model — fast
// one-pole jitter + slow Ornstein–Uhlenbeck — but differ in storage layout
// (scalar vs. per-voice arrays). The tuning constants and the coefficient math
// live here so they're defined in exactly one place.
//
// `@PublishedApi internal` on the constants is required because
// [PolyAnalogDrift.advanceAll] is a public `inline fun` and the body expands at
// the call site on Kotlin/JS — direct `private`/plain-`internal` references
// would fail the visibility check there.
// ─────────────────────────────────────────────────────────────────────────────

/** Fast-jitter time constant: ~50 ms — micro-wobble of a stable VCO. */
@PublishedApi
internal const val ANALOG_FAST_TAU_SEC: Double = 0.05

/** Slow-drift time constant: ~10 s — lazy pitch wander. */
@PublishedApi
internal const val ANALOG_SLOW_TAU_SEC: Double = 10.0

/** Mean-reversion strength as a fraction of α. 0 = random walk, 1 = strong pull. */
@PublishedApi
internal const val ANALOG_MEAN_REVERSION_RATIO: Double = 0.5

/** Target peak deviation of the fast layer, in cents, per unit `analog`. */
@PublishedApi
internal const val ANALOG_FAST_PEAK_CENTS: Double = 0.2

/** Target peak deviation of the slow layer, in cents, per unit `analog`. */
@PublishedApi
internal const val ANALOG_SLOW_PEAK_CENTS: Double = 0.8

/** One cent as a multiplicative offset: `2^(1/1200) - 1`. */
@PublishedApi
internal const val ANALOG_CENT_PER_MUL: Double = 5.7780e-4

/** "Peak" = how many σ we treat as the perceptual envelope. */
@PublishedApi
internal const val ANALOG_PEAK_SIGMAS: Double = 3.0

/** σ of uniform [-1, 1]: `1/√3`. */
@PublishedApi
internal const val ANALOG_SIGMA_X: Double = 0.5773502691896257

/** `1 / Int.MAX_VALUE` — maps a signed Int to ≈ [-1, 1]. */
@PublishedApi
internal const val ANALOG_INT_INV: Double = 1.0 / 2147483647.0

/**
 * Computed analog-drift coefficients for the chosen [analog] amount and
 * [sampleRate]. Holds α/β for both layers, output scales, and the
 * steady-state σ used by callers to seed initial state.
 *
 * Two callers — [AnalogDrift] (mono) and [PolyAnalogDrift] (poly) — read
 * the fields once during their `init`, then store the values in their own
 * fields for the hot loop. Keeping this class plain (no inline) is fine —
 * it's only touched at construction.
 */
internal class AnalogDriftCoeffs(analog: Double, sampleRate: Int) {
    val alphaFast: Double
    val alphaSlow: Double
    val betaSlow: Double
    val scaleFast: Double
    val scaleSlow: Double
    val sigmaYFast: Double
    val sigmaYSlow: Double

    init {
        val sr = sampleRate.toDouble()
        alphaFast = 1.0 / (ANALOG_FAST_TAU_SEC * sr)
        alphaSlow = 1.0 / (ANALOG_SLOW_TAU_SEC * sr)
        betaSlow = alphaSlow * ANALOG_MEAN_REVERSION_RATIO

        // Steady-state RMS of each smoother given uniform [-1, 1] white noise
        // input (σ²_x = 1/3). One-pole LPF: σ²_y ≈ α/2 × σ²_x.
        //                            OU: σ²_y ≈ α²/(2(α+β)) × σ²_x.
        sigmaYFast = sqrt(alphaFast / 2.0) * ANALOG_SIGMA_X
        sigmaYSlow = alphaSlow / sqrt(2.0 * (alphaSlow + betaSlow)) * ANALOG_SIGMA_X

        // Scale = analog × target_cents × cent_to_mul / (3σ). 3σ ≈ peak amplitude.
        scaleFast = analog * ANALOG_FAST_PEAK_CENTS * ANALOG_CENT_PER_MUL / (ANALOG_PEAK_SIGMAS * sigmaYFast)
        scaleSlow = analog * ANALOG_SLOW_PEAK_CENTS * ANALOG_CENT_PER_MUL / (ANALOG_PEAK_SIGMAS * sigmaYSlow)
    }
}

/** Standard-normal sample via Box-Muller. Two `rng.nextDouble()` calls. */
internal fun analogDriftGaussian(rng: Random): Double {
    val u1 = rng.nextDouble().coerceAtLeast(1e-12)
    val u2 = rng.nextDouble()
    return sqrt(-2.0 * ln(u1)) * cos(2.0 * PI * u2)
}
