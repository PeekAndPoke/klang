/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import kotlin.math.sqrt
import kotlin.random.Random

/**
 * N own [AnalogDrift] lanes plus one shared lane, blended per sample by `analogSpread`.
 *
 * The one drift container for every multi-voice site: the unison stacks (a lane per voice), the
 * sine partial bank (a lane per partial), the superpluck (a lane per string). Independent lanes
 * are what keeps a unison stack organic. Real analog stacks sound wide and alive precisely because
 * each VCO carries its own pitch instability; one walk shared by every voice wobbles them in
 * lockstep, which kills the analog feel. That is why the default is spread 1, a lane per voice.
 *
 * `spread` blends the two ends per sample, in RATIO MINUS ONE space (the deviations add;
 * multiplying the multipliers would carry a second-order term that means nothing at cent scale):
 *
 * ```
 * multiplier = 1 + sqrt(1 - s) * sharedDev[i] + sqrt(s) * (own[lane].nextMultiplier() - 1)
 * ```
 *
 * The weights are constant-power, so two independent walks sum to the same depth as one and the
 * stack drifts by `analog` at every setting (linear weights would leave `s = 0.5` drifting at
 * 0.71 times `analog`). The endpoints are exact, not a limit of the blend: at `s = 1` only the own
 * lane is advanced (what every super oscillator sounded like before this class existed, value for
 * value), at `s = 0` only the shared lane is, one walk for the whole stack, so the unison detune
 * stays static and the stack wobbles as a single physical oscillator.
 *
 * **Draw order.** Each own lane is drawn from [rng] the moment [ensureLanes] first reaches its
 * index, in index order; the shared lane is drawn at the first [prepareBlock] whose spread is
 * below 1, and never at all at spread 1. Adopters call both at fixed points in their block, so a
 * seeded voice renders reproducibly (`SeededVoiceRngSpec` is that contract).
 *
 * **Depth is latched.** [analog] is read by the adopter once, at the first block that builds this
 * object, exactly like the plain sine latches its own drift. Lanes created later (a mid-note voice
 * count rise) get the latched depth, and a surviving lane keeps its walk: the slow layer must not
 * re-seed to centre mid-note.
 *
 * **Growth only.** Lanes and the shared scratch grow on demand and never shrink, so a voice count
 * that moves up and down allocates once, and no block allocates in steady state.
 *
 * [active] is false when `analog` is 0. Adopters keep a null [DriftLanes] then and skip the drift
 * path entirely, which is what the mono oscillators do with a null [AnalogDrift].
 */
class DriftLanes(
    private val analog: Double,
    private val sampleRate: Int,
    private val rng: Random,
) {
    /** Whether drift is active. When false the adopter should hold null and skip the drift path. */
    val active: Boolean = analog > 0.0

    /**
     * The own lanes, indexed by voice / partial / string. `@PublishedApi internal` because [step]
     * is a public `inline fun` whose body expands at the call site on Kotlin/JS (no per-sample
     * method dispatch there); a private field would fail the visibility check.
     */
    @PublishedApi
    internal var own: Array<AnalogDrift> = emptyArray()

    /** The shared walk, created at the first [prepareBlock] that needs it. */
    private var shared: AnalogDrift? = null

    /** Growth-only scratch: the shared lane's deviation (`multiplier - 1`) per sample of the block. */
    @PublishedApi
    internal var sharedDev: DoubleArray = DoubleArray(0)

    /** Blend weights and the two per-block branch flags, all set by [prepareBlock]. */
    @PublishedApi
    internal var wShared: Double = 0.0

    @PublishedApi
    internal var wOwn: Double = 0.0

    @PublishedApi
    internal var useShared: Boolean = false

    @PublishedApi
    internal var useOwn: Boolean = false

    /** How many own lanes exist. Adopters that hand out stable lane indices count from here. */
    val laneCount: Int get() = own.size

    /**
     * Grow to [count] own lanes. Only the NEW indices are created, from [rng] in index order;
     * surviving lanes keep their walk. Never shrinks, and does nothing while [active] is false.
     */
    fun ensureLanes(count: Int) {
        if (!active || count <= own.size) {
            return
        }

        val old = own

        own = Array(count) { i -> if (i < old.size) old[i] else AnalogDrift(analog, sampleRate, rng) }
    }

    /**
     * Once per block, BEFORE the voice loop. Coerces [spread] to `0..1`, derives the constant-power
     * weights, and (below spread 1) advances the shared lane once per sample for `off until end`
     * into the scratch, so every voice's loop reads the same shared sequence.
     */
    fun prepareBlock(spread: Double, off: Int, end: Int) {
        if (!active) {
            return
        }

        // NaN-guard: coerceIn passes NaN through, so a NaN spread reads as the door default
        // (1, independent lanes).
        val s = if (spread.isNaN()) 1.0 else spread.coerceIn(0.0, 1.0)

        wShared = sqrt(1.0 - s)
        wOwn = sqrt(s)
        useShared = s < 1.0
        useOwn = s > 0.0

        if (!useShared) {
            return
        }

        val lane = shared ?: AnalogDrift(analog, sampleRate, rng).also { shared = it }

        if (sharedDev.size < end) {
            sharedDev = DoubleArray(end)
        }

        val dev = sharedDev

        for (i in off until end) {
            dev[i] = lane.nextMultiplier() - 1.0
        }
    }

    /**
     * The phase-increment multiplier for own lane [lane] at sample [i] of the current block.
     *
     * Branches only on the two per-block flags: at spread 1 it is the own lane's multiplier and
     * nothing else runs, at spread 0 the shared deviation and the own lane is NOT advanced.
     * Inactive returns exactly 1.0. No allocation, no dispatch on Kotlin/JS.
     */
    @Suppress("NOTHING_TO_INLINE")
    inline fun step(lane: Int, i: Int): Double {
        if (useShared) {
            if (useOwn) {
                return 1.0 + wShared * sharedDev[i] + wOwn * (own[lane].nextMultiplier() - 1.0)
            }

            return 1.0 + sharedDev[i]
        }

        if (useOwn) {
            return own[lane].nextMultiplier()
        }

        return 1.0
    }
}
