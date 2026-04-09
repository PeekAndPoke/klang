package io.peekandpoke.klang.audio_be.cylinders

import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.voices.Voice

/**
 * Mixing Channels / Effect Buses
 */
class Cylinders(
    maxCylinders: Int = 64,
    private val blockFrames: Int,
    private val sampleRate: Int,
    private val silentBlocksBeforeTailCheck: Int = 10,
) {
    private val maxCylinders = maxCylinders.coerceIn(1, 255)
    private val id2cylinder = mutableMapOf<Int, Cylinder>()
    private var cleanupIndex = 0

    /** Get all cylinders */
    val cylinders get() = id2cylinder.values

    /** Get all currently allocated cylinder IDs. */
    val cylindersIds: Set<Int> get() = id2cylinder.keys

    /**
     * Clear all cylinders
     */
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
    fun getOrInit(id: Int, voice: Voice): Cylinder {
        val safeId = id % maxCylinders

        return id2cylinder.getOrPut(safeId) {
            Cylinder(
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
