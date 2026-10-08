/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import kotlin.math.sqrt
import kotlin.random.Random

/**
 * N own [AnalogDrift] lanes plus one shared lane, blended per block by `analogSpread`.
 *
 * The one drift container for every multi-voice site: the unison stacks (a lane per voice), the
 * sine partial bank (a lane per partial), the superpluck (a lane per string). Independent lanes
 * are what keeps a unison stack organic. Real analog stacks sound wide and alive precisely because
 * each VCO carries its own pitch instability; one walk shared by every voice wobbles them in
 * lockstep, which kills the analog feel. That is why the default is spread 1, a lane per voice.
 *
 * `spread` blends the two walks, in RATIO MINUS ONE space (the deviations add; multiplying the
 * multipliers would carry a second-order term that means nothing at cent scale). Every lane steps
 * ONCE per block (2026-09-15; per sample before) and the voice ramps linearly across the block
 * between the blend of the two block starts and the blend of the two block ends:
 *
 * ```
 * multiplier(block edge) = 1 + sqrt(1 - s) * (shared - 1) + sqrt(s) * (own[lane] - 1)
 * ```
 *
 * The weights are constant-power, so two independent walks sum to the same depth as one and the
 * stack drifts by `analog` at every setting (linear weights would leave `s = 0.5` drifting at
 * 0.71 times `analog`). The endpoints are exact, not a limit of the blend: at `s = 1` only the own
 * lane is advanced (what every super oscillator sounded like before this class existed, value for
 * value), at `s = 0` only the shared lane is, one walk for the whole stack, so the unison detune
 * stays static and the stack wobbles as a single physical oscillator.
 *
 * **Built, then started** (tidy-up step 10). The node builds the container with the voice when its
 * depth may be above 0, sized to [capacity] own lanes (the voice count it can read at build), with the
 * shared lane when [sharedLane] says the spread may drop below 1; so every lane object it will use
 * exists before the first block. A signal reads as unknown at build, so its node always builds both.
 * Only a block-constant knob that reads the note frequency (read at frequency 0 at build) can guess
 * short; the render then builds what is missing, as before this step. [start] runs at the first block that sizes the adopter,
 * where the depth is read: it latches the depth and draws the shared seed. [ensureLanes] then SEEDS
 * the lanes it raises, in index order, instead of building them; only a count past [capacity] (a
 * `voices` signal the build could not read) grows the array, on the audio thread, as before this
 * step. The shared lane's own `Random(sharedSeed)` is still made at the first block below spread 1:
 * its seed is drawn at render, and a project-owned generator that a seed can restart is decision D7.
 *
 * **Draw order.** [start] takes ONE int from the voice stream, the shared lane's seed. Each own lane
 * is then drawn from that stream when [ensureLanes] first reaches its index, in index order. The
 * shared lane itself is seeded lazily, at the first [prepareBlock] whose spread is below 1, from its
 * own `Random(sharedSeed)`, so it consumes no voice draw of its own: WHEN the spread first drops
 * below 1 cannot shift what any later consumer of the voice rng gets, and a modulated
 * `analogSpread` cannot re-roll a superpluck's excitation bursts. A seeded voice renders
 * reproducibly (`SeededVoiceRngSpec` is that contract).
 *
 * **Retire and regrow.** [ensureLanes] re-seeds the lane at every index at or above the live
 * count, so an adopter that drops voices ([retireLanes]) and later grows back gets lanes that
 * attack in tune (the slow layer seeds at centre) instead of walks frozen mid-note. Adopters whose
 * state survives a shrink (the sine partial bank's partials, the superpluck's strings) simply never
 * retire, and their lanes keep walking.
 *
 * **Depth is latched.** The depth handed to [start] is read by the adopter once, at its first
 * block, exactly like the plain sine latches its own drift. Lanes raised later (a mid-note voice
 * count rise) get the latched depth, and a surviving lane keeps its walk: the slow layer must not
 * re-seed to centre mid-note.
 *
 * **No per-block allocation.** Lanes are seeded only when the live count rises, so no block
 * allocates in steady state.
 *
 * [active] is false until [start], and stays false when the depth is not above 0. Adopters hand a
 * null [DriftLanes] to their render loops then and skip the drift path entirely, which is what the
 * mono oscillators do with an inactive [AnalogDrift].
 *
 * **How a hot loop uses it.** [prepareBlock] once per block, then per voice [advanceLane], and
 * the ramp `m = startOf(lane)`, `dm = rampStep(from = m, to = endOf(lane), frames = length)` hoisted before its sample
 * loop, which pays one add per sample. Nothing is read off the container per sample: on
 * Kotlin/JS that cost a drifting 8-voice supersaw about 16 percent (Node, 2026-09-10), and the
 * per-sample lane steps it replaced cost the Schmetterling guitars 19 percent (2026-09-15).
 */
class DriftLanes(capacity: Int, sharedLane: Boolean) {
    /** Whether drift is active: [start] ran with a depth above 0. When false the adopter should hand null to its loops. */
    var active: Boolean = false
        private set

    /** The latched depth, set by [start]. */
    private var analog: Double = 0.0

    /** The lane step rate, set by [start]. */
    private var stepRate: Int = 0

