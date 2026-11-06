/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.warehouse

import io.peekandpoke.klang.audio_be.effects.Reverb
import io.peekandpoke.klang.audio_be.ignitor.REGION_MAX_STAGES

/**
 * The warehouse's shelf of idle [Reverb] units (resource-warehouse step 2d).
 *
 * A Freeverb network is ~200 KB of comb and allpass lines at 44.1 kHz, and every `Cylinder` used
 * to build one in its constructor, eight cylinders, 1.6 MB, zero-filled on the audio thread on the
 * first play, reverb or not. A unit now exists only once a cylinder (or a master chain) asks for
 * `reverb`, and comes from here.
 *
 * Same rules as [SizedBuffers], simpler because every unit is the same size: **stocked by return,
 * never by prediction**; a rented unit is indistinguishable from a new one — parameters back to
 * constructor defaults at return (two fields, O(1)), state zeroed by DEFERRED housekeeping
 * ([housekeep], one unit per call, a unit inside a region in slices of a base unit's worth, called once per block by
 * the backend) or on the spot by [rent]
 * when nothing clean is idle (review round 3: a unit's `reset()` is ~27 k stores, and sixteen of
 * them inside one render callback was the warmup's teardown stall); the shelf holds at most
 * [maxIdle] units and drops any further return; allocation failure is caught at this one site and
 * reported as `null`; a double return is refused and counted.
 */
