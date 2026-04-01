package io.peekandpoke.klang.audio_be.cylinders

import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.cylinders.katalyst.KatalystCompressorEffect
import io.peekandpoke.klang.audio_be.cylinders.katalyst.KatalystContext
import io.peekandpoke.klang.audio_be.cylinders.katalyst.KatalystDelayEffect
import io.peekandpoke.klang.audio_be.cylinders.katalyst.KatalystDuckingEffect
import io.peekandpoke.klang.audio_be.cylinders.katalyst.KatalystEffect
import io.peekandpoke.klang.audio_be.cylinders.katalyst.KatalystPhaserEffect
import io.peekandpoke.klang.audio_be.cylinders.katalyst.KatalystReverbEffect
import io.peekandpoke.klang.audio_be.effects.Compressor
import io.peekandpoke.klang.audio_be.effects.DelayLine
import io.peekandpoke.klang.audio_be.effects.Ducking
import io.peekandpoke.klang.audio_be.effects.Phaser
import io.peekandpoke.klang.audio_be.effects.Reverb
import io.peekandpoke.klang.audio_be.voices.Voice

/**
 * Mixing channel / Effect bus — called "Cylinder" in strudel.
 *
 * Each orbit has a composable bus pipeline:
 * **Delay → Reverb → Phaser → Compressor**
 *
 * Ducking runs in a separate pass after all orbits are processed (cross-orbit dependency).
 */
class Cylinder(val id: Int, val blockFrames: Int, sampleRate: Int, private val silentBlocksBeforeTailCheck: Int = 10) {

    // ════════════════════════════════════════════════════════════════════════════
    // Bus pipeline effects
    // ════════════════════════════════════════════════════════════════════════════

    val delay = KatalystDelayEffect(
        delayLine = DelayLine(maxDelaySeconds = 10.0, sampleRate = sampleRate),
    )

    val reverb = KatalystReverbEffect(
        reverb = Reverb(sampleRate),
    )

    val phaser = KatalystPhaserEffect(
        phaser = Phaser(sampleRate),
    )

    val compressor = KatalystCompressorEffect()

    val ducking = KatalystDuckingEffect()

    /**
     * The bus effect pipeline: Delay → Reverb → Phaser → Compressor.
     *
     * Ducking is NOT in this pipeline — it's applied separately by [Cylinders] after all orbits
     * are processed, because it needs cross-orbit access to the sidechain source.
     */
    val pipeline: List<KatalystEffect> = listOf(delay, reverb, phaser, compressor)

    // ════════════════════════════════════════════════════════════════════════════
    // Buffers and context
    // ════════════════════════════════════════════════════════════════════════════

    /** Dry mix buffer — voices sum into this */
    val mixBuffer = StereoBuffer(blockFrames)

    /** Delay send buffer — voices write delay sends here */
    val delaySendBuffer = StereoBuffer(blockFrames)

    /** Reverb send buffer — voices write reverb sends here */
    val reverbSendBuffer = StereoBuffer(blockFrames)

    /** Shared context for all bus effects */
    val katalystContext = KatalystContext(
        blockFrames = blockFrames,
        mixBuffer = mixBuffer,
        delaySendBuffer = delaySendBuffer,
        reverbSendBuffer = reverbSendBuffer,
    )

    // ════════════════════════════════════════════════════════════════════════════
    // State
    // ════════════════════════════════════════════════════════════════════════════

    var isActive = false
        private set

    private var silentBlockCount: Int = 0

    // ════════════════════════════════════════════════════════════════════════════
    // API
    // ════════════════════════════════════════════════════════════════════════════

