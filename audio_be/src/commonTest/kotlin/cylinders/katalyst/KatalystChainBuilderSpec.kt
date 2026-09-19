/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.warehouse.ReverbUnits
import io.peekandpoke.klang.audio_be.warehouse.SizedBuffers
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.KatalystDsl
import io.peekandpoke.klang.audio_bridge.KatalystStageDsl
import io.peekandpoke.klang.audio_bridge.constants.DUCK_ATTACK_SECONDS
import io.peekandpoke.klang.audio_bridge.constants.DUCK_DEPTH
import io.peekandpoke.klang.audio_bridge.constants.SLOT_UNSET

/**
 * [KatalystChainBuilder] is the one mapping from a declared stage to the effect the engine runs
 * for it. What this spec pins:
 *
 *  - the classic chain builds the historical effects plus its unity group fader, in the DSL's
 *    order, the duck outside the serial list;
 *  - nothing that is lazy today becomes eager at build (no ring, no reverb network);
 *  - a chain declaring two ducks runs ONE, and the writer that configures it is bound to THAT
 *    instance and not to the one the "last wins" rule dropped;
 *  - every declared stage gets exactly one writer, and every writer is a slot writer (step 5b-1),
 *    so the classic chain a cylinder is born with is configured from `katalystParams` like any
 *    other.
 *
 * The cylinder's side of the mapping is [KatalystClassicPipelineOrderSpec]'s job.
 */
