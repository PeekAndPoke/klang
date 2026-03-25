package io.peekandpoke.klang.audio_be.orbits

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.voices.Voice
import io.peekandpoke.klang.audio_be.voices.VoiceTestHelpers
import kotlin.math.abs

/**
 * Tests for the refactored Orbit bus pipeline integration.
 * Verifies that Orbit correctly delegates to its BusEffect pipeline.
 */
class OrbitBusPipelineSpec : StringSpec({

    val sampleRate = 44100
    val blockFrames = 128

    fun createOrbit() = Orbit(id = 0, blockFrames = blockFrames, sampleRate = sampleRate, silentBlocksBeforeTailCheck = 0)

    "orbit has 4-stage pipeline: Delay, Reverb, Phaser, Compressor" {
        val orbit = createOrbit()

        orbit.pipeline.size shouldBe 4
    }

    "orbit bus context shares buffers with orbit" {
        val orbit = createOrbit()

        orbit.busContext.mixBuffer shouldBe orbit.mixBuffer
        orbit.busContext.delaySendBuffer shouldBe orbit.delaySendBuffer
        orbit.busContext.reverbSendBuffer shouldBe orbit.reverbSendBuffer
    }

    "processEffects runs full pipeline when active" {
        val orbit = createOrbit()
        val voice = VoiceTestHelpers.createSynthVoice(
            startFrame = 0,
            endFrame = 1000,
            reverb = Voice.Reverb(room = 0.5, roomSize = 0.5),
        )
        orbit.updateFromVoice(voice)

        // Reverb comb filters need time to build up signal
        repeat(20) {
            orbit.reverbSendBuffer.left.fill(0.5f)
            orbit.reverbSendBuffer.right.fill(0.5f)
            orbit.mixBuffer.clear()
            orbit.processEffects()
        }

        // Reverb should add signal to mix buffer
        val hasSignal = orbit.mixBuffer.left.any { it != 0.0f }
        hasSignal shouldBe true
    }

    "processEffects does nothing when inactive" {
        val orbit = createOrbit()
        // orbit is NOT active (no updateFromVoice called)

        orbit.reverbSendBuffer.left.fill(0.5f)

        orbit.processEffects()

        // Nothing should happen
        orbit.mixBuffer.left[0] shouldBe 0.0f
    }

    "processDucking applies sidechain ducking" {
        val orbit = createOrbit()
        val voice = VoiceTestHelpers.createSynthVoice(
            startFrame = 0,
            endFrame = 1000,
            ducking = Voice.Ducking(orbitId = 1, attackSeconds = 0.001, depth = 1.0),
        )
        orbit.updateFromVoice(voice)

        orbit.mixBuffer.left.fill(0.5f)
        orbit.mixBuffer.right.fill(0.5f)

        // Create loud sidechain signal
        val sidechain = StereoBuffer(blockFrames)
        sidechain.left.fill(0.9f)
        sidechain.right.fill(0.9f)

        orbit.processDucking(sidechain)

        // Signal should be reduced
        val outputLevel = abs(orbit.mixBuffer.left[blockFrames - 1])
        (outputLevel < 0.5f) shouldBe true
    }

    "processDucking does nothing with null sidechain" {
        val orbit = createOrbit()
        val voice = VoiceTestHelpers.createSynthVoice(
            startFrame = 0,
            endFrame = 1000,
            ducking = Voice.Ducking(orbitId = 1, attackSeconds = 0.001, depth = 1.0),
        )
        orbit.updateFromVoice(voice)

        orbit.mixBuffer.left.fill(0.5f)

        orbit.processDucking(null)

        // Should be unchanged
        orbit.mixBuffer.left[0] shouldBe 0.5f
    }

    "updateFromVoice configures delay parameters" {
        val orbit = createOrbit()
        val voice = VoiceTestHelpers.createSynthVoice(
            delay = Voice.Delay(time = 0.5, feedback = 0.3, amount = 0.5),
        )
        orbit.updateFromVoice(voice)

        orbit.delay.delayLine.delayTimeSeconds shouldBe 0.5
        orbit.delay.delayLine.feedback shouldBe 0.3
    }

    "updateFromVoice configures reverb parameters" {
        val orbit = createOrbit()
        val voice = VoiceTestHelpers.createSynthVoice(
            reverb = Voice.Reverb(room = 0.5, roomSize = 0.7, roomFade = 0.3, roomLp = 5000.0, roomDim = 0.2),
        )
        orbit.updateFromVoice(voice)

        orbit.reverb.reverb.roomSize shouldBe 0.7
        orbit.reverb.reverb.roomFade shouldBe 0.3
        orbit.reverb.reverb.roomLp shouldBe 5000.0
        orbit.reverb.reverb.roomDim shouldBe 0.2
    }

    "updateFromVoice configures phaser parameters" {
        val orbit = createOrbit()
        val voice = VoiceTestHelpers.createSynthVoice(
            phaser = Voice.Phaser(rate = 2.0, depth = 0.5, center = 800.0, sweep = 600.0),
        )
        orbit.updateFromVoice(voice)

        orbit.phaser.phaser.rate shouldBe 2.0
        orbit.phaser.phaser.depth shouldBe 0.5
        orbit.phaser.phaser.centerFreq shouldBe 800.0
        orbit.phaser.phaser.sweepRange shouldBe 600.0
    }

    "updateFromVoice configures ducking" {
        val orbit = createOrbit()
        val voice = VoiceTestHelpers.createSynthVoice(
            ducking = Voice.Ducking(orbitId = 2, attackSeconds = 0.05, depth = 0.8),
        )
        orbit.updateFromVoice(voice)

        orbit.ducking.duckOrbitId shouldBe 2
        orbit.ducking.ducking shouldNotBe null
        orbit.ducking.ducking!!.depth shouldBe 0.8
    }

    "updateFromVoice configures compressor" {
        val orbit = createOrbit()
        val voice = VoiceTestHelpers.createSynthVoice(
            compressor = Voice.Compressor(
                thresholdDb = -15.0,
                ratio = 3.0,
                kneeDb = 4.0,
                attackSeconds = 0.005,
                releaseSeconds = 0.2,
            ),
        )
        orbit.updateFromVoice(voice)

        val c = orbit.compressor.compressor!!
        c.thresholdDb shouldBe -15.0
        c.ratio shouldBe 3.0
    }

    "clear resets all buffers" {
        val orbit = createOrbit()
        orbit.updateFromVoice(VoiceTestHelpers.createSynthVoice())

        orbit.mixBuffer.left.fill(0.5f)
        orbit.delaySendBuffer.left.fill(0.3f)
        orbit.reverbSendBuffer.left.fill(0.2f)

        orbit.clear()

        orbit.mixBuffer.left[0] shouldBe 0.0f
        orbit.delaySendBuffer.left[0] shouldBe 0.0f
        orbit.reverbSendBuffer.left[0] shouldBe 0.0f
    }
})
