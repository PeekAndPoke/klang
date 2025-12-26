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
     */
    fun processAndMix(masterMix: StereoBuffer) {
        for (orbit in orbits.values) {
            orbit.processEffects()

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
                it.initFromVoice(voice)
            }
        }
    }
}
