/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.Oversampler
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.ignitor.ScratchBuffers
import io.peekandpoke.klang.audio_be.roundTrip
import io.peekandpoke.klang.audio_be.warehouse.ReverbUnits
import io.peekandpoke.klang.audio_be.warehouse.SizedBuffers
import io.peekandpoke.klang.audio_bridge.DistortionShapes
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.KatalystDsl
import io.peekandpoke.klang.audio_bridge.KatalystStageDsl
import io.peekandpoke.klang.audio_bridge.constants.AUTHORED_LIMITER_ATTACK_SECONDS
import io.peekandpoke.klang.audio_bridge.constants.LIMITER_KNEE_DB
import io.peekandpoke.klang.audio_bridge.constants.LIMITER_RATIO
import io.peekandpoke.klang.audio_bridge.constants.LIMITER_RELEASE_SECONDS
import io.peekandpoke.klang.audio_bridge.constants.LIMITER_THRESHOLD_DB
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The Katalyst `parallel` stage ([KatalystParallelEffect], `docs/tasks-archive/2026-10/20261010-parallel-serial-bands.md` step 3):
 * branches side by side, summed, matched in phase (oversamplers by phase twins, pure delays by rings), and every
 * lifecycle question passed to the branches.
 *
 * **The oracles are the input's own definition**: a gain branch is the input times its factor, an empty branch is the
 * input, a limiter with a lookahead that compresses nothing is the input [delay] frames late
 * (`KatalystCompressorLookaheadSpec` pins that), and a distort stage at amount 0 is the input through an unshaped round
 * trip of its oversampler (`KatalystDistortEffectSpec` pins that), written here with bare [Oversampler]s; so every
 * expected sample is written from the input, never from the stage under test.
 */
