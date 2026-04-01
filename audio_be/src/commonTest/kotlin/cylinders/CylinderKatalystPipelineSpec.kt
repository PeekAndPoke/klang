package io.peekandpoke.klang.audio_be.cylinders

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.voices.Voice
import io.peekandpoke.klang.audio_be.voices.VoiceTestHelpers
import kotlin.math.abs

/**
 * Tests for the refactored Cylinder bus pipeline integration.
 * Verifies that Cylinder correctly delegates to its KatalystEffect pipeline.
 */
class OrbitBusPipelineSpec : StringSpec({

    val sampleRate = 44100
    val blockFrames = 128

    fun createOrbit() = Cylinder(id = 0, blockFrames = blockFrames, sampleRate = sampleRate, silentBlocksBeforeTailCheck = 0)

    "cylinder has 4-stage pipeline: Delay, Reverb, Phaser, Compressor" {
        val cylinder = createOrbit()

        cylinder.pipeline.size shouldBe 4
    }

    "cylinder bus context shares buffers with cylinder" {
        val cylinder = createOrbit()

        cylinder.katalystContext.mixBuffer shouldBe cylinder.mixBuffer
        cylinder.katalystContext.delaySendBuffer shouldBe cylinder.delaySendBuffer
        cylinder.katalystContext.reverbSendBuffer shouldBe cylinder.reverbSendBuffer
    }

    "processEffects runs full pipeline when active" {
        val cylinder = createOrbit()
        val voice = VoiceTestHelpers.createSynthVoice(
            startFrame = 0,
            endFrame = 1000,
            reverb = Voice.Reverb(room = 0.5, roomSize = 0.5),
        )
        cylinder.updateFromVoice(voice)

        // Reverb comb filters need time to build up signal
        repeat(20) {
            cylinder.reverbSendBuffer.left.fill(0.5f)
            cylinder.reverbSendBuffer.right.fill(0.5f)
            cylinder.mixBuffer.clear()
            cylinder.processEffects()
        }

        // Reverb should add signal to mix buffer
        val hasSignal = cylinder.mixBuffer.left.any { it != 0.0f }
        hasSignal shouldBe true
    }

    "processEffects does nothing when inactive" {
        val cylinder = createOrbit()
        // cylinder is NOT active (no updateFromVoice called)

        cylinder.reverbSendBuffer.left.fill(0.5f)

        cylinder.processEffects()

        // Nothing should happen
        cylinder.mixBuffer.left[0] shouldBe 0.0f
    }

    "processDucking applies sidechain ducking" {
        val cylinder = createOrbit()
        val voice = VoiceTestHelpers.createSynthVoice(
            startFrame = 0,
            endFrame = 1000,
            ducking = Voice.Ducking(cylinderId = 1, attackSeconds = 0.001, depth = 1.0),
        )
        cylinder.updateFromVoice(voice)

        cylinder.mixBuffer.left.fill(0.5f)
        cylinder.mixBuffer.right.fill(0.5f)

        // Create loud sidechain signal
        val sidechain = StereoBuffer(blockFrames)
        sidechain.left.fill(0.9f)
        sidechain.right.fill(0.9f)

        cylinder.processDucking(sidechain)

        // Signal should be reduced
        val outputLevel = abs(cylinder.mixBuffer.left[blockFrames - 1])
        (outputLevel < 0.5f) shouldBe true
    }

    "processDucking does nothing with null sidechain" {
        val cylinder = createOrbit()
        val voice = VoiceTestHelpers.createSynthVoice(
            startFrame = 0,
            endFrame = 1000,
            ducking = Voice.Ducking(cylinderId = 1, attackSeconds = 0.001, depth = 1.0),
        )
        cylinder.updateFromVoice(voice)

        cylinder.mixBuffer.left.fill(0.5f)

        cylinder.processDucking(null)

        // Should be unchanged
        cylinder.mixBuffer.left[0] shouldBe 0.5f
    }

    "updateFromVoice configures delay parameters" {
        val cylinder = createOrbit()
        val voice = VoiceTestHelpers.createSynthVoice(
            delay = Voice.Delay(time = 0.5, feedback = 0.3, amount = 0.5),
        )
        cylinder.updateFromVoice(voice)

        cylinder.delay.delayLine.delayTimeSeconds shouldBe 0.5
        cylinder.delay.delayLine.feedback shouldBe 0.3
    }

    "updateFromVoice configures reverb parameters" {
        val cylinder = createOrbit()
        val voice = VoiceTestHelpers.createSynthVoice(
            reverb = Voice.Reverb(room = 0.5, roomSize = 0.7, roomFade = 0.3, roomLp = 5000.0, roomDim = 0.2),
        )
        cylinder.updateFromVoice(voice)

        cylinder.reverb.reverb.roomSize shouldBe 0.7
        cylinder.reverb.reverb.roomFade shouldBe 0.3
        cylinder.reverb.reverb.roomLp shouldBe 5000.0
        cylinder.reverb.reverb.roomDim shouldBe 0.2
    }

    "updateFromVoice configures phaser parameters" {
        val cylinder = createOrbit()
        val voice = VoiceTestHelpers.createSynthVoice(
            phaser = Voice.Phaser(rate = 2.0, depth = 0.5, center = 800.0, sweep = 600.0),
        )
        cylinder.updateFromVoice(voice)

        cylinder.phaser.phaser.rate shouldBe 2.0
        cylinder.phaser.phaser.depth shouldBe 0.5
        cylinder.phaser.phaser.centerFreq shouldBe 800.0
        cylinder.phaser.phaser.sweepRange shouldBe 600.0
    }

    "updateFromVoice configures ducking" {
        val cylinder = createOrbit()
        val voice = VoiceTestHelpers.createSynthVoice(
            ducking = Voice.Ducking(cylinderId = 2, attackSeconds = 0.05, depth = 0.8),
        )
        cylinder.updateFromVoice(voice)

        cylinder.ducking.duckCylinderId shouldBe 2
        cylinder.ducking.ducking shouldNotBe null
        cylinder.ducking.ducking!!.depth shouldBe 0.8
    }

    "updateFromVoice configures compressor" {
        val cylinder = createOrbit()
        val voice = VoiceTestHelpers.createSynthVoice(
            compressor = Voice.Compressor(
                thresholdDb = -15.0,
                ratio = 3.0,
                kneeDb = 4.0,
                attackSeconds = 0.005,
                releaseSeconds = 0.2,
            ),
        )
        cylinder.updateFromVoice(voice)

        val c = cylinder.compressor.compressor!!
        c.thresholdDb shouldBe -15.0
        c.ratio shouldBe 3.0
    }

    "clear resets all buffers" {
        val cylinder = createOrbit()
        cylinder.updateFromVoice(VoiceTestHelpers.createSynthVoice())

        cylinder.mixBuffer.left.fill(0.5f)
        cylinder.delaySendBuffer.left.fill(0.3f)
        cylinder.reverbSendBuffer.left.fill(0.2f)

        cylinder.clear()

        cylinder.mixBuffer.left[0] shouldBe 0.0f
        cylinder.delaySendBuffer.left[0] shouldBe 0.0f
        cylinder.reverbSendBuffer.left[0] shouldBe 0.0f
    }
})
