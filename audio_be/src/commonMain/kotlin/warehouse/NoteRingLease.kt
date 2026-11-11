/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.warehouse

/**
 * What one voice's Ignitor tree rented from the [NoteRings] shelf (KLANG-00429, audit finding L5-03): made by the
 * build on the first rent (`IgnitorBuildCache.noteRingLease`), so a voice that rents nothing makes none, held by the
 * voice (`Voice.giveBackNoteRings`) and given back when the voice leaves its engine. A node that grows at render (the
 * superpluck's strings past its build-time count) rents through the same lease, so its new rings go back with the
 * others.
 *
 * [giveBackAll] gives every ring back ONCE and forgets them: a second call gives nothing, so a voice that leaves by two
 * ways (removed, then its engine disposed) returns nothing twice.
 */
class NoteRingLease(private val shelf: NoteRings) {
    private var rings: Array<DoubleArray?> = arrayOfNulls(INITIAL_CAPACITY)

    /** How many rings this lease holds now. */
    var count: Int = 0
        private set

    /** A zeroed ring of at least [minFrames] frames ([NoteRings.rent]), held by this lease until [giveBackAll]. */
    fun rent(minFrames: Int): DoubleArray {
        val ring = shelf.rent(minFrames)

        if (count == rings.size) {
            rings = rings.copyOf(2 * count)
        }

        rings[count] = ring
        count++

        return ring
    }

    /** Gives every ring held back to the shelf, dirty, and forgets them. */
    fun giveBackAll() {
        for (i in 0 until count) {
            val ring = rings[i]

            if (ring != null) {
                shelf.giveBack(ring)
            }

            rings[i] = null
        }

        count = 0
    }

    private companion object {
        /** A pluck holds one ring, a shimmer one, a superpluck one per string (8 by default); grown by doubling. */
        const val INITIAL_CAPACITY: Int = 4
    }
}

/**
 * A ring of at least [minFrames] frames for a node's build: rented through this lease, or, without one (a build outside
 * a voice: the specs, a tool), a fresh array of exactly [minFrames], as before the shelf.
 */
internal fun NoteRingLease?.ringOf(minFrames: Int): DoubleArray = this?.rent(minFrames) ?: DoubleArray(minFrames)