class KatalystChainBuilderSpec : StringSpec({

    val sampleRate = 44100
    val blockFrames = 128

    // The shelves are on the builder's door, not behind a default, so a spec says which ones it
    // rents from, exactly as the cylinder does.
    fun build(dsl: KatalystDsl) = KatalystChainBuilder.build(
        dsl = dsl,
        sampleRate = sampleRate,
        blockFrames = blockFrames,
        rings = SizedBuffers.forRings(sampleRate),
        reverbs = ReverbUnits(sampleRate),
    )

    fun ctx() = KatalystContext(
        blockFrames = blockFrames,
        mixBuffer = StereoBuffer(blockFrames),
    )

    // ── The classic chain ────────────────────────────────────────────────────────────────────────

    "the classic chain builds the historical effects and the fader, in DSL order, the duck outside the list" {
        val chain = build(KatalystDsl.classic)

        chain.pipeline.map { it::class.simpleName } shouldBe listOf(
            "KatalystBodyEffect",
            "KatalystFormantEffect",
            "KatalystDelayEffect",
            "KatalystReverbEffect",
            "KatalystPhaserEffect",
            "KatalystCompressorEffect",
            // The group fader, last in the serial list since 2026-09-19. Unity there, so it is
            // bit-transparent; what it buys is `katp("gain.gain", x)` on any orbit.
            "KatalystGainEffect",
        )

        // The duck is built, and it is NOT in the serial list: `Cylinders` runs it after every
        // orbit, because it needs the sidechain source.
        chain.duck.shouldNotBeNull()
        chain.pipeline.any { it is KatalystDuckEffect } shouldBe false
    }

    "the typed accessors are the very instances in the pipeline, at their declared positions" {
        val chain = build(KatalystDsl.classic)

        chain.body.shouldNotBeNull() shouldBeSameInstanceAs chain.pipeline[0]
        chain.vowel.shouldNotBeNull() shouldBeSameInstanceAs chain.pipeline[1]
        chain.delay.shouldNotBeNull() shouldBeSameInstanceAs chain.pipeline[2]
        chain.reverb.shouldNotBeNull() shouldBeSameInstanceAs chain.pipeline[3]
        chain.phaser.shouldNotBeNull() shouldBeSameInstanceAs chain.pipeline[4]
        chain.compressor.shouldNotBeNull() shouldBeSameInstanceAs chain.pipeline[5]
    }

    "building rents nothing: the delay has no ring and the reverb no network until an owner asks" {
        val chain = build(KatalystDsl.classic)

        // The warehouse rule (2b / 2d): a cylinder that never delays or rooms holds neither the
        // 7.68 MB ring nor the ~200 KB network. Building a chain must not change that.
        chain.delay.shouldNotBeNull().delayLine.shouldBeNull()
        chain.reverb.shouldNotBeNull().reverb.shouldBeNull()
        chain.deniedRents shouldBe 0
    }

    "an empty chain is an empty pipeline with no duck" {
        val chain = build(KatalystDsl(emptyList()))

        chain.pipeline.size shouldBe 0
        chain.duck.shouldBeNull()
        chain.hasTail() shouldBe false
    }

    // ── The duck: declared in the list, run outside it, last one wins ────────────────────────────

    "the classic chain installs one writer per declared stage" {
        // Eight stages, eight slot writers. The count is what the duplicate-duck row below
        // discriminates against, so it is pinned here on the chain everything else is measured
        // from.
        build(KatalystDsl.classic).writerCount shouldBe 8
    }

    "two declared ducks: ONE duck stage, ONE writer, and the dropped duplicate is never configured" {
        // Both ducks declare SLOTS, so the state below can configure the survivor; a bare
        // `Duck()` carries constants and would be deaf to it, which would make the row pass on a
        // duck nothing ever writes.
        fun duck() = KatalystStageDsl.Duck(
            orbit = IgnitorDsl.Param("duck.orbit", SLOT_UNSET),
            depth = IgnitorDsl.Param("duck.depth", DUCK_DEPTH),
            attack = IgnitorDsl.Param("duck.attack", DUCK_ATTACK_SECONDS),
        )

        val chain = build(
            KatalystDsl.of(
                duck(),
                KatalystStageDsl.Reverb(),
                duck(),
            )
        )

        chain.pipeline.map { it::class.simpleName } shouldBe listOf("KatalystReverbEffect")
        val duck = chain.duck.shouldNotBeNull()

        // Two writers, not three: the reverb's and exactly one duck's. A writer per DECLARED duck
        // would configure a stage nothing runs, which is the failure "last one wins" has to rule
        // out, and a count is the only way to tell that from a dropped instance.
        chain.writerCount shouldBe 2

        val params = mapOf("duck.orbit" to 3.0, "duck.depth" to 0.7, "duck.attack" to 0.02)

        chain.applyParams(params)

        // The surviving writer is bound to the instance the chain exposes and processes.
        duck.duckCylinderId shouldBe 3
        val ducking = duck.ducking.shouldNotBeNull()
        ducking.depth shouldBe 0.7

        // And a second block reuses that one DSP instance instead of building another, which is
        // what keeps the duck's envelope follower alive across notes.
        chain.applyParams(params)

        duck.ducking.shouldNotBeNull() shouldBeSameInstanceAs ducking
    }

    "every stage variant the DSL has builds a stage, and every stage states its own lifecycle" {
        // The real guard for the lifecycle is the compiler: `reset` and `hasTail` are abstract on
        // [KatalystEffect], so a stage that forgets one does not compile. What a test can add is
        // that the builder still covers all nine variants, so no kind silently loses its stage.
        val all = KatalystDsl.of(
            KatalystStageDsl.Body(),
            KatalystStageDsl.Vowel(),
            KatalystStageDsl.Delay(),
            KatalystStageDsl.Reverb(),
            KatalystStageDsl.Phaser(),
            KatalystStageDsl.Compressor(),
            KatalystStageDsl.Eq(),
            KatalystStageDsl.Gain(),
            KatalystStageDsl.Duck(),
        )

        val chain = build(all)

        // Eight in the serial list (the duck runs outside it), one stage per declared variant.
        chain.pipeline.size shouldBe all.stages.size - 1
        chain.duck.shouldNotBeNull()

        // Every stage answers the lifecycle without throwing, and a fresh chain has no tail.
        chain.hasTail() shouldBe false
        chain.reset()
        chain.retire()
        chain.hasTail() shouldBe false
    }

    // ── Eq and Gain: the two stages with no voice field ──────────────────────────────────────────

    "eq and gain build their own stage each, at their declared position" {
        val chain = build(
            KatalystDsl.of(
                KatalystStageDsl.Eq(),
                KatalystStageDsl.Gain(),
                KatalystStageDsl.Compressor(),
            )
        )

        chain.pipeline.map { it::class.simpleName } shouldBe listOf(
            "KatalystEqEffect",
            "KatalystGainEffect",
            "KatalystCompressorEffect",
        )
    }

    "a classic block plus an eq is nine stages and nine writers" {
        // The classic eight plus the ONE writer of the declared `eq`: one writer per declared
        // stage, whatever the kind, which is the rule the duplicate duck is the only exception to.
        val chain = build(KatalystDsl.of(*KatalystDsl.classic.stages.toTypedArray(), KatalystStageDsl.Eq()))

        chain.writerCount shouldBe 9
    }

    "a bare eq and a unity gain leave every sample of the mix exactly as they found it" {
        // `Eq()` declares no section (a transparent stage by its wire KDoc) and `Gain()` is unity,
        // which the stage skips entirely. Both together must not touch one sample.
        val chain = build(KatalystDsl.of(KatalystStageDsl.Eq(), KatalystStageDsl.Gain()))
        val ctx = ctx()

        // Configured, not merely unconfigured: an EQ bank IS installed (over no sections) and the
        // fader IS set (to unity), so this row is about what the two stages DO, not about a chain
        // that never ran its writers.
        chain.applyParams(null)

        // A deterministic pseudo-random fill: silence or DC would pass a stage that zeroes or
        // scales, and a ramp would pass one that reverses the buffer.
        var x = 0x5EED
        fun next(): Double {
            x = x * 1103515245 + 12345
            return ((x ushr 8) and 0xFFFF) / 65535.0 - 0.5
        }

        for (i in 0 until blockFrames) {
            ctx.mixBuffer.left[i] = next()
            ctx.mixBuffer.right[i] = next()
        }

        val mixL = ctx.mixBuffer.left.copyOf()
        val mixR = ctx.mixBuffer.right.copyOf()

        chain.process(ctx)

        for (i in 0 until blockFrames) {
            withClue("frame $i") {
                ctx.mixBuffer.left[i] shouldBe mixL[i]
                ctx.mixBuffer.right[i] shouldBe mixR[i]
            }
        }
    }
})