    /** The voice's random stream, set by [start]; the own lanes draw from it. */
    private lateinit var rng: Random

    /** The own lanes, indexed by voice / partial / string. Built unseeded; [ensureLanes] seeds them. */
    private var own: Array<AnalogDrift> = Array(if (capacity > 0) capacity else 0) { AnalogDrift() }

    /** How many of [own] are live. Indices at or above this are retired and are re-seeded on regrow. */
    private var live: Int = 0

    /**
     * The shared lane's seed, taken from the voice stream by [start] so that seeding the lane later
     * costs no voice draw. Drawn only while [active]: an inactive container consumes nothing.
     */
    private var sharedSeed: Int = 0

    /**
     * The shared walk, seeded at the first [prepareBlock] that needs it, from [sharedSeed]. Built here when the
     * spread may drop below 1, else at that block (the spread is 1 by default, where the walk is never read).
     */
    private var shared: AnalogDrift? = if (sharedLane) AnalogDrift() else null
    private var sharedSeeded: Boolean = false

    /** Weight of the shared walk this block, `sqrt(1 - spread)`. */
    private var wShared: Double = 0.0

    /** Weight of the own walk this block, `sqrt(spread)`. */
    private var wOwn: Double = 0.0

    private var useShared: Boolean = false
    private var useOwn: Boolean = false

    /** How many own lanes are live. Adopters that hand out stable lane indices count from here. */
    val laneCount: Int get() = live

    /**
     * Latches the depth [analog] and the [stepRate], keeps [rng] (the voice's stream) for the own
     * lanes, and takes the shared lane's seed from it when [analog] is above 0. Once, at the
     * adopter's first block.
     */
    fun start(analog: Double, stepRate: Int, rng: Random) {
        this.analog = analog
        this.stepRate = stepRate
        this.rng = rng
        active = analog > 0.0
        sharedSeed = if (active) rng.nextInt() else 0
    }

    /**
     * Raise the live count to [count]. Lanes below the current live count keep their walk; every
     * index from there up is seeded FRESH from the voice stream, in index order, whether or not it
     * was live before. Does nothing while [active] is false, or when [count] is not a rise. Past the
     * build-time capacity the array grows here, on the audio thread.
     */
    fun ensureLanes(count: Int) {
        if (!active || count <= live) {
            return
        }

        if (count > own.size) {
            val old = own

            own = Array(count) { i -> if (i < old.size) old[i] else AnalogDrift() }
        }

        for (i in live until count) {
            own[i].seed(analog = analog, stepRate = stepRate, rng = rng)
        }

        live = count
    }

    /**
     * Drop the lanes from [from] up. They stop being read, and a later [ensureLanes] re-seeds them
     * fresh rather than resuming a walk frozen mid-note. Nothing is deallocated and nothing is
     * drawn here. Call it where the adopter drops the voices themselves.
     */
    fun retireLanes(from: Int) {
        val floor = if (from < 0) 0 else from

        if (floor < live) {
            live = floor
        }
    }

    /**
     * Once per block, BEFORE the voice loop. Coerces [spread] to `0..1`, derives the constant-power
     * weights, and (below spread 1) steps the shared lane once, so every voice's ramp this block
     * blends the same shared move.
     */
    fun prepareBlock(spread: Double) {
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

        val lane = shared ?: AnalogDrift().also { shared = it }

        if (!sharedSeeded) {
            sharedSeeded = true
            lane.seed(analog = analog, stepRate = stepRate, rng = Random(sharedSeed))
        }

        lane.beginBlock()
    }

    /**
     * Steps voice [lane]'s own lane once for this block, when this block advances own lanes at all
     * (not at spread 0 exactly, not while [active] is false, not at a RETIRED index, whose object is
     * still in the array but is no longer anybody's lane). Once per voice per block, before
     * [startOf] and [endOf].
     */
    fun advanceLane(lane: Int) {
        if (useOwn && lane < live) {
            own[lane].beginBlock()
        }
    }

    /** Voice [lane]'s multiplier at the start of this block: the blend of both walks' block starts. */
    fun startOf(lane: Int): Double = blendOf(lane, atEnd = false)

    /** Voice [lane]'s multiplier at the end of this block: the blend of both walks' block ends. */
    fun endOf(lane: Int): Double = blendOf(lane, atEnd = true)

    /**
     * The blend, over the two lanes' ramp ends. Endpoints are exact: spread 1 is the own lane's own
     * multiplier, spread 0 the shared lane's, and a voice with neither (a retired index at spread 1)
     * sits at 1.
     */
    private fun blendOf(lane: Int, atEnd: Boolean): Double {
        val ownLane = if (useOwn && lane < live) own[lane] else null
        val sharedLane = if (useShared) shared else null
        val ownV = if (ownLane == null) 1.0 else if (atEnd) ownLane.blockEnd else ownLane.blockStart
        val sharedV = if (sharedLane == null) 1.0 else if (atEnd) sharedLane.blockEnd else sharedLane.blockStart

        if (sharedLane == null) {
            return ownV
        }

        if (ownLane == null) {
            return sharedV
        }

        return 1.0 + wShared * (sharedV - 1.0) + wOwn * (ownV - 1.0)
    }
}
