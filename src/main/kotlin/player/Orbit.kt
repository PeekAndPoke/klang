package io.peekandpoke.player

import io.peekandpoke.dsp.DelayLine

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
