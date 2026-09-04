/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.warehouse

import io.peekandpoke.klang.audio_be.effects.Reverb

/**
 * The warehouse's shelf of idle [Reverb] units (resource-warehouse step 2d).
 *
 * A Freeverb network is ~200 KB of comb and allpass lines at 44.1 kHz, and every `Cylinder` used
 * to build one in its constructor — eight orbits, 1.6 MB, zero-filled on the audio thread on the
 * first play, reverb or not. A unit now exists only once an orbit (or a master chain) asks for
 * `room`, and comes from here.
 *
 * Same rules as [SizedBuffers], simpler because every unit is the same size: **stocked by return,
 * never by prediction**; a rented unit is indistinguishable from a new one — parameters back to
 * constructor defaults at return (six fields, O(1)), state zeroed by DEFERRED housekeeping
 * ([housekeep], one unit per call, called once per block by the backend) or on the spot by [rent]
 * when nothing clean is idle (review round 3: a unit's `reset()` is ~27 k stores, and sixteen of
 * them inside one render callback was the warmup's teardown stall); the shelf holds at most
 * [maxIdle] units and drops any further return; allocation failure is caught at this one site and
 * reported as `null`; a double return is refused and counted.
 */
class ReverbUnits(
    val sampleRate: Int,
    /** Bound on IDLE units. A unit is ~200 KB; 32 is ~6.5 MB — the warmup's 16 plus a song's own returned on top. */
    val maxIdle: Int = MAX_IDLE_UNITS,
    private val allocate: (sampleRate: Int) -> Reverb? = ::allocateOrNull,
) {
    private class Idle(val unit: Reverb, var clean: Boolean)

    private val shelf = ArrayList<Idle>()

    /** Idle units on the shelf right now. */
    val idleCount: Int get() = shelf.size

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

    /** True when every idle unit is zeroed — the warmup waits for this before `BackendReady`. */
    val isClean: Boolean get() = dirtyCount == 0

    /**
     * A unit at constructor defaults with all-zero state, or `null` if none is idle and allocation
     * failed — or, with [allocateOnMiss] false, if none is idle (the caller knows allocation fails
     * and only wants what the shelf serves for free; the delay's round-2 lesson).
     */
    fun rent(allocateOnMiss: Boolean = true): Reverb? {
        // A clean unit first, newest return first; a dirty one is zeroed on the spot.
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

        if (pick >= 0) {
            val idle = shelf.removeAt(pick)
            hits++
            if (!idle.clean) {
                idle.unit.reset()
                dirtyCount--
                syncCleans++
            }

            return idle.unit
        }

        if (!allocateOnMiss) {
            return null
        }

        val fresh = allocate(sampleRate)

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

    /** Zeroes ONE dirty idle unit (oldest return first). Returns true if it did; an integer compare when clean. */
    fun housekeep(): Boolean {
        if (dirtyCount == 0) {
            return false
        }

        for (i in shelf.indices) {
            val idle = shelf[i]
            if (!idle.clean) {
                idle.unit.reset()
                idle.clean = true
                dirtyCount--
                housekeptUnits++

                return true
            }
        }

        return false
    }

    companion object {
        const val MAX_IDLE_UNITS: Int = 32

        /** The one place a reverb network is built; see [SizedBuffers.allocateOrNull] for why the catch is sound. */
        fun allocateOrNull(sampleRate: Int): Reverb? = try {
            Reverb(sampleRate)
        } catch (e: Throwable) {
            null
        }
    }
}
