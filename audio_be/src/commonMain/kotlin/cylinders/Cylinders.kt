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
        const val MAX_CYLINDERS = 256
    }
    private val maxCylinders = maxCylinders.coerceIn(1, 255)
    private val id2cylinder = mutableMapOf<Int, Cylinder>()
    private var cleanupIndex = 0

    /** Get all cylinders */
    val cylinders get() = id2cylinder.values

    /** Get all currently allocated cylinder IDs. */
    val cylindersIds: Set<Int> get() = id2cylinder.keys

    /** True if any cylinder is currently active. Alloc-free (no lambda) — safe to poll on the audio thread. */
    fun anyActive(): Boolean {
        for (cylinder in id2cylinder.values) {
            if (cylinder.isActive) return true
        }
        return false
    }

    /**
     * Clear all cylinders
     */
    /**
     * Returns every cylinder to the warehouse (which retires it: units back to their shelves,
     * state to a clean slate) and forgets them — the engine is being disposed (resource warehouse,
     * 2f + cylinders). The next playback's first voice on an orbit takes a shelved cylinder, and its
     * first delay or room a shelved ring or network: nothing is built in render.
     */
    fun releaseAll() {
        for (cylinder in id2cylinder.values) {
            units.giveBack(cylinder)
        }
        id2cylinder.clear()
    }

    fun clearAll() {
        for (cylinder in id2cylinder.values) {
            cylinder.clear()
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
     */
    fun processAndMix(fusionMix: StereoBuffer) {
        // Step 1: Process katalyst pipeline on all cylinders
        for (cylinder in id2cylinder.values) {
            // A chain requested before its registration arrived lands here, on the first block
            // where the name resolves, and so does one that waited behind a running crossfade.
            // One field read per cylinder when nothing is queued, which is the normal case (see
            // [Cylinder.pollPendingChain]). Nothing here can cut audio: an idle orbit renders
            // nothing this block, and a sounding one gets the crossfade (step 3b).
            cylinder.pollPendingChain()
            cylinder.processEffects()
        }

        // Step 2: Apply ducking (cross-cylinder sidechain)
        for (cylinder in id2cylinder.values) {
            val duckCylinderId = cylinder.duck?.duckCylinderId ?: continue
            val sidechainCylinder = id2cylinder[duckCylinderId] ?: continue
            cylinder.processDuck(sidechainCylinder.mixBuffer)
        }

        // Step 3: Mix all cylinders to output
        for (cylinder in id2cylinder.values) {
            if (!cylinder.isActive) continue

            run {
                val fusionLeft = fusionMix.left
                val cylinderLeft = cylinder.mixBuffer.left

                for (i in 0 until blockFrames) {
                    fusionLeft[i] = fusionLeft[i] + cylinderLeft[i]
                }
            }

            run {
                val fusionRight = fusionMix.right
                val cylinderRight = cylinder.mixBuffer.right

                for (i in 0 until blockFrames) {
                    fusionRight[i] = fusionRight[i] + cylinderRight[i]
                }
            }
        }

        // Step 4: Cleanup stale cylinders (round-robin, no allocation)
        if (id2cylinder.isNotEmpty()) {
            val size = id2cylinder.size
            val keyIndex = cleanupIndex % size
            // Iterate to the keyIndex-th entry without allocating a list
            var idx = 0
            for ((cylinderId, cylinder) in id2cylinder) {
                if (idx == keyIndex) {
                    cylinder.tryDeactivate()
                    break
                }
                idx++
            }
            cleanupIndex = (cleanupIndex + 1) % maxCylinders
        }
    }

    /**
     * Gets a cylinder.
     *
     * If the cylinder exists, it will be returned.
     *
     * When a new cylinder is created, it will be initialized with the given voice.
     */
    // blockStart is an ABSOLUTE backend frame — Double, see RenderClock.cursorFrame.
    fun getOrInit(id: Int, voice: Voice, blockStart: Double): Cylinder {
        return cylinderFor(id).also {
            it.updateFromVoice(voice, blockStart)
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

        return id2cylinder.getOrPut(safeId) {
            units.rent(
                id = safeId,
                silentBlocksBeforeTailCheck = silentBlocksBeforeTailCheck,
                katalysts = katalysts,
            )
        }
    }
}
