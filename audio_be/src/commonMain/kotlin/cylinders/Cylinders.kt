/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders

import io.peekandpoke.klang.audio_be.cylinders.katalyst.KatalystRegistry
import io.peekandpoke.klang.audio_be.warehouse.CylinderUnits
import io.peekandpoke.klang.audio_be.warehouse.ReverbUnits
import io.peekandpoke.klang.audio_be.warehouse.SizedBuffers
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.voices.Voice

/**
 * Mixing Channels / Effect Buses
 */
class Cylinders(
    private val blockFrames: Int,
    private val sampleRate: Int,
    private val silentBlocksBeforeTailCheck: Int = 10,
    maxCylinders: Int = MAX_CYLINDERS,
    /**
     * The cylinder shelf this engine rents from and returns to. Production passes the backend's one
     * warehouse; the default is a private shelf over private unit shelves, for specs.
     */
    private val units: CylinderUnits = CylinderUnits(
        blockFrames = blockFrames,
        sampleRate = sampleRate,
        rings = SizedBuffers.forRings(sampleRate),
        reverbs = ReverbUnits(sampleRate),
    ),
    /**
     * Where a `katalyst(…)` name is resolved. Production passes the owning engine's per-playback
     * fork, which is also what every cylinder rented here is handed, so a chain dies with the
     * playback that declared it; the default is a private, empty registry, for specs.
     */
    private val katalysts: KatalystRegistry = KatalystRegistry(),
) {
    companion object {
        const val MAX_CYLINDERS = 255
    }
    private val maxCylinders = maxCylinders.coerceIn(1, MAX_CYLINDERS)

    /**
     * The rented cylinders in the order they were rented. That order is the mix's summation order (and the
     * round-robin cleanup's), so it is kept exactly: a cylinder is only ever appended, and only [releaseAll]
     * removes, all at once. Every per-block walk is an index loop over it: a map or list iterator would allocate
     * per block on Kotlin/JS.
     */
    private val rented = ArrayList<Cylinder>()

    /**
     * The cylinder for each orbit id, by [slotOf]. A folded id (`id % maxCylinders`, see [cylinderFor]) keeps the
     * sign of a negative orbit, so the ids run from `-(maxCylinders - 1)` to `maxCylinders - 1`.
     */
    private val byId = arrayOfNulls<Cylinder>(2 * this.maxCylinders - 1)

    private var cleanupIndex = 0

    /** All rented cylinders, in rent order (the mix order). */
    val cylinders: List<Cylinder> get() = rented

    /** The ids of all rented cylinders, in rent order. Allocates: for specs and diagnostics, not the render path. */
    val cylindersIds: Set<Int> get() = rented.mapTo(LinkedHashSet()) { it.id }

    /** True if any cylinder is currently active. Alloc-free (no lambda, no iterator): safe to poll on the audio thread. */
    fun anyActive(): Boolean {
        for (i in 0 until rented.size) {
            if (rented[i].isActive) {
                return true
            }
        }

        return false
    }

    /** True while any orbit rings with a tail that can never end on its own ([Cylinder.sustainsItself]). */
    fun anySustainsItself(): Boolean {
        for (i in 0 until rented.size) {
            if (rented[i].sustainsItself()) {
                return true
            }
        }

        return false
    }

    /**
     * Returns every cylinder to the warehouse (which retires it: units back to their shelves,
     * state to a clean slate) and forgets them — the engine is being disposed (resource warehouse,
     * 2f + cylinders). The next playback's first voice on an orbit takes a shelved cylinder, and its
     * first delay or room a shelved ring or network: nothing is built in render.
     */
    fun releaseAll() {
        for (i in 0 until rented.size) {
            units.giveBack(rented[i])
        }

        rented.clear()
        byId.fill(null)
    }

    fun clearAll() {
        for (i in 0 until rented.size) {
            rented[i].clear()
        }
    }

    /**
     * Processes all cylinders and mixes the results into the given buffer.
     *
     * Processing order:
     * 1. Install (or start fading in) any chain queued on a cylinder, then run every cylinder's
     *    katalyst pipeline (for the classic chain: Body → Vowel → Delay → Reverb → Phaser →
     *    Compressor → Gain)
     * 2. Apply ducking (cross-cylinder sidechain — requires all cylinders processed first)
     * 3. Mix all active cylinders to fusion output
     * 4. Round-robin cleanup check for silent cylinders
     *
     * [blockStart] is the block's start frame, the one this block's voices claimed their orbits
     * with: it commits each orbit's owner for it, and the cleanup asks the orbit whether a voice still checks in (see
     * [Cylinder.tryDeactivate]).
     */
    // blockStart is an ABSOLUTE backend frame, a Double (see RenderClock.cursorFrame).
    fun processAndMix(fusionMix: StereoBuffer, blockStart: Double) {
        val count = rented.size

        // Step 1: Process katalyst pipeline on all cylinders
        for (i in 0 until count) {
            val cylinder = rented[i]
            // The block's owner, once, after every voice has rendered: the newest `Sounding` offer
            // ([Cylinder.commitOwner]). Before the pending poll, which resolves an arriving chain from it.
            cylinder.commitOwner()
            // A chain requested before its registration arrived lands here, on the first block
            // where the name resolves, and so does one that waited behind a running crossfade.
            // One field read per cylinder when nothing is queued, which is the normal case (see
            // [Cylinder.pollPendingChain]). Nothing here can cut audio: an idle orbit renders
            // nothing this block, and a sounding one gets the crossfade (step 3b).
            cylinder.pollPendingChain()
            cylinder.processEffects()
        }

        // Step 2: Apply ducking (cross-cylinder sidechain). The duck names its sidechain by the RAW orbit id, not
        // folded: an id outside the folded range finds no cylinder, as it never did.
        for (i in 0 until count) {
            val cylinder = rented[i]
            val duckCylinderId = cylinder.duck?.duckCylinderId ?: continue
            val sidechainCylinder = rentedOrNull(duckCylinderId) ?: continue
            cylinder.processDuck(sidechainCylinder.mixBuffer)
        }

        // Step 3: Mix all cylinders to output
        for (i in 0 until count) {
            val cylinder = rented[i]

            if (!cylinder.isActive) {
                continue
            }

            fusionMix.addFrom(source = cylinder.mixBuffer, frames = blockFrames)
        }

        // Step 4: Cleanup stale cylinders (round-robin over the rent order, no allocation)
        if (count > 0) {
            rented[cleanupIndex % count].tryDeactivate(blockStart)
            cleanupIndex = (cleanupIndex + 1) % maxCylinders
        }
    }

    /**
     * A `Sounding` voice renders on orbit [id] this block and offers itself as its owner ([Cylinder.offer]): the
     * orbit's bus settings are owned by the newest `Sounding` voice; a voice gives the orbit up when its gate
     * closes or it is cut. The cylinder is rented on first use.
     */
    // blockStart is an ABSOLUTE backend frame, Double, see RenderClock.cursorFrame.
    fun offer(id: Int, voice: Voice, blockStart: Double): Cylinder {
        return cylinderFor(id).also {
            it.offer(voice, blockStart)
        }
    }

    /**
     * A voice renders on orbit [id] this block without offering itself (past its gate, or fading): it routes its
     * audio and keeps the orbit in use ([Cylinder.checkIn]). The cylinder is rented on first use.
     */
    // blockStart is an ABSOLUTE backend frame, Double, see RenderClock.cursorFrame.
    fun checkIn(id: Int, blockStart: Double): Cylinder {
        return cylinderFor(id).also {
            it.checkIn(blockStart)
        }
    }

    /**
     * Routes a `katalyst(…)` reference to the cylinder for [orbit]: the scheduler calls this when
     * it consumes the reference, whether or not the event also sounds (see
     * [Cylinder.requestChain]).
     *
     * A chain declared on an orbit that is silent so far RENTS that orbit's cylinder, exactly as
     * its first voice would: the rent is the same call with the same accounting, and the cylinder
     * it returns is inactive, so it costs the render loop three early returns and one pending
     * check per block until a voice arrives. Not renting would mean losing the declaration of
     * every pattern whose chain is announced before its first note.
     *
     * One thing the rental is NOT free of: [processAndMix]'s cleanup is round-robin, ONE cylinder
     * per block, so every allocated cylinder makes every other cylinder's silence grace longer
     * (the block-framing D11 note on `Cylinder`: the wall-clock grace is
     * `silentBlocksBeforeTailCheck × allocatedCylinders × blockFrames`). A chain-only rental
     * therefore stretches the tail-check schedule of the orbits that ARE sounding, by the same
     * amount an extra sounding orbit would.
     */
    fun requestChain(orbit: Int, name: String) {
        cylinderFor(orbit).requestChain(name)
    }

    /**
     * The cylinder for [id], rented from the shelf on first use. ONE door for both callers, so a
     * chain request and a voice can never disagree about which cylinder an orbit number means
     * (the `% maxCylinders` fold).
     */
    private fun cylinderFor(id: Int): Cylinder {
        val safeId = id % maxCylinders
        val slot = slotOf(safeId)
        val existing = byId[slot]

        if (existing != null) {
            return existing
        }

        val cylinder = units.rent(
            id = safeId,
            silentBlocksBeforeTailCheck = silentBlocksBeforeTailCheck,
            katalysts = katalysts,
        )

        byId[slot] = cylinder
        rented.add(cylinder)

        return cylinder
    }

    /** The rented cylinder whose id is exactly [id], or null: an id outside the folded range has none. */
    private fun rentedOrNull(id: Int): Cylinder? {
        if (id <= -maxCylinders || id >= maxCylinders) {
            return null
        }

        return byId[slotOf(id)]
    }

    /** The [byId] index of a folded id (`-(maxCylinders - 1)` to `maxCylinders - 1`). */
    private fun slotOf(foldedId: Int): Int = foldedId + maxCylinders - 1
}
