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
 * never by prediction**; a returned unit is [Reverb.reset] and put back to constructor defaults, so
 * a rented one is indistinguishable from a new one; the shelf holds at most [maxIdle] units and
 * drops any further return (working memory is demand-driven and unbounded); allocation failure is
 * caught at this one site and reported as `null`; a double return is refused and counted.
 */
class ReverbUnits(
    val sampleRate: Int,
    /** Bound on IDLE units. A unit is ~200 KB; 16 is a little over 3 MB. */
    val maxIdle: Int = MAX_IDLE_UNITS,
    private val allocate: (sampleRate: Int) -> Reverb? = ::allocateOrNull,
) {
    private val shelf = ArrayList<Reverb>()

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

    /** A unit at constructor defaults with all-zero state, or `null` if none is idle and allocation failed. */
    fun rent(): Reverb? {
        if (shelf.isNotEmpty()) {
            hits++

            return shelf.removeAt(shelf.size - 1)
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
     * Returns [unit] to the shelf, reset to all-zero state and constructor-default parameters. Over
     * [maxIdle] the unit is dropped instead (and not reset: garbage needs no zero-fill).
     */
    fun giveBack(unit: Reverb) {
        for (idle in shelf) {
            if (idle === unit) {
                doubleReturns++

                return
            }
        }

        if (shelf.size >= maxIdle) {
            dropped++

            return
        }

        unit.reset()
        unit.restoreDefaults()
        shelf.add(unit)
    }

    companion object {
        const val MAX_IDLE_UNITS: Int = 16

        /** The one place a reverb network is built; see [SizedBuffers.allocateOrNull] for why the catch is sound. */
        fun allocateOrNull(sampleRate: Int): Reverb? = try {
            Reverb(sampleRate)
        } catch (e: Throwable) {
            null
        }
    }
}
