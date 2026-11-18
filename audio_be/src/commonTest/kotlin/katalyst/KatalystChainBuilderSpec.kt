/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.katalyst

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.warehouse.ReverbUnits
import io.peekandpoke.klang.audio_be.warehouse.SizedBuffers
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.Kat
import io.peekandpoke.klang.audio_bridge.KatalystDsl
import io.peekandpoke.klang.audio_bridge.KatalystKnobDsl
import io.peekandpoke.klang.audio_bridge.KatalystStageDsl
import io.peekandpoke.klang.audio_bridge.constants.DUCK_DEPTH
import io.peekandpoke.klang.audio_bridge.constants.DUCK_RELEASE_SECONDS
import io.peekandpoke.klang.audio_bridge.constants.SLOT_UNSET
import io.peekandpoke.klang.audio_bridge.kat
import io.peekandpoke.klang.common.infra.klangMapOf
import io.peekandpoke.klang.common.infra.toKlangMap

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

        chain.pipeline.map { it.stageName() } shouldBe listOf(
            "KatalystResonatorEffect(BODY)",
            "KatalystResonatorEffect(VOWEL)",
            "KatalystDelayEffect",
            "KatalystReverbEffect",
            "KatalystPhaserEffect",
            "KatalystCompressorEffect",
            // The group fader, last in the serial list since 2026-09-19. Unity there, so it is
            // bit-transparent; what it buys is `katp("gain.gain", x)` on any cylinder.
            "KatalystGainEffect",
        )

        // The duck is built, and it is NOT in the serial list: `Cylinders` runs it after every
        // cylinder, because it needs the sidechain source.
        chain.duck.shouldNotBeNull()
        chain.pipeline.any { it is KatalystDuckEffect } shouldBe false
    }

    "the spec helpers find the very instances in the pipeline, at their declared positions" {
        val chain = build(KatalystDsl.classic)

        chain.body.shouldNotBeNull() shouldBeSameInstanceAs chain.pipeline[0]
        chain.vowel.shouldNotBeNull() shouldBeSameInstanceAs chain.pipeline[1]
        chain.delay.shouldNotBeNull() shouldBeSameInstanceAs chain.pipeline[2]
        chain.reverb.shouldNotBeNull() shouldBeSameInstanceAs chain.pipeline[3]
        chain.phaser.shouldNotBeNull() shouldBeSameInstanceAs chain.pipeline[4]
        chain.compressor.shouldNotBeNull() shouldBeSameInstanceAs chain.pipeline[5]
    }

    "a chain declares a tail exactly when it declares a reverb, a delay, a distort, a parallel with one or a region (the list of KatalystChain.declaresTail)" {
        // The master's "may a silent output still hold a tail" test reads this (`MasterBus.isRinging`); it moved from
        // the master onto the chain in engine tidy-up step 13 (A2.1).
        val wet = KatalystKnobDsl.Constant(0.4)

        build(KatalystDsl.classic).declaresTail shouldBe true
        build(KatalystDsl(emptyList())).declaresTail shouldBe false
        build(KatalystDsl.of(KatalystStageDsl.Reverb(wet = wet))).declaresTail shouldBe true
        build(KatalystDsl.of(KatalystStageDsl.Delay(wet = wet))).declaresTail shouldBe true
        build(KatalystDsl.of(KatalystStageDsl.Gain(KatalystKnobDsl.Constant(0.8)))).declaresTail shouldBe false
        build(KatalystDsl.of(KatalystStageDsl.Compressor())).declaresTail shouldBe false
        build(KatalystDsl.of(KatalystStageDsl.Phaser())).declaresTail shouldBe false
        // The distort stage's DC blocker decays after its input stops (review round 2 of the distort stage).
        build(KatalystDsl.of(KatalystStageDsl.Distort())).declaresTail shouldBe true
        // The shape stage's too (KLANG-00467): the same core at unity drive.
        build(KatalystDsl.of(KatalystStageDsl.Shape())).declaresTail shouldBe true

        // Every other stage the classic chain declares (body, vowel, phaser, compressor, gain, duck), none a tail.
        val noTail = KatalystDsl.classic.stages.filter { it !is KatalystStageDsl.Reverb && it !is KatalystStageDsl.Delay }
        build(KatalystDsl(noTail)).declaresTail shouldBe false
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
            cylinder = KatalystKnobDsl.Param("duck.cylinder", SLOT_UNSET),
            depth = KatalystKnobDsl.Param("duck.depth", DUCK_DEPTH),
            release = KatalystKnobDsl.Param("duck.release", DUCK_RELEASE_SECONDS),
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

        val params = mapOf("duck.cylinder" to 3.0, "duck.depth" to 0.7, "duck.release" to 0.02)

        chain.applyParams(params.toKlangMap())

        // The surviving writer is bound to the instance the chain exposes and processes.
        duck.duckCylinderId shouldBe 3
        val ducking = duck.ducking.shouldNotBeNull()
        ducking.depth shouldBe 0.7

        // And a second block reuses that one DSP instance instead of building another, which is
        // what keeps the duck's envelope follower alive across notes.
        chain.applyParams(params.toKlangMap())

        duck.ducking.shouldNotBeNull() shouldBeSameInstanceAs ducking
    }

    "the chain gives its duck the cylinder's per-block tick, so a late switch-on fades in" {
        // The duck runs in a LATER pass than the chain and not at all while it is off, so its own
        // `process` cannot close the "first initialisation is instant" window. `KatalystChain
        // .process` is the cylinder's per-block event and the one caller of `cylinderBlockRan`
        // (Katalyst 5c-9). Without it the duck would SNAP to full reduction whenever it first
        // engages, however long the cylinder has been sounding.
        val chain = build(
            KatalystDsl.of(
                KatalystStageDsl.Duck(
                    cylinder = KatalystKnobDsl.Param("duck.cylinder", SLOT_UNSET),
                    depth = KatalystKnobDsl.Param("duck.depth", DUCK_DEPTH),
                    release = KatalystKnobDsl.Param("duck.release", DUCK_RELEASE_SECONDS),
                )
            )
        )

        val duck = chain.duck.shouldNotBeNull()
        val ctx = KatalystContext(blockFrames = blockFrames, mixBuffer = StereoBuffer(blockFrames))

        // Four blocks of a sounding cylinder whose duck names no source cylinder: off, but alive.
        repeat(4) {
            chain.applyParams(klangMapOf("duck.depth" to 0.0))
            chain.process(ctx)
        }

        // Now it engages. The weight has to start from 0 and ride in.
        chain.applyParams(klangMapOf("duck.cylinder" to 3.0, "duck.depth" to 0.8))

        duck.weight shouldBe 0.0
    }

    "every stage variant the DSL has builds a stage, and every stage states its own lifecycle" {
        // The real guard for the lifecycle is the compiler: `reset` and `hasTail` are abstract on
        // [KatalystEffect], so a stage that forgets one does not compile. What a test can add is
        // that the builder still covers all ten variants, so no kind silently loses its stage.
        val all = KatalystDsl.of(
            KatalystStageDsl.Body(),
            KatalystStageDsl.Vowel(),
            KatalystStageDsl.Delay(),
            KatalystStageDsl.Reverb(),
            KatalystStageDsl.Phaser(),
            KatalystStageDsl.Compressor(),
            KatalystStageDsl.Eq(),
            KatalystStageDsl.Gain(),
            KatalystStageDsl.Shape(),
            KatalystStageDsl.Duck(),
        )

        val chain = build(all)

        // Nine in the serial list (the duck runs outside it), one stage per declared variant.
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

    // ── The chain's filter doors and its shape stage (KLANG-00467, cylinder amp step 1) ───────────────────────────

    /** A deterministic stereo fill for [blocks] blocks, the two sides different. */
    fun noiseInput(blocks: Int): Pair<DoubleArray, DoubleArray> {
        var x = 0x5EED

        fun next(): Double {
            x = x * 1103515245 + 12345

            return ((x ushr 8) and 0xFFFF) / 65535.0 - 0.5
        }

        return DoubleArray(blocks * blockFrames) { next() } to DoubleArray(blocks * blockFrames) { next() }
    }

    /** Renders [chain] over [input], configured from authored values, both channels. */
    fun render(chain: KatalystChain, input: Pair<DoubleArray, DoubleArray>): Pair<DoubleArray, DoubleArray> {
        val ctx = ctx()
        val frames = input.first.size
        val outL = DoubleArray(frames)
        val outR = DoubleArray(frames)

        for (b in 0 until frames / blockFrames) {
            val from = b * blockFrames

            val to = from + blockFrames

            input.first.copyInto(destination = ctx.mixBuffer.left, destinationOffset = 0, startIndex = from, endIndex = to)
            input.second.copyInto(destination = ctx.mixBuffer.right, destinationOffset = 0, startIndex = from, endIndex = to)
            chain.applyParams(null)
            chain.process(ctx)
            ctx.mixBuffer.left.copyInto(destination = outL, destinationOffset = from, startIndex = 0, endIndex = blockFrames)
            ctx.mixBuffer.right.copyInto(destination = outR, destinationOffset = from, startIndex = 0, endIndex = blockFrames)
        }

        return outL to outR
    }

    "a chain holding a shape stage builds the shape effect, with no writer, and renders the curve" {
        val chain = build(KatalystDsl.of(KatalystStageDsl.Shape(shape = 10)))

        chain.pipeline.map { it::class.simpleName } shouldBe listOf("KatalystShapeEffect")
        // Never off and no knob: nothing to configure.
        chain.writerCount shouldBe 0

        val input = noiseInput(blocks = 8)
        val (left, right) = render(chain, input)

        // A bare core of the same shape and pole renders the same samples, so the arm handed the shape over.
        val alone = KatalystShapeEffect(sampleRate = sampleRate, shapeIndex = 10)
        val (aloneL, aloneR) = render(
            KatalystChain(serial = arrayOf(alone), writers = emptyArray(), duck = null, duckWriter = null, firstStage = 0),
            input,
        )

        left.toList() shouldBe aloneL.toList()
        right.toList() shouldBe aloneR.toList()
        (left.indices.any { left[it] != input.first[it] }) shouldBe true
    }

    "the four filter doors render the same samples as the same sections written into one eq stage by hand" {
        val doors = Kat {
            it.highpass(freq = 90.kat, q = 0.8.kat) { f -> f.passes(2.0) }
                .bandpass(freq = 700.kat, q = 1.5.kat)
                .notch(freq = 3100.kat, q = 4.kat)
                .lowpass(freq = 5000.kat, q = 1.1.kat) { f -> f.passes(3.0) }
        }

        fun c(value: Double) = IgnitorDsl.Constant(value)

        // The seven sections written out, the cascades' q on the Butterworth ladder as literals: 0.8 times
        // sqrt(2) / (2 cos(pi / 8)) and (3 pi / 8); 1.1 times the three factors of N = 3, the middle exactly 1.
        val byHand = KatalystDsl.of(
            KatalystStageDsl.Eq(
                sections = listOf(
                    IgnitorDsl.EqSection.Highpass(freq = c(90.0), q = c(0.6122934917841437)),
                    IgnitorDsl.EqSection.Highpass(freq = c(90.0), q = c(1.4782072520180587)),
                    IgnitorDsl.EqSection.Bandpass(freq = c(700.0), q = c(1.5)),
                    IgnitorDsl.EqSection.Notch(freq = c(3100.0), q = c(4.0)),
                    IgnitorDsl.EqSection.Lowpass(freq = c(5000.0), q = c(0.8052558883257651)),
                    IgnitorDsl.EqSection.Lowpass(freq = c(5000.0), q = c(1.1)),
                    IgnitorDsl.EqSection.Lowpass(freq = c(5000.0), q = c(3.005255888325766)),
                ),
            ),
        )

        doors.stages.size shouldBe 4

        val input = noiseInput(blocks = 16)
        val (doorsL, doorsR) = render(build(doors), input)
        val (handL, handR) = render(build(byHand), input)

        // A tolerance for the literals' last bit (the door computes q times the factor); a wrong section is far off.
        for (k in doorsL.indices) {
            withClue("frame $k") {
                doorsL[k] shouldBe (handL[k] plusOrMinus 1e-12)
                doorsR[k] shouldBe (handR[k] plusOrMinus 1e-12)
            }
        }

        // ...and the filters do something: the output is not the input.
        (doorsL.indices.any { doorsL[it] != input.first[it] }) shouldBe true
    }

    "a shape stage inside oversampleBy(4) runs at the region's rate with its DC pole mapped (firstStage = 2)" {
        val chain = build(Kat { it.oversampleBy(4) { r -> r.shape("tube") } })
        val input = noiseInput(blocks = 16)
        val (left, right) = render(chain, input)

        // The same region built by hand around a bare shape effect at four times the rate, its pole mapped by 2 steps.
        val body = KatalystChain(
            serial = arrayOf(KatalystShapeEffect(sampleRate = sampleRate * 4, shapeIndex = 10, firstStage = 2)),
            writers = emptyArray(),
            duck = null,
            duckWriter = null,
            firstStage = 2,
        )
        val region = KatalystRegionEffect(body = body, steps = 2, firstStage = 0, blockFrames = blockFrames)
        val byHand = KatalystChain(serial = arrayOf(region), writers = emptyArray(), duck = null, duckWriter = null, firstStage = 0)
        val (handL, handR) = render(byHand, input)

        for (k in left.indices) {
            withClue("frame $k") {
                left[k] shouldBe handL[k]
                right[k] shouldBe handR[k]
            }
        }
    }
})
