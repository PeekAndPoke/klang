/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.cylinders.katalyst.KatalystBodyEffect
import io.peekandpoke.klang.audio_be.cylinders.katalyst.KatalystFormantEffect
import io.peekandpoke.klang.audio_be.voices.Voice
import io.peekandpoke.klang.audio_be.voices.VoiceTestHelpers
import io.peekandpoke.klang.audio_bridge.FilterDef
import kotlin.math.abs

/**
 * Tests for the refactored Cylinder bus pipeline integration.
 * Verifies that Cylinder correctly delegates to its KatalystEffect pipeline.
 */
class OrbitBusPipelineSpec : StringSpec({

    val sampleRate = 44100
    val blockFrames = 128

    fun createOrbit() = Cylinder(id = 0, blockFrames = blockFrames, sampleRate = sampleRate, silentBlocksBeforeTailCheck = 0)

    val woodBody = FilterDef.Body(bands = listOf(FilterDef.Body.Mode(freq = 300.0, db = 6.0, q = 8.0)), mix = 1.0)

    // True if the orbit's body resonator is active — a body on a DC mix blends it away from 1.0.
    fun bodyActiveOn(cylinder: Cylinder): Boolean {
        cylinder.mixBuffer.left.fill(1.0)
        cylinder.mixBuffer.right.fill(1.0)
        cylinder.processEffects()
        return cylinder.mixBuffer.left[blockFrames - 1] != 1.0
    }

    "cylinder has 6-stage pipeline: Body, Vowel, Delay, Reverb, Phaser, Compressor" {
        val cylinder = createOrbit()

        cylinder.pipeline.size shouldBe 6
    }

    "orbit runs exactly ONE body and ONE vowel pass regardless of voice count (per-orbit, not per-voice)" {
        val cylinder = createOrbit()

        cylinder.pipeline.filterIsInstance<KatalystBodyEffect>().size shouldBe 1
        cylinder.pipeline.filterIsInstance<KatalystFormantEffect>().size shouldBe 1
        // body/vowel run first — before the time/dynamics effects.
        (cylinder.pipeline[0] is KatalystBodyEffect) shouldBe true
        (cylinder.pipeline[1] is KatalystFormantEffect) shouldBe true
    }

    "body is owned by the first voice to set it; a later non-body owner turns it off (lease hand-off)" {
        val cylinder = createOrbit()
        val bf = blockFrames

        // Voice A (has body) claims the orbit body at block 0.
        cylinder.updateFromVoice(VoiceTestHelpers.createSynthVoice(body = woodBody), blockStart = 0)
        bodyActiveOn(cylinder) shouldBe true

        // A stops checking in; voice B (no body) claims after the 1-block grace → body turns OFF.
        cylinder.updateFromVoice(VoiceTestHelpers.createSynthVoice(), blockStart = 2 * bf)
        bodyActiveOn(cylinder) shouldBe false
    }

    "while the body owner is alive, a non-body voice on the same orbit does NOT turn the body off" {
        val cylinder = createOrbit()
        val bf = blockFrames

        cylinder.updateFromVoice(VoiceTestHelpers.createSynthVoice(body = woodBody), blockStart = 0) // A owns
        cylinder.updateFromVoice(VoiceTestHelpers.createSynthVoice(), blockStart = bf)               // B within grace → denied
        bodyActiveOn(cylinder) shouldBe true // still A's body
    }

    "one lease owns ALL bus effects: a second voice cannot change reverb/delay while the owner is alive" {
        val cylinder = createOrbit()
        cylinder.updateFromVoice(
            VoiceTestHelpers.createSynthVoice(
                reverb = Voice.Reverb(room = 0.5, roomSize = 0.7),
                delay = Voice.Delay(amount = 0.5, time = 0.3, feedback = 0.4),
            ),
            blockStart = 0,
        )
        cylinder.reverb.reverb.roomSize shouldBe 0.7
        cylinder.delay.delayLine.delayTimeSeconds shouldBe 0.3

        // Different voice, same block → denied → owner's settings persist.
        cylinder.updateFromVoice(
            VoiceTestHelpers.createSynthVoice(
                reverb = Voice.Reverb(room = 0.5, roomSize = 0.2),
                delay = Voice.Delay(amount = 0.5, time = 0.9, feedback = 0.1),
            ),
            blockStart = 0,
        )
        cylinder.reverb.reverb.roomSize shouldBe 0.7
        cylinder.delay.delayLine.delayTimeSeconds shouldBe 0.3
    }

    "when the orbit owner ends, a new voice takes over and its bus settings apply" {
        val cylinder = createOrbit()
        val bf = blockFrames
        cylinder.updateFromVoice(
            VoiceTestHelpers.createSynthVoice(reverb = Voice.Reverb(room = 0.5, roomSize = 0.7)), blockStart = 0,
        )
        cylinder.reverb.reverb.roomSize shouldBe 0.7

        cylinder.updateFromVoice(
            VoiceTestHelpers.createSynthVoice(reverb = Voice.Reverb(room = 0.5, roomSize = 0.2)), blockStart = 2 * bf,
        )
        cylinder.reverb.reverb.roomSize shouldBe 0.2 // new owner's
    }

    "deactivating clears the reverb tail even after the owner switched reverb off (no stale-tail leak on reuse)" {
        val cylinder = createOrbit() // silentBlocksBeforeTailCheck = 0
        val bf = blockFrames

        // Owner A: reverb on — build up a comb-filter tail.
        cylinder.updateFromVoice(
            VoiceTestHelpers.createSynthVoice(reverb = Voice.Reverb(room = 0.8, roomSize = 0.8)), blockStart = 0,
        )
        repeat(20) {
            cylinder.reverbSendBuffer.left.fill(0.5)
            cylinder.reverbSendBuffer.right.fill(0.5)
            cylinder.mixBuffer.clear()
            cylinder.processEffects()
        }
        cylinder.reverb.reverb.hasTail() shouldBe true

        // Owner A ends; a no-reverb voice takes over → roomSize 0 (but the comb buffers are still full).
        cylinder.updateFromVoice(
            VoiceTestHelpers.createSynthVoice(reverb = Voice.Reverb(room = 0.0, roomSize = 0.0)), blockStart = 2 * bf,
        )
        cylinder.reverb.reverb.roomSize shouldBe 0.0
        cylinder.reverb.reverb.hasTail() shouldBe true // params don't clear the buffers

        // Orbit goes silent → tryDeactivate (roomSize 0 short-circuits its tail check) → resetBusEffects.
        cylinder.mixBuffer.left.fill(0.0)
        cylinder.mixBuffer.right.fill(0.0)
        cylinder.tryDeactivate()

        cylinder.isActive shouldBe false
        cylinder.reverb.reverb.hasTail() shouldBe false // FIX A: tail cleared on lease free
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
        cylinder.updateFromVoice(voice, blockStart = 0)

        // Reverb comb filters need time to build up signal
        repeat(20) {
            cylinder.reverbSendBuffer.left.fill(0.5)
            cylinder.reverbSendBuffer.right.fill(0.5)
            cylinder.mixBuffer.clear()
            cylinder.processEffects()
        }

        // Reverb should add signal to mix buffer
        val hasSignal = cylinder.mixBuffer.left.any { it != 0.0 }
        hasSignal shouldBe true
    }

    "processEffects does nothing when inactive" {
        val cylinder = createOrbit()
        // cylinder is NOT active (no updateFromVoice called)

        cylinder.reverbSendBuffer.left.fill(0.5)

        cylinder.processEffects()

        // Nothing should happen
        cylinder.mixBuffer.left[0] shouldBe 0.0
    }

    "processDucking applies sidechain ducking" {
        val cylinder = createOrbit()
        val voice = VoiceTestHelpers.createSynthVoice(
            startFrame = 0,
            endFrame = 1000,
            ducking = Voice.Ducking(cylinderId = 1, attackSeconds = 0.001, depth = 1.0),
        )
        cylinder.updateFromVoice(voice, blockStart = 0)

        cylinder.mixBuffer.left.fill(0.5)
        cylinder.mixBuffer.right.fill(0.5)

        // Create loud sidechain signal
        val sidechain = StereoBuffer(blockFrames)
        sidechain.left.fill(0.9)
        sidechain.right.fill(0.9)

        cylinder.processDucking(sidechain)

        // Signal should be reduced
        val outputLevel = abs(cylinder.mixBuffer.left[blockFrames - 1])
        (outputLevel < 0.5) shouldBe true
    }

    "processDucking does nothing with null sidechain" {
        val cylinder = createOrbit()
        val voice = VoiceTestHelpers.createSynthVoice(
            startFrame = 0,
            endFrame = 1000,
            ducking = Voice.Ducking(cylinderId = 1, attackSeconds = 0.001, depth = 1.0),
        )
        cylinder.updateFromVoice(voice, blockStart = 0)

        cylinder.mixBuffer.left.fill(0.5)

        cylinder.processDucking(null)

        // Should be unchanged
        cylinder.mixBuffer.left[0] shouldBe 0.5
    }

    "updateFromVoice configures delay parameters" {
        val cylinder = createOrbit()
        val voice = VoiceTestHelpers.createSynthVoice(
            delay = Voice.Delay(time = 0.5, feedback = 0.3, amount = 0.5),
        )
        cylinder.updateFromVoice(voice, blockStart = 0)

        cylinder.delay.delayLine.delayTimeSeconds shouldBe 0.5
        cylinder.delay.delayLine.feedback shouldBe 0.3
    }

    "updateFromVoice configures reverb parameters" {
        val cylinder = createOrbit()
        val voice = VoiceTestHelpers.createSynthVoice(
            reverb = Voice.Reverb(room = 0.5, roomSize = 0.7, roomFade = 0.3, roomLp = 5000.0, roomDim = 0.2),
        )
        cylinder.updateFromVoice(voice, blockStart = 0)

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
        cylinder.updateFromVoice(voice, blockStart = 0)

        cylinder.phaser.phaser.rate shouldBe 2.0
        cylinder.phaser.phaser.depth shouldBe 0.5
        cylinder.phaser.phaser.center shouldBe 800.0
        cylinder.phaser.phaser.sweep shouldBe 600.0
    }

    "updateFromVoice configures ducking" {
        val cylinder = createOrbit()
        val voice = VoiceTestHelpers.createSynthVoice(
            ducking = Voice.Ducking(cylinderId = 2, attackSeconds = 0.05, depth = 0.8),
        )
        cylinder.updateFromVoice(voice, blockStart = 0)

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
        cylinder.updateFromVoice(voice, blockStart = 0)

        val c = cylinder.compressor.compressor!!
        c.thresholdDb shouldBe -15.0
        c.ratio shouldBe 3.0
    }

    "clear resets all buffers" {
        val cylinder = createOrbit()
        cylinder.updateFromVoice(VoiceTestHelpers.createSynthVoice(), blockStart = 0)

        cylinder.mixBuffer.left.fill(0.5)
        cylinder.delaySendBuffer.left.fill(0.3)
        cylinder.reverbSendBuffer.left.fill(0.2)

        cylinder.clear()

        cylinder.mixBuffer.left[0] shouldBe 0.0
        cylinder.delaySendBuffer.left[0] shouldBe 0.0
        cylinder.reverbSendBuffer.left[0] shouldBe 0.0
    }
})
