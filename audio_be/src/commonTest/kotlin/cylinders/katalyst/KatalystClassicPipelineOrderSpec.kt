/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.cylinders.Cylinder
import io.peekandpoke.klang.audio_bridge.KatalystDsl
import io.peekandpoke.klang.audio_bridge.KatalystStageDsl

/**
 * [KatalystDsl.classic] claims to be "the chain every cylinder has always run". This is the only
 * test that can check that claim against the cylinder itself, which is why it lives in audio_be:
 * `audio_bridge` cannot see [Cylinder], so a spec over there can only compare the declaration with
 * a hand-typed list, and a hand-typed list is exactly what drifts.
 *
 * The comparison is stage kind against effect class, in order, for the whole serial pipeline, plus
 * the duck the cylinder deliberately runs OUTSIDE that list (`Cylinders` applies it after every
 * orbit, because it needs the sidechain source). A stage added, dropped or moved on either side
 * fails here.
 *
 * Deliberately NOT asserted: knob values. Those are `KatalystDefaultsSyncSpec`'s job, and the
 * cylinder gets them from the owner voice today, not from the chain (step 1 registers the chain
 * and ignores it).
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
        is KatalystStageDsl.Duck -> "KatalystDuckingEffect"
        // Neither exists on the cylinder yet; they arrive with the chain in step 2, and the
        // classic chain must not contain them before then (asserted below by the exact match).
        is KatalystStageDsl.Eq -> "KatalystEqEffect"
        is KatalystStageDsl.Gain -> "KatalystGainEffect"
    }

    "the classic chain IS the cylinder's pipeline, stage for effect, in order, plus the duck" {
        val cylinder = Cylinder(id = 0, blockFrames = 128, sampleRate = 48000)

        // What the engine runs: the serial pipeline, then the ducking pass `Cylinders` does after
        // every orbit has been processed.
        val engineOrder = cylinder.pipeline.map { it::class.simpleName } + cylinder.ducking::class.simpleName

        KatalystDsl.classic.stages.map { effectClassOf(it) } shouldBe engineOrder
    }

    "the duck is declared last, and the cylinder runs it outside the serial list" {
        val cylinder = Cylinder(id = 0, blockFrames = 128, sampleRate = 48000)

        // The position is documented as ignored, so "last" is a reading convention, not a
        // promise about signal order. What IS load-bearing: the duck must not be in the serial
        // pipeline, or it would run per orbit before the sidechain source exists.
        KatalystDsl.classic.stages.last() shouldBe KatalystDsl.classic.stages
            .filterIsInstance<KatalystStageDsl.Duck>().single()

        cylinder.pipeline.any { it is KatalystDuckingEffect } shouldBe false
    }
})
