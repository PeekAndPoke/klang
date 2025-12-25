package io.peekandpoke.player.orbits

import io.peekandpoke.dsp.DelayLine
import io.peekandpoke.player.StereoBuffer
import io.peekandpoke.player.voices.Voice

class Orbit(val id: Int, val blockFrames: Int, sampleRate: Int) {
    // dry mix buffer
    val mixBuffer = StereoBuffer(blockFrames)

    // wet mix buffer
    val delaySendBuffer = StereoBuffer(blockFrames)

    // delay line
    val delayLine = DelayLine(maxDelaySeconds = 10.0, sampleRate = sampleRate)

    // To track if we need to update parameters this block
    private var initialized = false

    /**
     * Initialize orbit from a voice only if it was NOT yet initialized
     */
    fun initFromVoice(voice: Voice) {
        if (!initialized) {
            initialized = true

            delayLine.delayTimeSeconds = voice.delay.time
            delayLine.feedback = voice.delay.feedback
        }
    }

    fun clear() {
        mixBuffer.clear()
        delaySendBuffer.clear()
    }

    fun processEffects() {
        if (initialized && delayLine.delayTimeSeconds > 0.01) {
            delayLine.process(delaySendBuffer, mixBuffer, blockFrames)
        }
    }
}