class KatalystParallelEffectSpec : StringSpec({

    val sampleRate = 44100
    val blockFrames = 128

    /** 5 ms at 44.1 kHz: 220 frames, not a multiple of the block, so a pad ring straddles blocks. */
    val lookahead = 0.005
    val delay = (lookahead * sampleRate).toInt()

    fun c(value: Double) = IgnitorDsl.Constant(value)

    /** A limiter that never reaches its ceiling on the input below: a pure delay of [delay] frames. */
    val late = KatalystStageDsl.Compressor(
        threshold = c(LIMITER_THRESHOLD_DB),
        ratio = c(LIMITER_RATIO),
        knee = c(LIMITER_KNEE_DB),
        attack = c(AUTHORED_LIMITER_ATTACK_SECONDS),
        release = c(LIMITER_RELEASE_SECONDS),
        lookahead = lookahead,
    )

    val empty = KatalystDsl(emptyList())

    fun branch(vararg stages: KatalystStageDsl) = KatalystDsl.of(*stages)

    fun parallel(vararg branches: KatalystDsl) = KatalystStageDsl.Parallel(branches = branches.toList())

    fun chain(vararg stages: KatalystStageDsl, params: Map<String, Double>? = null): KatalystChain = KatalystChainBuilder.build(
        dsl = KatalystDsl.of(*stages),
        sampleRate = sampleRate,
        blockFrames = blockFrames,
        rings = SizedBuffers.forRings(sampleRate),
        reverbs = ReverbUnits(sampleRate),
    ).also { it.applyParams(params) }

    /** Well below the limiter's ceiling, different on the two channels so a swapped channel shows. */
    fun left(k: Int): Double =
        if (k < 0) 0.0 else 0.4 * sin(2.0 * PI * 110.0 * k / sampleRate) + 0.15 * sin(2.0 * PI * 2750.0 * k / sampleRate + 0.3)

    fun right(k: Int): Double = if (k < 0) 0.0 else 0.35 * sin(2.0 * PI * 165.0 * k / sampleRate + 1.0)

    class Take(val l: DoubleArray, val r: DoubleArray)

    /** Runs [chain] over [blocks] blocks of the input, from frame [from] of it. */
    fun run(
        chain: KatalystChain,
        blocks: Int,
        from: Int = 0,
        left: (Int) -> Double = ::left,
        right: (Int) -> Double = ::right,
    ): Take {
        val ctx = KatalystContext(blockFrames = blockFrames, mixBuffer = StereoBuffer(blockFrames))
        val l = DoubleArray(blocks * blockFrames)
        val r = DoubleArray(blocks * blockFrames)

        for (b in 0 until blocks) {
            for (i in 0 until blockFrames) {
                ctx.mixBuffer.left[i] = left(from + b * blockFrames + i)
                ctx.mixBuffer.right[i] = right(from + b * blockFrames + i)
            }

            chain.process(ctx)
            ctx.mixBuffer.left.copyInto(destination = l, destinationOffset = b * blockFrames, startIndex = 0, endIndex = blockFrames)
            ctx.mixBuffer.right.copyInto(destination = r, destinationOffset = b * blockFrames, startIndex = 0, endIndex = blockFrames)
        }

        return Take(l = l, r = r)
    }

    fun Take.shouldBe(expectLeft: (Int) -> Double, expectRight: (Int) -> Double) {
        for (k in l.indices) {
            withClue("sample $k") {
                l[k] shouldBe (expectLeft(k) plusOrMinus 1e-12)
                r[k] shouldBe (expectRight(k) plusOrMinus 1e-12)
            }
        }
    }

    // ── The sum ─────────────────────────────────────────────────────────────────────────────────

    "the stage is the SUM of its branches: gain 0.5 and gain 0.25 side by side are 0.75 of the bus" {
        val out = run(chain(parallel(branch(KatalystStageDsl.Gain(c(0.5))), branch(KatalystStageDsl.Gain(c(0.25))))), blocks = 4)

        out.shouldBe({ 0.75 * left(it) }, { 0.75 * right(it) })
    }

    "an empty branch is the dry bus: two of them are twice the bus, three branches sum all three" {
        run(chain(parallel(empty, empty)), blocks = 3).shouldBe({ 2.0 * left(it) }, { 2.0 * right(it) })

        run(chain(parallel(empty, branch(KatalystStageDsl.Gain(c(-1.0))), branch(KatalystStageDsl.Gain(c(0.5))))), blocks = 3)
            .shouldBe({ 0.5 * left(it) }, { 0.5 * right(it) })
    }

    "a stage after the parallel processes the sum, a stage before it feeds every branch" {
        val out = run(
            chain(
                KatalystStageDsl.Gain(c(2.0)),
                parallel(empty, branch(KatalystStageDsl.Gain(c(0.5)))),
                KatalystStageDsl.Gain(c(0.1)),
            ),
            blocks = 3,
        )

        // 2 * (1 + 0.5) * 0.1
        out.shouldBe({ 0.3 * left(it) }, { 0.3 * right(it) })
    }

    "a stage with no branch is not built: the bus passes unchanged" {
        val built = chain(parallel())

        built.pipeline.size shouldBe 0
        run(built, blocks = 2).shouldBe({ left(it) }, { right(it) })
    }

    // ── Aligned by latency ──────────────────────────────────────────────────────────────────────

    "a late branch and a dry one meet aligned: the dry is delayed to the late one, no comb" {
        val built = chain(parallel(branch(late), empty))
        val stage = built.pipeline.single().shouldBeInstanceOf<KatalystParallelEffect>()

        stage.latencyFrames shouldBe delay
        built.latencyFrames shouldBe delay

        // Unaligned, the sum would be in[k - D] + in[k]: a comb. Aligned, it is the input twice, D frames late.
        run(built, blocks = 8).shouldBe({ 2.0 * left(it - delay) }, { 2.0 * right(it - delay) })
    }

    "the padding follows the branch order: the late branch second gives the same sum" {
        run(chain(parallel(empty, branch(late))), blocks = 8).shouldBe({ 2.0 * left(it - delay) }, { 2.0 * right(it - delay) })
    }

    "nested branches add up their latency: a parallel inside a branch reports its longest branch" {
        // Branch one is late twice (a limiter, then a parallel whose longest branch is late once); branch two once.
        val built = chain(
            parallel(
                branch(late, parallel(branch(late), empty)),
                branch(late),
            )
        )

        built.latencyFrames shouldBe 2 * delay

        // Branch one: in[k - D] through (late + dry, aligned) = 2 * in[k - 2D]. Branch two, padded: in[k - 2D].
        run(built, blocks = 12).shouldBe({ 3.0 * left(it - 2 * delay) }, { 3.0 * right(it - 2 * delay) })
    }

    "a reset clears the pad rings: the next life starts from silence, not from the last one's frames" {
        val built = chain(parallel(branch(late), empty))

        run(built, blocks = 4)
        built.reset()
        built.applyParams(null)

        run(built, blocks = 4).shouldBe({ 2.0 * left(it - delay) }, { 2.0 * right(it - delay) })
    }

    // ── Matched in phase: the oversamplers' twins ───────────────────────────────────────────────

    /** A distort stage at [factor] that is off: the input through its oversampler's round trip, unshaped. */
    fun distortOff(factor: Int) = KatalystStageDsl.Distort(amount = c(0.0), oversample = factor)

    /**
     * [signal] (the first [frames] frames of it, from 0) through an unshaped round trip of a bare [Oversampler] per
     * entry of [stages], block by block, then [shift] frames late.
     */
    fun twin(signal: (Int) -> Double, frames: Int, vararg stages: Int, shift: Int = 0): (Int) -> Double {
        val out = DoubleArray(frames) { signal(it) }
        val scratch = ScratchBuffers(blockFrames)

        for (s in stages) {
            val os = Oversampler(s)

            for (b in 0 until frames / blockFrames) {
                val block = AudioBuffer(blockFrames) { out[b * blockFrames + it] }

                os.roundTrip(buffer = block, offset = 0, length = blockFrames, scratch = scratch) { _, _ -> }
                block.copyInto(destination = out, destinationOffset = b * blockFrames)
            }
        }

        return { k -> if (k - shift < 0) 0.0 else out[k - shift] }
    }

    "an oversampled branch and a dry one meet in phase: the dry gets the twin of the distort's oversampler" {
        val built = chain(parallel(branch(distortOff(2)), empty))
        val stage = built.pipeline.single().shouldBeInstanceOf<KatalystParallelEffect>()

        stage.oversamplers shouldBe listOf(1)
        stage.latencyFrames shouldBe 3
        built.latencyFrames shouldBe 3

        val frames = 8 * blockFrames
        val l = twin(::left, frames, 1)
        val r = twin(::right, frames, 1)

        run(built, blocks = 8).shouldBe({ 2.0 * l(it) }, { 2.0 * r(it) })
    }

    "an oversampled branch and a lookahead: each pads the other's kind, a twin and a ring" {
        val built = chain(parallel(branch(distortOff(2)), branch(late)))

        built.latencyFrames shouldBe 3 + delay

        val frames = 12 * blockFrames
        val l = twin(::left, frames, 1, shift = delay)
        val r = twin(::right, frames, 1, shift = delay)

        run(built, blocks = 12).shouldBe({ 2.0 * l(it) }, { 2.0 * r(it) })
    }

    "nested: a 2x branch holding a 4x parallel, against a dry branch that gets both twins" {
        val built = chain(parallel(branch(distortOff(2), parallel(branch(distortOff(4)), empty)), empty))
        val stage = built.pipeline.single().shouldBeInstanceOf<KatalystParallelEffect>()

        stage.oversamplers shouldBe listOf(1, 2)
        built.latencyFrames shouldBe 3 + 4

        // Branch one: the 2x round trip, then the inner sum, twice the 4x round trip. Branch two: both twins.
        val frames = 8 * blockFrames
        val l = twin(::left, frames, 1, 2)
        val r = twin(::right, frames, 1, 2)

        run(built, blocks = 8).shouldBe({ 3.0 * l(it) }, { 3.0 * r(it) })
    }

    "a reset clears the twins: the next life is the fresh round trip, not the last one's ring" {
        val built = chain(parallel(branch(distortOff(2)), empty))

        run(built, blocks = 4)
        built.reset()
        built.applyParams(null)

        val frames = 4 * blockFrames
        val l = twin(::left, frames, 1)
        val r = twin(::right, frames, 1)

        run(built, blocks = 4).shouldBe({ 2.0 * l(it) }, { 2.0 * r(it) })
    }

    "a clean distort next to the dry stays flat at the top: twice the input at 16.75 kHz, where a frame pad notched" {
        // A hard clip under 1 passes a quiet sine untouched, so the distorted branch is the drive's gain on the
        // oversampler's round trip; a whole-frame pad left -27.1 dB here at 2x and 44.1 kHz (audio review, round 2).
        // 16.75 kHz at 44.1 kHz repeats every 882 frames; the RMS spans whole periods from frame 1764 on.
        val hard = DistortionShapes.indexOf("hard").toInt()
        val sine = { k: Int -> 0.1 * sin(2.0 * PI * 16750.0 * k / sampleRate) }
        val clean = KatalystStageDsl.Distort(amount = c(0.01), shape = hard, oversample = 2)
        val blocks = (6 * 882) / blockFrames + 1
        val out = run(chain(parallel(branch(clean), empty)), blocks = blocks, left = sine, right = sine)

        val span = 2 * 882 until 6 * 882
        val level = sqrt(span.sumOf { out.l[it] * out.l[it] } / span.count()) / sqrt(span.sumOf { sine(it) * sine(it) } / span.count())

        // The drive's gain at 0.01 is a hair over 1, so in phase the sum is a hair over 2; a twin 15 degrees off would
        // pass the lower bound alone, the upper one closes that.
        level shouldBeGreaterThan 1.99
        level shouldBeLessThan 2.03
    }

    "a twin after a lookahead is a tail until it rings out, after every branch has gone quiet" {
        // The lookahead branch gets the 2x twin; its output, so the twin's input, ends 220 frames after the bus input.
        // After three quiet blocks the distort's hold (268) and the lookahead (220) have both passed, while the twin
        // has seen at most 256 quiet frames of its 268; after five, nothing rings.
        val built = chain(parallel(branch(distortOff(2)), branch(late)))
        val stage = built.pipeline.single().shouldBeInstanceOf<KatalystParallelEffect>()

        run(built, blocks = 4)
        run(built, blocks = 3, left = { 0.0 }, right = { 0.0 })
        stage.branches.none { it.hasTail() } shouldBe true
        stage.hasTail() shouldBe true

        run(built, blocks = 2, left = { 0.0 }, right = { 0.0 })
        stage.hasTail() shouldBe false
    }

    // ── The branches read the orbit's slots ─────────────────────────────────────────────────────

    "a slot in a branch reads the orbit's param state, and a new state reaches it" {
        val gain = KatalystStageDsl.Gain(IgnitorDsl.Param("branch.gain", 1.0))
        val built = chain(parallel(empty, branch(gain)), params = mapOf("branch.gain" to 0.0))

        // Written 0: only the dry branch is left.
        run(built, blocks = 2).shouldBe({ left(it) }, { right(it) })

        built.reset()
        built.applyParams(mapOf("branch.gain" to 3.0))

        // A fresh fader snaps, so the new factor holds from the first sample.
        run(built, blocks = 2).shouldBe({ 4.0 * left(it) }, { 4.0 * right(it) })
    }

    // ── Tails ───────────────────────────────────────────────────────────────────────────────────

    "a room in a branch is the chain's tail: declared at build, held while it rings" {
        val dry = chain(parallel(empty, branch(KatalystStageDsl.Gain(c(0.5)))))

        dry.declaresTail shouldBe false

        val roomy = chain(parallel(empty, branch(KatalystStageDsl.Reverb(wet = c(0.5), size = c(6.0)))))

        roomy.declaresTail shouldBe true
        roomy.hasTail() shouldBe false

        run(roomy, blocks = 20)

        roomy.hasTail() shouldBe true
    }

    "a delay at feedback 1 in a branch sustains the chain" {
        val loop = chain(parallel(empty, branch(KatalystStageDsl.Delay(wet = c(0.5), time = c(0.01), feedback = c(1.0)))))

        run(loop, blocks = 20)

        loop.sustainsItself() shouldBe true
    }

    // ── A duck in a branch is the orbit's duck ──────────────────────────────────────────────────

    "a duck inside a branch, nested or not, is the chain's duck, and no branch keeps one" {
        val duck = KatalystStageDsl.Duck(orbit = c(1.0), depth = c(0.5))
        val built = chain(parallel(empty, branch(parallel(empty, branch(duck)))))

        built.duck.shouldNotBeNull()
        built.ducksWith() shouldBe true

        val outer = built.pipeline.single().shouldBeInstanceOf<KatalystParallelEffect>()
        val inner = outer.branches[1].pipeline.single().shouldBeInstanceOf<KatalystParallelEffect>()

        outer.branches.forEach { it.duck.shouldBeNull() }
        inner.branches.forEach { it.duck.shouldBeNull() }
    }

    "the last duck in the order written wins, branches included" {
        val first = KatalystStageDsl.Duck(orbit = c(1.0), depth = c(0.5))
        val unset = KatalystStageDsl.Duck()

        // The chain's own unset duck comes after the branch's: it wins, and an unset duck does not duck.
        chain(parallel(empty, branch(first)), unset).ducksWith() shouldBe false
        // The branch's comes after: it wins.
        chain(unset, parallel(empty, branch(first))).ducksWith() shouldBe true
    }

    // ── A sanity check on the oracle ────────────────────────────────────────────────────────────

    "control: unaligned, the same two branches WOULD comb (the late branch alone is late)" {
        // Guards the alignment rows against a limiter that stopped being late: then every row above would pass
        // without any padding at all.
        val out = run(chain(late), blocks = 8)
        var differs = 0.0

        for (k in out.l.indices) {
            differs = maxOf(differs, abs(out.l[k] - left(k)))
        }

        differs shouldBeGreaterThan 0.1
    }
})
