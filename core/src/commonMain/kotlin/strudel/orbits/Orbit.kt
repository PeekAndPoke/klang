package io.peekandpoke.klang.strudel.orbits

import io.peekandpoke.klang.dsp.DelayLine
import io.peekandpoke.klang.dsp.Reverb
import io.peekandpoke.klang.dsp.StereoBuffer
import io.peekandpoke.klang.strudel.voices.Voice

/**
 * Mixing channel ... called Orbit in strudel
 *
 * TODO: keep track of when the orbit was last used.
 * - when the orbit was inactive from say blockFrames * 2, deactivate it
 *
 */
class Orbit(val id: Int, val blockFrames: Int, sampleRate: Int) {
    // dry mix buffer
    val mixBuffer = StereoBuffer(blockFrames)

    // delay
    val delaySendBuffer = StereoBuffer(blockFrames)
    val delayLine = DelayLine(maxDelaySeconds = 10.0, sampleRate = sampleRate)

    // reverb
    val reverbSendBuffer = StereoBuffer(blockFrames)
    val reverb = Reverb(sampleRate)

    // To track if we need to update parameters this block
    private var isActive = false

    /**
     * Initialize orbit from a voice only if it was NOT yet initialized
     */
    fun initFromVoice(voice: Voice) {
        // Already active?
        if (isActive) return

        isActive = true

        // Delay
        delayLine.delayTimeSeconds = voice.delay.time
        delayLine.feedback = voice.delay.feedback

        // Reverb
        // TODO: check the below ...
        // Use room amount as the send level? Usually room=amount
        // The mix is handled by how much we write to reverbSendBuffer
        // But we can also modulate damping if needed.
        reverb.roomSize = voice.reverb.roomSize.coerceIn(0.0, 1.0)
    }

    fun clear() {
        // Not active?
        if (!isActive) return

        mixBuffer.clear()
        delaySendBuffer.clear()
        reverbSendBuffer.clear()
    }

    fun processEffects() {
        // Not active?
        if (!isActive) return

        // Delay active?
        if (delayLine.delayTimeSeconds > 0.01) {
            delayLine.process(delaySendBuffer, mixBuffer, blockFrames)
        }

        // Reverb active?
        if (reverb.roomSize > 0.01) {
            reverb.process(reverbSendBuffer, mixBuffer, blockFrames)
        }
    }
}
