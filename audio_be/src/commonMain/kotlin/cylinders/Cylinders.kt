/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders

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
     * 1. Process all cylinder katalyst pipelines (Delay → Reverb → Phaser → Compressor)
     * 2. Apply ducking (cross-cylinder sidechain — requires all cylinders processed first)
     * 3. Mix all active cylinders to fusion output
     * 4. Round-robin cleanup check for silent cylinders
     */
    fun processAndMix(fusionMix: StereoBuffer) {
        // Step 1: Process katalyst pipeline on all cylinders
        for (cylinder in id2cylinder.values) {
            cylinder.processEffects()
        }

        // Step 2: Apply ducking (cross-cylinder sidechain)
        for (cylinder in id2cylinder.values) {
            val duckCylinderId = cylinder.ducking.duckCylinderId ?: continue
            val sidechainCylinder = id2cylinder[duckCylinderId] ?: continue
            cylinder.processDucking(sidechainCylinder.mixBuffer)
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
        val safeId = id % maxCylinders

        return id2cylinder.getOrPut(safeId) {
            units.rent(id = safeId, silentBlocksBeforeTailCheck = silentBlocksBeforeTailCheck)
        }.also {
            it.updateFromVoice(voice, blockStart)
        }
    }
}
