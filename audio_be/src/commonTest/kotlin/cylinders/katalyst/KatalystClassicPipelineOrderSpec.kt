/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.cylinders.Cylinder
import io.peekandpoke.klang.audio_be.voices.Voice
import io.peekandpoke.klang.audio_be.voices.VoiceTestHelpers
import io.peekandpoke.klang.audio_be.warehouse.ReverbUnits
import io.peekandpoke.klang.audio_be.warehouse.SizedBuffers
import io.peekandpoke.klang.audio_bridge.KatalystDsl
import io.peekandpoke.klang.audio_bridge.KatalystStageDsl

/**
 * [KatalystDsl.classic] claims to be "the chain every cylinder has always run". This is the only
 * test that can check that claim against the cylinder itself, which is why it lives in audio_be:
 * `audio_bridge` cannot see [Cylinder], so a spec over there can only compare the declaration with
 * a hand-typed list, and a hand-typed list is exactly what drifts.
 *
 * Two independent assertions, both with a real cylinder on one side:
 *
 *  1. **the declaration against the engine**, stage kind for effect class, in order, through the
 *     one hand-written mapping below. This is what catches a stage added, dropped or moved on
 *     either side.
 *  2. **the cylinder's list against the chain the builder builds from [KatalystDsl.classic]**. Since
 *     Katalyst step 2 the cylinder does not name its stages at all: it asks
 *     [KatalystChainBuilder] for the classic chain and runs what comes back. A cylinder built from
 *     any other declaration fails here.
 *
 * Deliberately NOT asserted: knob values. Those are `KatalystDefaultsSyncSpec`'s job, and the
 * cylinder still gets them from the owner voice (step 3 moves them onto the chain's slots).
 */
