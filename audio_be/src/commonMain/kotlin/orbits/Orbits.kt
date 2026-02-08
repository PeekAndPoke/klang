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
) {
    private val maxOrbits = maxOrbits.coerceIn(1, 32)
    private val orbits = mutableMapOf<Int, Orbit>()

    /**
     * Clear all orbits
     */
    fun clearAll() {
        for (orbit in orbits.values) {
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
        for (orbit in orbits.values) {
            orbit.processEffects()
        }

        // Step 2: Apply ducking (cross-orbit sidechain)
        for (orbit in orbits.values) {
            val ducking = orbit.ducking
            val duckOrbitId = orbit.duckOrbitId

            if (ducking != null && duckOrbitId != null) {
                // Get sidechain source orbit
                val sidechainOrbit = orbits[duckOrbitId]

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
        for (orbit in orbits.values) {
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

        return orbits.getOrPut(safeId) {
            Orbit(id = id, blockFrames = blockFrames, sampleRate = sampleRate).also {
                it.updateFromVoice(voice)
            }
        }
    }
}
