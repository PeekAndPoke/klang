package io.peekandpoke.klang.audio_be.orbits

import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.effects.*
import io.peekandpoke.klang.audio_be.voices.Voice

/**
 * Mixing channel / Effect bus ... called Orbit in strudel
 */
class Orbit(val id: Int, val blockFrames: Int, sampleRate: Int, private val silentBlocksBeforeTailCheck: Int) {
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

    // Compressor
    /** Compressor processor instance */
    var compressor: Compressor? = null
        private set

    // To track if we need to update parameters this block
    var isActive = false
        private set

    // Silent block counter for graceful deactivation (allows effect tails to decay)
    private var silentBlockCount: Int = 0

    /**
     * Update orbit settings from a voice.
     */
    fun updateFromVoice(voice: Voice) {
        isActive = true

        // Delay
        delayLine.delayTimeSeconds = voice.delay.time
        delayLine.feedback = voice.delay.feedback

        // Reverb (reverb.room is used Voice.mixToOrbit)
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

        // Compressor — initialize once so the envelope follower state is preserved across notes.
        // On subsequent calls only update the parameters (e.g. when alternating via `<...>`).
        val compSettings = voice.compressor
        if (compSettings != null) {
            val existing = compressor
            if (existing == null) {
                compressor = Compressor(
                    sampleRate = reverb.sampleRate,
                    thresholdDb = compSettings.thresholdDb,
                    ratio = compSettings.ratio,
                    kneeDb = compSettings.kneeDb,
                    attackSeconds = compSettings.attackSeconds,
                    releaseSeconds = compSettings.releaseSeconds
                )
            } else {
                existing.thresholdDb = compSettings.thresholdDb
                existing.ratio = compSettings.ratio
                existing.kneeDb = compSettings.kneeDb
                existing.attackSeconds = compSettings.attackSeconds
                existing.releaseSeconds = compSettings.releaseSeconds
            }
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

        // Compressor active? (Insert effect on the mix bus)
        compressor?.process(mixBuffer.left, mixBuffer.right, blockFrames)
    }

    /**
     * Checks if the orbit is silent and deactivates it if so.
     *
     * Uses a two-phase approach to avoid cutting off effect tails (delay/reverb):
     * 1. When mixBuffer is silent, increment a counter instead of deactivating immediately.
     *    This grace period keeps effects processing so their tails continue to decay naturally.
     * 2. After N silent blocks, scan effect internal buffers. If they still have audio, reset
     *    the counter and keep processing. If silent, deactivate.
     */
    fun tryDeactivate() {
        if (!isActive) return

        // Check if mixBuffer has audio
        if (!isMixBufferSilent()) {
            silentBlockCount = 0
            return
        }

        // mixBuffer is silent — count up
        silentBlockCount++

        // Grace period: keep processing effects for N blocks
        if (silentBlockCount < silentBlocksBeforeTailCheck) return

        // After grace period: check if effects still have tail audio
        fun delayHasTail() = delayLine.delayTimeSeconds > 0.001 && delayLine.hasTail()
        fun reverbHasTail() = reverb.roomSize > 0.001 && reverb.hasTail()

        if (reverbHasTail() || delayHasTail()) {
            silentBlockCount = 0
            return
        }

        // Everything is silent — deactivate
        isActive = false
        silentBlockCount = 0
    }

    private fun isMixBufferSilent(): Boolean {
        val threshold = 0.00001
        for (sample in mixBuffer.left) {
            if (sample > threshold || sample < -threshold) return false
        }
        for (sample in mixBuffer.right) {
            if (sample > threshold || sample < -threshold) return false
        }
        return true
    }
}