class KatalystClassicPipelineOrderSpec : StringSpec({

    /** The one mapping from a declared stage to the effect the cylinder runs for it. */
    fun effectClassOf(stage: KatalystStageDsl): String = when (stage) {
        is KatalystStageDsl.Body -> "KatalystBodyEffect"
        is KatalystStageDsl.Vowel -> "KatalystFormantEffect"
        is KatalystStageDsl.Delay -> "KatalystDelayEffect"
        is KatalystStageDsl.Reverb -> "KatalystReverbEffect"
        is KatalystStageDsl.Phaser -> "KatalystPhaserEffect"
        is KatalystStageDsl.Compressor -> "KatalystCompressorEffect"
        is KatalystStageDsl.Duck -> "KatalystDuckEffect"
        // No DSP of their own yet: both hold their position and pass the buffer through untouched
        // until step 4 gives them `KatalystEqEffect` / `KatalystGainEffect`. The classic chain must
        // not contain them (asserted below by the exact match).
        is KatalystStageDsl.Eq -> "KatalystPassThroughStage"
        is KatalystStageDsl.Gain -> "KatalystPassThroughStage"
    }

    "the classic chain IS the cylinder's pipeline, stage for effect, in order, plus the duck" {
        val cylinder = Cylinder(id = 0, blockFrames = 128, sampleRate = 48000)

        // What the engine runs: the serial pipeline, then the ducking pass `Cylinders` does after
        // every orbit has been processed.
        val engineOrder = cylinder.pipeline.map { it::class.simpleName } +
            cylinder.duck.shouldNotBeNull()::class.simpleName

        KatalystDsl.classic.stages.map { effectClassOf(it) } shouldBe engineOrder
    }

    "the cylinder runs the chain the builder builds from the classic declaration, nothing else" {
        val cylinder = Cylinder(id = 0, blockFrames = 128, sampleRate = 48000)

        val declared = KatalystChainBuilder.build(
            dsl = KatalystDsl.classic,
            sampleRate = 48000,
            blockFrames = 128,
            rings = SizedBuffers.forRings(48000),
            reverbs = ReverbUnits(48000),
            voiceDriven = true,
        )

        cylinder.pipeline.map { it::class.simpleName } shouldBe declared.pipeline.map { it::class.simpleName }
        cylinder.duck.shouldNotBeNull()::class.simpleName shouldBe
            declared.duck.shouldNotBeNull()::class.simpleName
    }

    "the cylinder builds its chain with ITS OWN sample rate, not a literal" {
        // 22050 is neither the engine default nor the number any other spec here uses, so a
        // hardcoded rate in the builder call shows up immediately.
        val cylinder = Cylinder(id = 0, blockFrames = 128, sampleRate = 22050)

        cylinder.updateFromVoice(
            VoiceTestHelpers.createSynthVoice(delay = Voice.Delay(amount = 0.5, time = 0.25, feedback = 0.3)),
            blockStart = 0.0,
        )

        // The ring the stage rents is sized and clocked by the rate the stage was built with: at
        // 22050 a 0.25 s echo is half the samples it is at 44100, and the line says so itself.
        cylinder.delay!!.delayLine.shouldNotBeNull().sampleRate shouldBe 22050
    }

    "the cylinder builds its chain with ITS OWN block size, so a drain fills the whole block" {
        // The stages size their silent-drain buffers from the block frames they were BUILT with,
        // and each clamps its drain to `min(ctx.blockFrames, its own buffer)` so a mismatch cannot
        // read out of bounds. That clamp is exactly why a literal in the builder call is INAUDIBLE
        // at the door and audible in the output: a stage built for 128 frames inside a 256-frame
        // cylinder drains only the first half of every block and leaves the second half silent.
        // 256 is double the usual 128 on purpose.
        val bigBlock = 256
        val cylinder = Cylinder(id = 0, blockFrames = bigBlock, sampleRate = 44100)

        // Owner A charges a small room.
        cylinder.updateFromVoice(
            VoiceTestHelpers.createSynthVoice(reverb = Voice.Reverb(amount = 0.8, size = 0.05)),
            blockStart = 0.0,
        )
        repeat(20) {
            cylinder.reverbSendBuffer.left.fill(0.5)
            cylinder.reverbSendBuffer.right.fill(0.5)
            cylinder.mixBuffer.clear()
            cylinder.processEffects()
        }

        // Owner B has no room: the off-config starts the drain, which runs on SILENT input from
        // the stage's own buffer.
        cylinder.updateFromVoice(
            VoiceTestHelpers.createSynthVoice(reverb = Voice.Reverb(amount = 0.0, size = 0.0)),
            blockStart = 2.0 * bigBlock,
        )
        cylinder.reverb!!.hasTail() shouldBe true

        cylinder.clear()
        cylinder.processEffects()

        val secondHalf = bigBlock / 2 until bigBlock

        withClue("the drain must reach the second half of the block, not just the first 128 frames") {
            secondHalf.any { cylinder.mixBuffer.left[it] != 0.0 } shouldBe true
            secondHalf.any { cylinder.mixBuffer.right[it] != 0.0 } shouldBe true
        }
    }

    "the same for the delay stage: its drain covers the cylinder's whole block, not 128 frames" {
        // The reverb row above pins the builder's Reverb branch. The Delay branch passes
        // blockFrames of its own, and its drain clamps the same way, so it needs its own guard:
        // a stage built for 128 frames echoes into the first half of a 256-frame block only.
        val bigBlock = 256
        val cylinder = Cylinder(id = 0, blockFrames = bigBlock, sampleRate = 44100)

        // Owner A charges the ring with a delay short enough that echoes land inside one block.
        cylinder.updateFromVoice(
            VoiceTestHelpers.createSynthVoice(delay = Voice.Delay(amount = 0.8, time = 0.01, feedback = 0.8)),
            blockStart = 0.0,
        )
        repeat(20) {
            cylinder.delaySendBuffer.left.fill(0.5)
            cylinder.delaySendBuffer.right.fill(0.5)
            cylinder.mixBuffer.clear()
            cylinder.processEffects()
        }

        // Owner B has no delay: the off-config starts the drain, which runs on SILENT input from
        // the stage's own buffer.
        cylinder.updateFromVoice(
            VoiceTestHelpers.createSynthVoice(),
            blockStart = 2.0 * bigBlock,
        )
        cylinder.delay!!.hasTail() shouldBe true

        cylinder.clear()
        cylinder.processEffects()

        val secondHalf = bigBlock / 2 until bigBlock

        withClue("the delay drain must reach the second half of the block too") {
            secondHalf.any { cylinder.mixBuffer.left[it] != 0.0 } shouldBe true
            secondHalf.any { cylinder.mixBuffer.right[it] != 0.0 } shouldBe true
        }
    }

    "the duck is declared last, and the cylinder runs it outside the serial list" {
        val cylinder = Cylinder(id = 0, blockFrames = 128, sampleRate = 48000)

        // The position is documented as ignored, so "last" is a reading convention, not a
        // promise about signal order. What IS load-bearing: the duck must not be in the serial
        // pipeline, or it would run per orbit before the sidechain source exists.
        KatalystDsl.classic.stages.last() shouldBe KatalystDsl.classic.stages
            .filterIsInstance<KatalystStageDsl.Duck>().single()

        cylinder.pipeline.any { it is KatalystDuckEffect } shouldBe false
    }
})
