/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.warehouse

import io.peekandpoke.klang.audio_be.cylinders.Cylinder

/**
 * The warehouse's shelf of idle [Cylinder]s (resource warehouse, step 3 — maintainer 2026-09-04:
 * "cylinders will join the warehouse").
 *
 * A cylinder without its rented units is small (three block-sized send buffers and the effect
 * shells), but it is a deep object graph, and building eight of them in the first frame of a song
 * on a phone is what remained of the "Der Schmetterling" stutter once the rings and networks had
 * moved to their shelves. So cylinders are built by the warmup, returned when their engine is
 * disposed, and taken from here by the next engine's first voice on an orbit.
 *
 * Same rules as the other shelves: stocked by return; a returned cylinder is retired to a clean
 * slate ([Cylinder.retire]) with its ring and network already handed back to THEIR shelves — a
 * cylinder is never idle with units inside it, so every byte on a shelf is accounted for exactly
 * once; at most [maxIdle] idle; a double return is refused and counted. No allocation catch: a
 * cylinder is a few KB, and if THAT fails the tab is gone regardless.
 */
class CylinderUnits(
    private val blockFrames: Int,
    private val sampleRate: Int,
    /** The unit shelves a cylinder built here rents from. */
    private val rings: SizedBuffers,
    private val reverbs: ReverbUnits,
    val maxIdle: Int = MAX_IDLE_CYLINDERS,
) {
    private val shelf = ArrayList<Cylinder>()

    /** Idle cylinders on the shelf right now. */
    val idleCount: Int get() = shelf.size

    // Counters, for specs and for the diagnostics feed. Never reset; monotone.
    var allocations: Int = 0
        private set
    var hits: Int = 0
        private set
    var dropped: Int = 0
        private set
    var doubleReturns: Int = 0
        private set

    /**
     * A clean cylinder for orbit [id]: an idle one re-labelled, or a new one. [silentBlocksBeforeTailCheck]
     * is the owning `Cylinders`' setting and is adopted along with the id.
     */
    fun rent(id: Int, silentBlocksBeforeTailCheck: Int): Cylinder {
        if (shelf.isNotEmpty()) {
            hits++

            return shelf.removeAt(shelf.size - 1).also { it.adopt(id, silentBlocksBeforeTailCheck) }
        }

        allocations++

        return Cylinder(
            id = id,
            blockFrames = blockFrames,
            sampleRate = sampleRate,
            silentBlocksBeforeTailCheck = silentBlocksBeforeTailCheck,
            rings = rings,
            reverbs = reverbs,
        )
    }

    /** Retires [cylinder] (units back to their shelves, state to a clean slate) and shelves it. */
    fun giveBack(cylinder: Cylinder) {
        for (idle in shelf) {
            if (idle === cylinder) {
                doubleReturns++

                return
            }
        }

        cylinder.retire()

        if (shelf.size >= maxIdle) {
            dropped++

            return
        }

        shelf.add(cylinder)
    }

    companion object {
        /** Two full warmups' worth: the warmup builds 16, a song returns its own on top. */
        const val MAX_IDLE_CYLINDERS: Int = 32
    }
}
