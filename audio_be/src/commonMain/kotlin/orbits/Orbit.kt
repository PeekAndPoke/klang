package io.peekandpoke.klang.audio_be.orbits

import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.effects.DelayLine
import io.peekandpoke.klang.audio_be.effects.Ducking
import io.peekandpoke.klang.audio_be.effects.Phaser
import io.peekandpoke.klang.audio_be.effects.Reverb
import io.peekandpoke.klang.audio_be.voices.Voice

/**
 * Mixing channel / Effect bus ... called Orbit in strudel
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

    // phaser (insert effect)
    val phaser = Phaser(sampleRate)

    // Ducking / Sidechain
    /** Sidechain source orbit ID (which orbit triggers the ducking) */
    var duckOrbitId: Int? = null
        private set

    /** Ducking processor instance */
    var ducking: Ducking? = null
        private set

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
        reverb.roomFade = voice.reverb.roomFade
        reverb.roomLp = voice.reverb.roomLp
        reverb.roomDim = voice.reverb.roomDim
        reverb.iResponse = voice.reverb.iResponse

        // Phaser
        if (voice.phaser.depth > 0) {
            phaser.rate = voice.phaser.rate
            phaser.depth = voice.phaser.depth
            phaser.centerFreq = if (voice.phaser.center > 0) voice.phaser.center else 1000.0
            phaser.sweepRange = if (voice.phaser.sweep > 0) voice.phaser.sweep else 1000.0
            // Feedback usually fixed or tied to depth in simple models, or add a param later
            phaser.feedback = 0.5
        }

        // Ducking / Sidechain
        voice.ducking?.let { voiceDucking ->
            duckOrbitId = voiceDucking.orbitId
            ducking = Ducking(
                sampleRate = reverb.sampleRate,
                attackSeconds = voiceDucking.attackSeconds,
                depth = voiceDucking.depth
            )
        }
    }

    fun clear() {
        // Not active?
        if (!isActive) return

        mixBuffer.clear()
        delaySendBuffer.clear()
        reverbSendBuffer.clear()
        // Phaser is insert, no separate buffer needed if processed in-place on mixBuffer
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

        // Phaser active? (Insert effect on the mix bus)
        if (phaser.depth > 0.01) {
            phaser.process(mixBuffer, blockFrames)
        }
    }
}
