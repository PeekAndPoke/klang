package io.peekandpoke.player

import io.peekandpoke.dsp.DelayLine

internal class Orbits(
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
    fun processAndMix(masterMixBuffer: DoubleArray) {
        for (orbit in orbits.values) {
            orbit.processEffects()

            // Sum orbit mix into master mix
            for (i in 0 until blockFrames) {
                masterMixBuffer[i] += orbit.mixBuffer[i]
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
    fun getOrInit(id: Int, voice: StrudelPlayer.Voice): Orbit {
        val safeId = id % maxOrbits

        return orbits.getOrPut(safeId) {
            Orbit(id = id, blockFrames = blockFrames, sampleRate = sampleRate).also {
                it.initFromVoice(voice)
            }
        }
    }
}

internal class Orbit(val id: Int, val blockFrames: Int, sampleRate: Int) {
    val mixBuffer = DoubleArray(blockFrames)
    val delaySendBuffer = DoubleArray(blockFrames)
    val delayLine = DelayLine(maxDelaySeconds = 10.0, sampleRate = sampleRate)

    // To track if we need to update parameters this block
    private var initialized = false

    /**
     * Initialize orbit from a voice only if it was NOT yet initialized
     */
    fun initFromVoice(voice: StrudelPlayer.Voice) {
        if (!initialized) {
            initialized = true

            delayLine.delayTimeSeconds = voice.delay.time
            delayLine.feedback = voice.delay.feedback
        }
    }

    fun clear() {
        mixBuffer.fill(0.0)
        delaySendBuffer.fill(0.0)
    }

    fun processEffects() {
        if (initialized && delayLine.delayTimeSeconds > 0.01) {
            delayLine.process(delaySendBuffer, mixBuffer, blockFrames)
        }
    }
}