class ReverbUnits(
    val sampleRate: Int,
    /** Bound on IDLE units. A unit is ~200 KB; 32 is ~6.5 MB — the warmup's 16 plus a song's own returned on top. */
    val maxIdle: Int = MAX_IDLE_UNITS,
    /**
     * Builds a unit at a sample rate and a region's factor (1 outside a region); the one place a unit is made, so the
     * scaled shelves ([atFactor]) allocate through it too and a test's injected failure reaches them.
     */
    private val allocate: (sampleRate: Int, factor: Int) -> Reverb? = ::allocateOrNull,
) {
    private class Idle(val unit: Reverb, var clean: Boolean)

    private val shelf = ArrayList<Idle>()

    /**
     * The shelves for the reverbs inside oversampling regions, by their 2x steps above the base rate (index 0 is 2x),
     * each created on its first [atFactor], on the base shelf only: a unit at 4x is four times as long and only serves
     * a 4x region (KLANG-00214 (oversampling regions)). They are kept and housekept by the base shelf ([housekeep],
     * [isClean]); their counters are their own, and the base shelf's [version] and `total*` sums cover them, for the
     * warehouse's stats.
     */
    private val scaled = arrayOfNulls<ReverbUnits>(REGION_MAX_STAGES)

    /** How many times the base rate this shelf's units run at: 1 for the base shelf, the region's for a scaled one. */
    private var factor: Int = 1

    /** The base shelf a scaled one belongs to; null on the base shelf itself. */
    private var base: ReverbUnits? = null

    /** Idle units on the shelf right now. */
    val idleCount: Int get() = shelf.size

    /** Bumped on every change of this shelf. */
    private var ownVersion: Int = 0

    /** Moves on every change, of this shelf or of a scaled shelf it keeps: the stats snapshot rebuilds only then. */
    val version: Int
        get() = ownVersion + sumScaled { it.version }

    /** Idle units on this shelf and on the scaled shelves it keeps, each unit counted once whatever its factor. */
    val totalIdleCount: Int get() = idleCount + sumScaled { it.idleCount }

    /** [dirtyCount] over this shelf and its scaled shelves. */
    val totalDirtyCount: Int get() = dirtyCount + sumScaled { it.dirtyCount }

    /** [allocations] over this shelf and its scaled shelves. */
    val totalAllocations: Int get() = allocations + sumScaled { it.allocations }

    /** [hits] over this shelf and its scaled shelves. */
    val totalHits: Int get() = hits + sumScaled { it.hits }

    /** [failures] over this shelf and its scaled shelves. */
    val totalFailures: Int get() = failures + sumScaled { it.failures }

    /** [dropped] over this shelf and its scaled shelves. */
    val totalDropped: Int get() = dropped + sumScaled { it.dropped }

    // Counters, for specs and for the diagnostics feed. Never reset; monotone.
    var allocations: Int = 0
        private set
    var hits: Int = 0
        private set
    var failures: Int = 0
        private set
    var dropped: Int = 0
        private set
    var doubleReturns: Int = 0
        private set

    /** Units zeroed by [housekeep] so far. */
    var housekeptUnits: Int = 0
        private set

    /** Rents that had to zero a dirty unit synchronously because no clean one was idle. */
    var syncCleans: Int = 0
        private set

    /** Idle units not yet zeroed; keeps the per-block [housekeep] an integer compare while clean. */
    var dirtyCount: Int = 0
        private set

    /** True when every idle unit is zeroed, the scaled shelves' too: the warmup waits for this before `BackendReady`. */
    val isClean: Boolean
        get() {
            if (dirtyCount != 0) {
                return false
            }

            for (child in scaled) {
                if (child != null && !child.isClean) {
                    return false
                }
            }

            return true
        }

    /**
     * The shelf of units for a region at [factor] times THIS shelf's rate (2, 4, 8, 16; a power of two, as the region
     * builder hands it): on a scaled shelf the factors multiply, so a 2x region inside a 2x region reaches the one 4x
     * shelf. Created on the first call and kept by the base shelf: its units run at the base rate times the whole
     * factor, their damping mapped by it ([Reverb.factor]). It holds at most [maxIdle] divided by that factor idle
     * units (at least one): a unit is that many times as large. [factor] 1 or less is this shelf.
     */
    fun atFactor(factor: Int): ReverbUnits {
        if (factor <= 1) {
            return this
        }

        val root = base ?: this
        // The steps of a power of two above the base rate (2 is 1, 4 is 2, ...); a region stops at 16x, the array's size
        val steps = (31 - (this.factor * factor).countLeadingZeroBits()).coerceAtMost(REGION_MAX_STAGES)

        return root.scaled[steps - 1] ?: root.scaledShelf(steps).also { root.scaled[steps - 1] = it }
    }

    /** A new shelf on this base shelf for units [steps] 2x steps above its rate, allocating through this shelf's [allocate]. */
    private fun scaledShelf(steps: Int): ReverbUnits {
        val whole = 1 shl steps

        return ReverbUnits(
            sampleRate = sampleRate * whole,
            maxIdle = (maxIdle / whole).coerceAtLeast(1),
            allocate = allocate,
        ).also {
            it.factor = whole
            it.base = this
        }
    }

    /**
     * A unit at constructor defaults with all-zero state, or `null` if none is idle and allocation
     * failed — or, with [allocateOnMiss] false, if none is idle (the caller knows allocation fails
     * and only wants what the shelf serves for free; the delay's round-2 lesson).
     */
    fun rent(allocateOnMiss: Boolean = true): Reverb? {
        // A clean unit first, newest return first; a dirty one is zeroed on the spot, from where housekeeping stopped.
        var pick = -1
        for (i in shelf.indices.reversed()) {
            if (shelf[i].clean) {
                pick = i
                break
            }
        }
        if (pick < 0 && shelf.isNotEmpty()) {
            pick = shelf.size - 1
        }

        ownVersion++
        if (pick >= 0) {
            val idle = shelf.removeAt(pick)
            hits++
            if (!idle.clean) {
                // From where housekeeping stopped to the end: a whole reset for a unit it has not started
                idle.unit.resetPart(budget = idle.unit.storeCount)
                dirtyCount--
                syncCleans++
            }

            return idle.unit
        }

        if (!allocateOnMiss) {
            return null
        }

        val fresh = allocate(sampleRate, factor)

        if (fresh == null) {
            failures++

            return null
        }

        allocations++

        return fresh
    }

    /**
     * Returns [unit] to the shelf: parameters back to constructor defaults now (O(1)), state zeroed
     * later by [housekeep] or by the [rent] that takes it. Over [maxIdle] the unit is dropped
     * instead.
     */
    fun giveBack(unit: Reverb) {
        ownVersion++
        for (i in shelf.indices) {
            if (shelf[i].unit === unit) {
                doubleReturns++

                return
            }
        }

        if (shelf.size >= maxIdle) {
            dropped++

            return
        }

        unit.restoreDefaults()
        shelf.add(Idle(unit, clean = false))
        dirtyCount++
    }

    /**
     * Zeroes ONE dirty idle unit (oldest return first), this shelf's before a scaled shelf's ([atFactor]), or a base
     * unit's worth of it: a scaled unit is [Reverb.resetPart] a slice of `storeCount / factor` samples (rounded up)
     * per call, so a 16x unit takes sixteen calls and no block zeroes much more than one base unit. Returns true if it did any work;
     * integer compares when clean.
     */
    fun housekeep(): Boolean {
        if (dirtyCount == 0) {
            for (child in scaled) {
                if (child != null && child.housekeep()) {
                    return true
                }
            }

            return false
        }

        for (i in shelf.indices) {
            val idle = shelf[i]
            if (!idle.clean) {
                // Rounded up, so a unit takes exactly [factor] calls
                if (idle.unit.resetPart(budget = (idle.unit.storeCount + factor - 1) / factor)) {
                    idle.clean = true
                    dirtyCount--
                    housekeptUnits++
                    ownVersion++
                }

                return true
            }
        }

        return false
    }

    /** [pick] summed over the scaled shelves this shelf keeps (none on a scaled shelf). */
    private inline fun sumScaled(pick: (ReverbUnits) -> Int): Int {
        var sum = 0

        for (child in scaled) {
            if (child != null) {
                sum += pick(child)
            }
        }

        return sum
    }

    companion object {
        const val MAX_IDLE_UNITS: Int = 32

        /**
         * The one place a reverb network is built, at [factor] times the base rate inside a region ([Reverb.factor], 1
         * outside); see [SizedBuffers.allocateOrNull] for why the catch is sound.
         */
        fun allocateOrNull(sampleRate: Int, factor: Int): Reverb? = try {
            Reverb(sampleRate = sampleRate, factor = factor)
        } catch (e: Throwable) {
            null
        }
    }
}
