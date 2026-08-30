/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
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
import kotlin.math.sin

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
        cylinder.updateFromVoice(VoiceTestHelpers.createSynthVoice(body = woodBody), blockStart = 0.0)
        bodyActiveOn(cylinder) shouldBe true

        // A stops checking in; voice B (no body) claims after the 1-block grace → body turns OFF.
        cylinder.updateFromVoice(VoiceTestHelpers.createSynthVoice(), blockStart = 2.0 * bf)
        bodyActiveOn(cylinder) shouldBe false
    }

    "while the body owner is alive, a non-body voice on the same orbit does NOT turn the body off" {
        val cylinder = createOrbit()
        val bf = blockFrames

        cylinder.updateFromVoice(VoiceTestHelpers.createSynthVoice(body = woodBody), blockStart = 0.0) // A owns
        cylinder.updateFromVoice(VoiceTestHelpers.createSynthVoice(), blockStart = bf.toDouble())               // B within grace → denied
        bodyActiveOn(cylinder) shouldBe true // still A's body
    }

    "one lease owns ALL bus effects: a second voice cannot change reverb/delay while the owner is alive" {
        val cylinder = createOrbit()
        cylinder.updateFromVoice(
            VoiceTestHelpers.createSynthVoice(
                reverb = Voice.Reverb(room = 0.5, roomSize = 0.7),
                delay = Voice.Delay(amount = 0.5, time = 0.3, feedback = 0.4),
            ),
            blockStart = 0.0,
        )
        cylinder.reverb.reverb.roomSize shouldBe 0.7
        cylinder.delay.delayLine.delayTimeSeconds shouldBe 0.3

        // Different voice, same block → denied → owner's settings persist.
        cylinder.updateFromVoice(
            VoiceTestHelpers.createSynthVoice(
                reverb = Voice.Reverb(room = 0.5, roomSize = 0.2),
                delay = Voice.Delay(amount = 0.5, time = 0.9, feedback = 0.1),
            ),
            blockStart = 0.0,
        )
        cylinder.reverb.reverb.roomSize shouldBe 0.7
        cylinder.delay.delayLine.delayTimeSeconds shouldBe 0.3
    }

    "when the orbit owner ends, a new voice takes over and its bus settings apply" {
        val cylinder = createOrbit()
        val bf = blockFrames
        cylinder.updateFromVoice(
            VoiceTestHelpers.createSynthVoice(reverb = Voice.Reverb(room = 0.5, roomSize = 0.7)), blockStart = 0.0,
        )
        cylinder.reverb.reverb.roomSize shouldBe 0.7

        cylinder.updateFromVoice(
            VoiceTestHelpers.createSynthVoice(reverb = Voice.Reverb(room = 0.5, roomSize = 0.2)), blockStart = 2.0 * bf,
        )
        cylinder.reverb.reverb.roomSize shouldBe 0.2 // new owner's
    }

    "switching reverb off starts the drain: the orbit rings out, stays alive, then deactivates clean" {
        // The drain-lifecycle rewrite of the old stale-tail-leak row: an off-takeover used to
        // FREEZE the comb network (invisible to the param-gated tail check, cut by the next
        // deactivation); now the tail mixes out on its own timeline and cleanup waits for it.
        val cylinder = createOrbit() // silentBlocksBeforeTailCheck = 0
        val bf = blockFrames

        // Owner A: reverb on — build up a comb-filter tail (small room = a drain the test can afford).
        cylinder.updateFromVoice(
            VoiceTestHelpers.createSynthVoice(reverb = Voice.Reverb(room = 0.8, roomSize = 0.05)), blockStart = 0.0,
        )
        repeat(20) {
            cylinder.reverbSendBuffer.left.fill(0.5)
            cylinder.reverbSendBuffer.right.fill(0.5)
            cylinder.mixBuffer.clear()
            cylinder.processEffects()
        }
        cylinder.reverb.hasTail() shouldBe true

        // Owner A ends; a no-reverb voice takes over → the off-config starts the DRAIN under the
        // RETAINED params (the countdown decays at owner A's room, not the new owner's 0.0).
        cylinder.updateFromVoice(
            VoiceTestHelpers.createSynthVoice(reverb = Voice.Reverb(room = 0.0, roomSize = 0.0)), blockStart = 2.0 * bf,
        )
        cylinder.reverb.reverb.roomSize shouldBe 0.05 // retained
        cylinder.reverb.hasTail() shouldBe true // draining — VISIBLE to cleanup now

        // The tail CHECK itself must hold the orbit, not just the mix-silence gate: with the mix
        // cleared, only the reverbHasTail() wiring stands between a charged drain and
        // deactivation (mutation campaign: `reverbHasTail() = false` survived without this).
        cylinder.mixBuffer.clear()
        cylinder.tryDeactivate()
        cylinder.isActive shouldBe true

        // The tail keeps SOUNDING while it drains, and the orbit must not deactivate under it
        // (the old param-gated check cut exactly here).
        cylinder.clear()
        cylinder.processEffects()
        cylinder.mixBuffer.left.any { it > 0.001 || it < -0.001 } shouldBe true
        cylinder.tryDeactivate()
        cylinder.isActive shouldBe true

        // Run the production block loop until the countdown's terminal reset flips the tail off.
        var blocks = 0
        while (cylinder.reverb.hasTail() && blocks < 1200) {
            cylinder.clear()
            cylinder.processEffects()
            cylinder.tryDeactivate()
            blocks++
        }
        (blocks < 1200) shouldBe true // the drain terminated on its own schedule

        // One silent round finishes deactivation if the final drain block was still audible.
        cylinder.mixBuffer.clear()
        cylinder.tryDeactivate()

        cylinder.isActive shouldBe false
        cylinder.reverb.reverb.hasTail(0.0) shouldBe false // literally zero on lease free
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
            startFrame = 0.0,
            endFrame = 1000.0,
            reverb = Voice.Reverb(room = 0.5, roomSize = 0.5),
        )
        cylinder.updateFromVoice(voice, blockStart = 0.0)

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
            startFrame = 0.0,
            endFrame = 1000.0,
            ducking = Voice.Ducking(cylinderId = 1, attackSeconds = 0.001, depth = 1.0),
        )
        cylinder.updateFromVoice(voice, blockStart = 0.0)

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
            startFrame = 0.0,
            endFrame = 1000.0,
            ducking = Voice.Ducking(cylinderId = 1, attackSeconds = 0.001, depth = 1.0),
        )
        cylinder.updateFromVoice(voice, blockStart = 0.0)

        cylinder.mixBuffer.left.fill(0.5)

        cylinder.processDucking(null)

        // Should be unchanged
        cylinder.mixBuffer.left[0] shouldBe 0.5
    }

    "a reused orbit starts the phaser from a clean slate: cascade cleared AND sweep zeroed" {
        // Review round 2: the leaf resets were guarded but the resetBusEffects WIRING was an
        // unkilled mutation, and the LFO phase deliberately surviving Phaser.reset() (right for
        // the bypass path) is wrong across a full teardown — the carried phase would be "blocks
        // the previous life stayed active x rate", a cleanup-schedule artifact.
        fun phaserVoice() = VoiceTestHelpers.createSynthVoice(
            phaser = Voice.Phaser(rate = 1.7, depth = 0.8, center = 1200.0, sweep = 900.0),
        )

        fun fillTone(cylinder: Cylinder) {
            for (i in 0 until blockFrames) {
                val v = sin(0.07 * i)
                cylinder.mixBuffer.left[i] = v
                cylinder.mixBuffer.right[i] = v
            }
        }

        // Life 1: engaged phaser, several blocks of signal — cascade and LFO both move.
        val reused = createOrbit()
        reused.updateFromVoice(phaserVoice(), blockStart = 0.0)

        repeat(6) {
            reused.clear()
            fillTone(reused)
            reused.processEffects()
        }

        reused.clear()
        reused.tryDeactivate()
        reused.isActive shouldBe false

        // Life 2 opens with a PHASER-LESS stretch before a phaser voice engages. Review round 3:
        // the first clean-slate fix zeroed the phase but kept the dead owner's RATE, so this
        // stretch free-ran the sweep at 1.7 Hz and the engagement landed mid-sweep, offset by a
        // cleanup-schedule artifact. The interlude is what makes that observable.
        reused.updateFromVoice(VoiceTestHelpers.createSynthVoice(), blockStart = 20.0 * blockFrames)

        repeat(10) {
            reused.clear()
            fillTone(reused)
            reused.processEffects()
        }

        reused.updateFromVoice(phaserVoice(), blockStart = 23.0 * blockFrames)
        reused.clear()
        fillTone(reused)
        reused.processEffects()

        // Reference: a genuinely fresh orbit, same phaser-less prelude, same voice, same tone.
        val fresh = createOrbit()
        fresh.updateFromVoice(VoiceTestHelpers.createSynthVoice(), blockStart = 0.0)

        repeat(10) {
            fresh.clear()
            fillTone(fresh)
            fresh.processEffects()
        }

        fresh.updateFromVoice(phaserVoice(), blockStart = 3.0 * blockFrames)
        fresh.clear()
        fillTone(fresh)
        fresh.processEffects()

        var m = 0.0
        for (i in 0 until blockFrames) {
            m = maxOf(
                m,
                abs(reused.mixBuffer.left[i] - fresh.mixBuffer.left[i]),
                abs(reused.mixBuffer.right[i] - fresh.mixBuffer.right[i]),
            )
        }
        m shouldBe 0.0
    }

    "a non-finite depth cannot desync the two phaser gates" {
        val cylinder = createOrbit()

        cylinder.updateFromVoice(
            VoiceTestHelpers.createSynthVoice(
                phaser = Voice.Phaser(rate = 2.0, depth = 0.8, center = 1200.0, sweep = 900.0),
            ),
            blockStart = 0.0,
        )

        // New owner with a NaN depth: the setter rejects the write (stored depth stays 0.8), and
        // the kernel gate must follow the STORED value so both gates agree — the new owner's
        // kernel params land (review round 2; gating on the raw NaN left the previous owner's
        // ENTIRE phaser in charge).
        cylinder.updateFromVoice(
            VoiceTestHelpers.createSynthVoice(
                phaser = Voice.Phaser(rate = 3.0, depth = Double.NaN, center = 800.0, sweep = 700.0),
            ),
            blockStart = 2.0 * blockFrames,
        )

        cylinder.phaser.phaser.depth shouldBe 0.8
        cylinder.phaser.phaser.rate shouldBe 3.0
        cylinder.phaser.phaser.center shouldBe 800.0
    }

    "a no-phaser owner keeps the sweep clock: kernel params retained, only depth drops" {
        val cylinder = createOrbit()

        cylinder.updateFromVoice(
            VoiceTestHelpers.createSynthVoice(
                phaser = Voice.Phaser(rate = 2.0, depth = 0.8, center = 1200.0, sweep = 900.0),
            ),
            blockStart = 0.0,
        )
        cylinder.phaser.phaser.rate shouldBe 2.0
        cylinder.phaser.phaser.depth shouldBe 0.8

        // Owner lapses; a plain voice takes over (VoiceFactory-default phaser: rate 0, depth 0).
        cylinder.updateFromVoice(
            VoiceTestHelpers.createSynthVoice(),
            blockStart = 2.0 * blockFrames,
        )

        // Depth is the new owner's, but the CLOCK params are retained (ledger D2, completed in
        // review round 1: writing rate 0 froze the LFO as surely as the old skipped prepareBlock).
        cylinder.phaser.phaser.depth shouldBe 0.0
        cylinder.phaser.phaser.rate shouldBe 2.0
        cylinder.phaser.phaser.center shouldBe 1200.0
    }

    "updateFromVoice configures delay parameters" {
        val cylinder = createOrbit()
        val voice = VoiceTestHelpers.createSynthVoice(
            delay = Voice.Delay(time = 0.5, feedback = 0.3, amount = 0.5),
        )
        cylinder.updateFromVoice(voice, blockStart = 0.0)

        cylinder.delay.delayLine.delayTimeSeconds shouldBe 0.5
        cylinder.delay.delayLine.feedback shouldBe 0.3
    }

    "updateFromVoice configures reverb parameters" {
        val cylinder = createOrbit()
        val voice = VoiceTestHelpers.createSynthVoice(
            reverb = Voice.Reverb(room = 0.5, roomSize = 0.7, roomFade = 0.3, roomLp = 5000.0, roomDim = 0.2),
        )
        cylinder.updateFromVoice(voice, blockStart = 0.0)

        cylinder.reverb.reverb.roomSize shouldBe 0.7
        cylinder.reverb.reverb.roomFade shouldBe 0.3
        cylinder.reverb.reverb.roomLp shouldBe 5000.0
        cylinder.reverb.reverb.roomDim shouldBe 0.2
    }

    "updateFromVoice configures phaser parameters" {
        val cylinder = createOrbit()
        val voice = VoiceTestHelpers.createSynthVoice(
            phaser = Voice.Phaser(rate = 2.0, depth = 0.5, center = 800.0, sweep = 600.0, floor = 0.25),
        )
        cylinder.updateFromVoice(voice, blockStart = 0.0)

        cylinder.phaser.phaser.rate shouldBe 2.0
        cylinder.phaser.phaser.depth shouldBe 0.5
        cylinder.phaser.phaser.center shouldBe 800.0
        cylinder.phaser.phaser.sweep shouldBe 600.0
        // C4.2: the floor must be FORWARDED (a dropped line falls back to additive 1.0
        // and phaserFloor() becomes a silent no-op on the bus path)
        cylinder.phaser.phaser.floor shouldBe 0.25
    }

    "updateFromVoice: an absent phaser floor arrives as the additive default 1.0" {
        val cylinder = createOrbit()
        val voice = VoiceTestHelpers.createSynthVoice(
            phaser = Voice.Phaser(rate = 2.0, depth = 0.5, center = 800.0, sweep = 600.0),
        )
        cylinder.updateFromVoice(voice, blockStart = 0.0)
        cylinder.phaser.phaser.floor shouldBe 1.0
    }

    "updateFromVoice configures ducking" {
        val cylinder = createOrbit()
        val voice = VoiceTestHelpers.createSynthVoice(
            ducking = Voice.Ducking(cylinderId = 2, attackSeconds = 0.05, depth = 0.8),
        )
        cylinder.updateFromVoice(voice, blockStart = 0.0)

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
        cylinder.updateFromVoice(voice, blockStart = 0.0)

        val c = cylinder.compressor.compressor!!
        c.thresholdDb shouldBe -15.0
        c.ratio shouldBe 3.0
    }

    "clear resets all buffers" {
        val cylinder = createOrbit()
        cylinder.updateFromVoice(VoiceTestHelpers.createSynthVoice(), blockStart = 0.0)

        cylinder.mixBuffer.left.fill(0.5)
        cylinder.delaySendBuffer.left.fill(0.3)
        cylinder.reverbSendBuffer.left.fill(0.2)

        cylinder.clear()

        cylinder.mixBuffer.left[0] shouldBe 0.0
        cylinder.delaySendBuffer.left[0] shouldBe 0.0
        cylinder.reverbSendBuffer.left[0] shouldBe 0.0
    }
})
