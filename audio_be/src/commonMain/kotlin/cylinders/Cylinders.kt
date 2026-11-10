/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders

import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.katalyst.KatalystRegistry
import io.peekandpoke.klang.audio_be.utils.copyRangeInto
import io.peekandpoke.klang.audio_be.voices.Voice
import io.peekandpoke.klang.audio_be.warehouse.CylinderUnits
import io.peekandpoke.klang.audio_be.warehouse.ReverbUnits
import io.peekandpoke.klang.audio_be.warehouse.SizedBuffers
import kotlin.math.max

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
     * The cylinder for each cylinder id, by [slotOf]. A folded id (`id % maxCylinders`, see [cylinderFor]) keeps the
     * sign of a negative cylinder, so the ids run from `-(maxCylinders - 1)` to `maxCylinders - 1`.
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

    /** True while any cylinder rings with a tail that can never end on its own ([Cylinder.sustainsItself]). */
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
     * 2f + cylinders). The next playback's first voice on a cylinder takes a shelved cylinder, and its
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
     * 2. Apply ducking (cross-cylinder sidechain, requires all cylinders processed first): every sidechain reads
     *    its source's mix as the chains left it, before any duck writes (KLANG-00428)
     * 3. Mix all active cylinders to fusion output
     * 4. Round-robin cleanup check for silent cylinders
     *
     * [blockStart] is the block's start frame, the one this block's voices claimed their cylinders
     * with: it commits each cylinder's owner for it, and the cleanup asks the cylinder whether a voice still checks in (see
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
            // [Cylinder.pollPendingChain]). Nothing here can cut audio: an idle cylinder renders
            // nothing this block, and a sounding one gets the crossfade (step 3b).
            cylinder.pollPendingChain()
            cylinder.processEffects()
        }

        // Step 2: Apply ducking (cross-cylinder sidechain). The duck names its sidechain by the RAW cylinder id, not
        // folded: an id outside the folded range finds no cylinder, as it never did.
        //
        // Every sidechain reads its source's mix BEFORE any duck writes (maintainer, 2026-11-10; audit findings L6-04,
        // L7b-08, KLANG-00428), so the result does not hang on the order the cylinders were first used, and two
        // cylinders that duck each other duck alike. 2a keeps a copy of the mix of every source that is itself
        // ducked this block, before 2b runs a single duck; 2b hands each duck that copy, or the live mix of a source
        // nobody ducks. The live mix is exact there: the duck pass writes only through `processDuck`, and that writes
        // only its own cylinder's mix (`KatalystContext.mixBuffer`; the swap's blend uses its own scratch), so a
        // cylinder whose `processDuck` does not run this block ([ducksThisBlock] false) leaves the pass with its mix
        // bit for bit as it came in. One block copy per such source, into a buffer the cylinder owns.
        for (i in 0 until count) {
            val cylinder = rented[i]

            if (!cylinder.isActive) {
                continue
            }

            val duckCylinderId = cylinder.duck?.duckCylinderId ?: continue
            val source = rentedOrNull(duckCylinderId) ?: continue

            if (!source.preDuckHeld && ducksThisBlock(source)) {
                source.mixBuffer.left.copyRangeInto(destination = source.preDuckMix.left, destinationOffset = 0, startIndex = 0, endIndex = blockFrames)
                source.mixBuffer.right.copyRangeInto(destination = source.preDuckMix.right, destinationOffset = 0, startIndex = 0, endIndex = blockFrames)
                source.preDuckHeld = true
            }
        }

        for (i in 0 until count) {
            val cylinder = rented[i]
            val duckCylinderId = cylinder.duck?.duckCylinderId ?: continue
            val sidechainCylinder = rentedOrNull(duckCylinderId) ?: continue
            cylinder.processDuck(if (sidechainCylinder.preDuckHeld) sidechainCylinder.preDuckMix else sidechainCylinder.mixBuffer)
        }

        for (i in 0 until count) {
            rented[i].preDuckHeld = false
        }

        // Step 3: Mix all cylinders to output
        for (i in 0 until count) {
            val cylinder = rented[i]

            if (!cylinder.isActive) {
                continue
            }

            fusionMix.addFrom(source = cylinder.mixBuffer, frames = blockFrames)
        }

        // Step 4: Cleanup stale cylinders (round-robin over the rent order, no allocation). The index wraps at
        // maxCylinders, or at the count once more cylinders than that are rented: a folded id keeps its sign, so up to
        // `2 * maxCylinders - 1` can be, and a wrap at maxCylinders alone never reached the ones past index 254, which
        // then stayed active for good and kept a stopped engine (KLANG-00428, audit finding L6-05). Up to maxCylinders
        // the wrap is what it always was (a count that does not divide it visits one cylinder twice at the wrap, one
        // visit of grace), so no teardown moves.
        if (count > 0) {
            rented[cleanupIndex % count].tryDeactivate(blockStart)
            cleanupIndex = (cleanupIndex + 1) % max(count, maxCylinders)
        }
    }

    /**
     * A `Sounding` voice renders on cylinder [id] this block and offers itself as its owner ([Cylinder.offer]): the
     * cylinder's bus settings are owned by the newest `Sounding` voice; a voice gives the cylinder up when its gate
     * closes or it is cut. The cylinder is rented on first use.
     */
    // blockStart is an ABSOLUTE backend frame, Double, see RenderClock.cursorFrame.
    fun offer(id: Int, voice: Voice, blockStart: Double): Cylinder {
        return cylinderFor(id).also {
            it.offer(voice, blockStart)
        }
    }

    /**
     * A voice renders on cylinder [id] this block without offering itself (past its gate, or fading): it routes its
     * audio and keeps the cylinder in use ([Cylinder.checkIn]). The cylinder is rented on first use.
     */
    // blockStart is an ABSOLUTE backend frame, Double, see RenderClock.cursorFrame.
    fun checkIn(id: Int, blockStart: Double): Cylinder {
        return cylinderFor(id).also {
            it.checkIn(blockStart)
        }
    }

    /**
     * Routes a `katalyst(…)` reference to the cylinder for [cylinder]: the scheduler calls this when
     * it consumes the reference, whether or not the event also sounds (see
     * [Cylinder.requestChain]).
     *
     * A chain declared on a cylinder that is silent so far RENTS its `Cylinder` instance, exactly as
     * its first voice would: the rent is the same call with the same accounting, and the cylinder
     * it returns is inactive, so it costs the render loop three early returns and one pending
     * check per block until a voice arrives. Not renting would mean losing the declaration of
     * every pattern whose chain is announced before its first note.
     *
     * One thing the rental is NOT free of: [processAndMix]'s cleanup is round-robin, ONE cylinder
     * per block, so every allocated cylinder makes every other cylinder's silence grace longer
     * (the block-framing D11 note on `Cylinder`: the wall-clock grace is
     * `silentBlocksBeforeTailCheck × allocatedCylinders × blockFrames`). A chain-only rental
     * therefore stretches the tail-check schedule of the cylinders that ARE sounding, by the same
     * amount an extra sounding cylinder would.
     */
    fun requestChain(cylinder: Int, name: String) {
        cylinderFor(cylinder).requestChain(name)
    }

    /**
     * The cylinder for [id], rented from the shelf on first use. ONE door for both callers, so a
     * chain request and a voice can never disagree about which instance a cylinder number means
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

    /**
     * Whether the duck pass will write [cylinder]'s mix this block: it runs (`processDuck` returns at once on an
     * inactive cylinder), it has a duck with a sidechain, and that sidechain is rented. The same three tests the pass
     * itself makes before it calls `processDuck`.
     */
    private fun ducksThisBlock(cylinder: Cylinder): Boolean {
        if (!cylinder.isActive) {
            return false
        }

        val id = cylinder.duck?.duckCylinderId ?: return false

        return rentedOrNull(id) != null
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