    /**
     * Update orbit settings from a voice.
     */
    fun updateFromVoice(voice: Voice) {
        isActive = true

        // Delay
        delay.delayLine.delayTimeSeconds = voice.delay.time
        delay.delayLine.feedback = voice.delay.feedback

        // Reverb (reverb.room is used by SendRenderer for send amount)
        reverb.reverb.roomSize = voice.reverb.roomSize.coerceIn(0.0, 1.0)
        reverb.reverb.roomFade = voice.reverb.roomFade
        reverb.reverb.roomLp = voice.reverb.roomLp
        reverb.reverb.roomDim = voice.reverb.roomDim
        reverb.reverb.iResponse = voice.reverb.iResponse

        // Phaser — always update parameters (not just when depth > 0) to avoid stale state
        phaser.phaser.rate = voice.phaser.rate
        phaser.phaser.depth = voice.phaser.depth
        phaser.phaser.centerFreq = if (voice.phaser.center > 0) voice.phaser.center else 1000.0
        phaser.phaser.sweepRange = if (voice.phaser.sweep > 0) voice.phaser.sweep else 1000.0
        phaser.phaser.feedback = 0.5

        // Ducking / Sidechain — reuse instance to preserve envelope state (like compressor)
        val voiceDucking = voice.ducking
        if (voiceDucking != null) {
            ducking.duckCylinderId = voiceDucking.cylinderId
            val existing = ducking.ducking
            if (existing == null) {
                ducking.ducking = Ducking(
                    sampleRate = reverb.reverb.sampleRate,
                    attackSeconds = voiceDucking.attackSeconds,
                    depth = voiceDucking.depth,
                )
            } else {
                existing.attackSeconds = voiceDucking.attackSeconds
                existing.depth = voiceDucking.depth
            }
        } else {
            ducking.clear()
        }

        // Compressor — initialize once so the envelope follower state is preserved across notes.
        // On subsequent calls only update the parameters (e.g. when alternating via `<...>`).
        val compSettings = voice.compressor
        if (compSettings != null) {
            val existing = compressor.compressor
            if (existing == null) {
                compressor.compressor = Compressor(
                    sampleRate = reverb.reverb.sampleRate,
                    thresholdDb = compSettings.thresholdDb,
                    ratio = compSettings.ratio,
                    kneeDb = compSettings.kneeDb,
                    attackSeconds = compSettings.attackSeconds,
                    releaseSeconds = compSettings.releaseSeconds,
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
        if (!isActive) return

        mixBuffer.clear()
        delaySendBuffer.clear()
        reverbSendBuffer.clear()
    }

    /**
     * Processes all bus effects in pipeline order: Delay → Reverb → Phaser → Compressor.
     *
     * Ducking is NOT processed here — see [Cylinders.processAndMix].
     */
    fun processEffects() {
        if (!isActive) return

        for (effect in pipeline) {
            effect.process(katalystContext)
        }
    }

    /**
     * Applies ducking using the resolved sidechain buffer.
     *
     * Called by [Cylinders] after all orbits have processed their main pipeline,
     * since ducking needs cross-orbit access.
     */
    fun processDucking(sidechainMixBuffer: StereoBuffer?) {
        if (!isActive) return

        katalystContext.sidechainBuffer = sidechainMixBuffer
        ducking.process(katalystContext)
        katalystContext.sidechainBuffer = null
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

        if (!isMixBufferSilent()) {
            silentBlockCount = 0
            return
        }

        silentBlockCount++

        if (silentBlockCount < silentBlocksBeforeTailCheck) return

        fun delayHasTail() = delay.delayLine.delayTimeSeconds > 0.001 && delay.delayLine.hasTail()
        fun reverbHasTail() = reverb.reverb.roomSize > 0.001 && reverb.reverb.hasTail()

        if (reverbHasTail() || delayHasTail()) {
            silentBlockCount = 0
            return
        }

        isActive = false
        silentBlockCount = 0
    }

    private fun isMixBufferSilent(): Boolean {
        val threshold = 0.00001f
        for (sample in mixBuffer.left) {
            if (sample > threshold || sample < -threshold) return false
        }
        for (sample in mixBuffer.right) {
            if (sample > threshold || sample < -threshold) return false
        }
        return true
    }
}
