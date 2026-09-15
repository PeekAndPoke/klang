/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

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
 * live in [AnalogDriftCoeffs] — single source of truth shared with [DriftLanes], which stacks
 * these lanes for the multi-voice oscillators.
 *
 * Both layers are smoothed white noise (one-pole on white for the fast layer,
 * Ornstein–Uhlenbeck for the slow one), which is closer to the physical
 * statistics of analog component noise than Perlin's lattice-based field.
 *
 * The fast layer is seeded from its steady-state Gaussian (it settles in ~50 ms,
 * so the micro-shimmer is immediate). The slow layer is seeded at CENTRE so every
 * note attacks in tune; its lazy drift only develops if the note is held long
 * enough. Seeding the slow layer at steady-state (the old behaviour) turned short
 * notes into per-note random detune — each note stuck at its seeded offset for its
 * whole (short) life, which read as wandering intonation on melodic lines.
 *
 * A step costs 3 xorshift ops + 4 muls + 4 adds. No allocations, no `Random.nextX()`
 * dispatch, no perm-table lookups.
 *
 * When [analog] is 0.0, [active] is false. Oscillators should branch on [active]
 * to skip the drift path entirely (zero overhead).
 *
 * [stepRate] is how often the caller steps the lane, per second: the coefficients follow it, so
 * a lane stepped once per block (the oscillators since 2026-09-15, at
 * [analogDriftStepRate]) wanders with the same time constants and depth as one stepped per
 * sample. The block form is [beginBlock] once, then a linear ramp from [blockStart] to
 * [blockEnd] across the block's samples:
 * ```
 * drift.beginBlock()
 * var m = drift.blockStart
 * val dm = (drift.blockEnd - m) / length
 * for (...) { phase += inc * m; m += dm }
 * ```
 * [nextMultiplier] is the raw step; the filter drift (`FilterModRenderer`) holds one per block
 * without a ramp.
 */
class AnalogDrift(analog: Double, stepRate: Int, rng: Random = Random) {
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

    /** The multiplier this block starts at: where the previous block ended. See [beginBlock]. */
    var blockStart: Double = 1.0
        private set

    /** The multiplier this block ends at, one step past [blockStart]. See [beginBlock]. */
    var blockEnd: Double = 1.0
        private set

    init {
        val coeffs = AnalogDriftCoeffs(analog, stepRate)
        alphaFast = coeffs.alphaFast
        alphaSlow = coeffs.alphaSlow
        betaSlow = coeffs.betaSlow
        scaleFast = coeffs.scaleFast
        scaleSlow = coeffs.scaleSlow

        // Fast layer: seed from its steady-state Gaussian — it settles within ~50 ms,
        // so the immediate micro-shimmer is harmless. Slow layer: seed at CENTRE (0.0)
        // so the note attacks in tune and only drifts if held (see class KDoc).
        yFast = analogDriftGaussian(rng) * coeffs.sigmaYFast
        ySlow = 0.0

        var s = rng.nextInt()
        if (s == 0) s = 1 // xorshift32 doesn't tolerate a zero seed
        rngState = s
        // The ramp starts where the seeded state sits, so the first block moves from it, not from 1.
        blockEnd = 1.0 + yFast * scaleFast + ySlow * scaleSlow
        blockStart = blockEnd
    }

    /**
     * Advances the lane one step and sets up the block's ramp: [blockStart] becomes the previous
     * [blockEnd] (continuity across blocks), [blockEnd] the new multiplier. Once per block.
     */
    fun beginBlock() {
        blockStart = blockEnd
        blockEnd = nextMultiplier()
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
