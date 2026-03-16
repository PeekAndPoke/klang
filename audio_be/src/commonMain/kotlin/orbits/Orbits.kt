package io.peekandpoke.klang.audio_be.orbits

import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.voices.Voice

/**
 * Mixing Channels / Effect Buses
 */
class Orbits(
    maxOrbits: Int = 16,
    private val blockFrames: Int,
    private val sampleRate: Int,
    private val silentBlocksBeforeTailCheck: Int = 10,
) {
    private val maxOrbits = maxOrbits.coerceIn(1, 255)
    private val id2orbit = mutableMapOf<Int, Orbit>()
    private var cleanupIndex = 0

    /** Get all orbits */
    val orbits get() = id2orbit.values

    /** Get all currently allocated orbit IDs. */
    val orbitsIds: Set<Int> get() = id2orbit.keys

    /**
     * Clear all orbits
     */
    fun clearAll() {
        for (orbit in id2orbit.values) {
            orbit.clear()
        }
    }

    /**
     * Processes all orbits and mixes the results into the given buffer.
     *
     * Processing order:
     * 1. Process all orbit effects (delay, reverb, phaser)
     * 2. Apply ducking (requires all orbits processed first)
     * 3. Mix all orbits to master output
     */
    fun processAndMix(masterMix: StereoBuffer) {
        // Step 1: Process effects on all orbits
        for (orbit in id2orbit.values) {
            orbit.processEffects()
        }

        // Step 2: Apply ducking (cross-orbit sidechain)
        for (orbit in id2orbit.values) {
            val ducking = orbit.ducking
            val duckOrbitId = orbit.duckOrbitId

            if (ducking != null && duckOrbitId != null) {
                // Get sidechain source orbit
                val sidechainOrbit = id2orbit[duckOrbitId]

                if (sidechainOrbit != null) {
                    // Apply ducking using sidechain orbit's mix as trigger
                    ducking.process(
                        input = orbit.mixBuffer.left,
                        sidechain = sidechainOrbit.mixBuffer.left,
                        blockSize = blockFrames
                    )
                    ducking.process(
                        input = orbit.mixBuffer.right,
                        sidechain = sidechainOrbit.mixBuffer.right,
                        blockSize = blockFrames
                    )
                }
            }
        }

        // Step 3: Mix all orbits to output
        for (orbit in id2orbit.values) {
            if (!orbit.isActive) continue

            run {
                val masterLeft = masterMix.left
                val orbitLeft = orbit.mixBuffer.left

                for (i in 0 until blockFrames) {
                    masterLeft[i] += orbitLeft[i]
                }
            }

            run {
                val masterRight = masterMix.right
                val orbitRight = orbit.mixBuffer.right

                for (i in 0 until blockFrames) {
                    masterRight[i] += orbitRight[i]
                }
            }
        }

        // Step 4: Cleanup stale orbits (round-robin approach)
        // We pick one orbit per block to check for silence
        val orbitKeys = id2orbit.keys.toList()
        if (orbitKeys.isNotEmpty()) {
            val keyIndex = cleanupIndex % orbitKeys.size
            val orbitId = orbitKeys[keyIndex]
            id2orbit[orbitId]?.tryDeactivate()

            cleanupIndex = (cleanupIndex + 1) % maxOrbits
        }
    }

    /**
     * Gets an orbit.
     *
     * If the orbit exists, it will be returned.
     *
     * When a new orbit is created, it will be initialized with the given voice.
     */
    fun getOrInit(id: Int, voice: Voice): Orbit {
        val safeId = id % maxOrbits

        return id2orbit.getOrPut(safeId) {
            Orbit(id = id, blockFrames = blockFrames, sampleRate = sampleRate, silentBlocksBeforeTailCheck = silentBlocksBeforeTailCheck)
        }.also {
            it.updateFromVoice(voice)
        }
    }
}
