package io.peekandpoke.klang.audio_be.ignitor

import kotlin.random.Random

/**
 * Analog oscillator drift — two-timescale Ornstein–Uhlenbeck process.
 *
 * Models the micro-pitch instabilities of real analog VCOs by layering:
 * - a **fast jitter** (~50 ms time constant, ±0.2 cents per unit `analog`) — the
 *   constant micro-wobble of a real oscillator;
 * - a **slow OU drift** (~10 s time constant with mild mean reversion, ±0.8
 *   cents per unit `analog`) — the lazy, breathing pitch wander that makes a
 *   sustained note feel alive rather than perfectly stable.
 *
 * Total drift peak ≈ ±`analog` cents (clean linear mapping). Tuning constants
 * live in [AnalogDriftCoeffs] — single source of truth shared with [PolyAnalogDrift].
 *
 * Both layers are smoothed white noise (one-pole on white for the fast layer,
 * Ornstein–Uhlenbeck for the slow one), which is closer to the physical
 * statistics of analog component noise than Perlin's lattice-based field.
 *
 * State is seeded at construction from the steady-state Gaussian distribution
 * of each smoother, so drift is immediate — no ramp-up after note-on.
 *
 * Per-sample cost: 3 xorshift ops + 4 muls + 4 adds. No allocations, no `Random.nextX()`
 * dispatch, no perm-table lookups.
 *
 * When [analog] is 0.0, [active] is false. Oscillators should branch on [active]
 * to skip the drift path entirely (zero overhead).
 *
 * Usage in oscillator hot loop:
 * ```
 * phase += inc * drift.nextMultiplier()
 * ```
 */
class AnalogDrift(analog: Double, sampleRate: Int) {
    /** Whether analog drift is active. Check this to skip the drift path entirely. */
    val active: Boolean = analog > 0.0

    private val alphaFast: Double
    private val alphaSlow: Double
    private val betaSlow: Double
    private val scaleFast: Double
    private val scaleSlow: Double

    private var yFast: Double
    private var ySlow: Double
    private var rngState: Int

    init {
        val coeffs = AnalogDriftCoeffs(analog, sampleRate)
        alphaFast = coeffs.alphaFast
        alphaSlow = coeffs.alphaSlow
        betaSlow = coeffs.betaSlow
        scaleFast = coeffs.scaleFast
        scaleSlow = coeffs.scaleSlow

        // Seed from steady-state Gaussian so drift is immediate (no ramp-up).
        yFast = analogDriftGaussian(Random) * coeffs.sigmaYFast
        ySlow = analogDriftGaussian(Random) * coeffs.sigmaYSlow

        var s = Random.nextInt()
        if (s == 0) s = 1 // xorshift32 doesn't tolerate a zero seed
        rngState = s
    }

    /**
     * Returns the next phase-increment multiplier. Centred on 1.0 with a
     * fast-jitter + slow-drift Gaussian-like distribution.
     *
     * Only call when [active] is true.
     */
    fun nextMultiplier(): Double {
        // xorshift32 — inline, no Random dispatch
        var s = rngState
        s = s xor (s shl 13)
        s = s xor (s ushr 17)
        s = s xor (s shl 5)
        rngState = s
        val x = s * ANALOG_INT_INV // uniform ≈ [-1, 1]

        val newYFast = yFast + alphaFast * (x - yFast)
        val newYSlow = ySlow + alphaSlow * (x - ySlow) - betaSlow * ySlow
        yFast = newYFast
        ySlow = newYSlow

        return 1.0 + newYFast * scaleFast + newYSlow * scaleSlow
    }
}