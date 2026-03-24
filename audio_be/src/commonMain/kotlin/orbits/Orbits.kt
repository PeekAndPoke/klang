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
     * 1. Process all orbit bus pipelines (Delay → Reverb → Phaser → Compressor)
     * 2. Apply ducking (cross-orbit sidechain — requires all orbits processed first)
     * 3. Mix all active orbits to master output
     * 4. Round-robin cleanup check for silent orbits
     */
    fun processAndMix(masterMix: StereoBuffer) {
        // Step 1: Process bus pipeline on all orbits
        for (orbit in id2orbit.values) {
            orbit.processEffects()
        }

        // Step 2: Apply ducking (cross-orbit sidechain)
        for (orbit in id2orbit.values) {
            val duckOrbitId = orbit.ducking.duckOrbitId ?: continue
            val sidechainOrbit = id2orbit[duckOrbitId] ?: continue
            orbit.processDucking(sidechainOrbit.mixBuffer)
        }

        // Step 3: Mix all orbits to output
        for (orbit in id2orbit.values) {
            if (!orbit.isActive) continue

            run {
                val masterLeft = masterMix.left
                val orbitLeft = orbit.mixBuffer.left

                for (i in 0 until blockFrames) {
                    masterLeft[i] = masterLeft[i] + orbitLeft[i]
                }
            }

            run {
                val masterRight = masterMix.right
                val orbitRight = orbit.mixBuffer.right

                for (i in 0 until blockFrames) {
                    masterRight[i] = masterRight[i] + orbitRight[i]
                }
            }
        }

        // Step 4: Cleanup stale orbits (round-robin, no allocation)
        if (id2orbit.isNotEmpty()) {
            val size = id2orbit.size
            val keyIndex = cleanupIndex % size
            // Iterate to the keyIndex-th entry without allocating a list
            var idx = 0
            for ((orbitId, orbit) in id2orbit) {
                if (idx == keyIndex) {
                    orbit.tryDeactivate()
                    break
                }
                idx++
            }
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
            Orbit(
                id = safeId,
                blockFrames = blockFrames,
                sampleRate = sampleRate,
                silentBlocksBeforeTailCheck = silentBlocksBeforeTailCheck
            )
        }.also {
            it.updateFromVoice(voice)
        }
    }
}
