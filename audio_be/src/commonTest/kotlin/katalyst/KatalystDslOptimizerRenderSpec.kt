/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.katalyst

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.probes.ProbeOutput
import io.peekandpoke.klang.audio_be.probes.ProbeSignal
import io.peekandpoke.klang.audio_be.probes.katalystProbeStage
import io.peekandpoke.klang.audio_be.warehouse.ReverbUnits
import io.peekandpoke.klang.audio_be.warehouse.SizedBuffers
import io.peekandpoke.klang.audio_bridge.Kat
import io.peekandpoke.klang.audio_bridge.KatalystBuilder
import io.peekandpoke.klang.audio_bridge.KatalystDsl
import io.peekandpoke.klang.audio_bridge.KatalystStageDsl
import io.peekandpoke.klang.audio_bridge.kat
import io.peekandpoke.klang.audio_bridge.optimize
import kotlin.math.floor
import kotlin.random.Random

/**
 * The promise of the Katalyst chain's pass (KLANG-00468, cylinder amp step 2): a chain and its optimized form render
 * the SAME samples, bit for bit, no tolerance. Each row builds the written chain and [KatalystDsl.optimize]'s form with
 * [KatalystChainBuilder] and runs both over the same stereo input (left and right different, 300 blocks of 128 frames,
 * a noise and a saw), sample for sample.
 *
 * Every merged row also pins that the pass merged what it should (the count of `eq` stages), so a pass that merges
 * nothing cannot pass by rendering the same chain twice. The refused rows (a tap in the later stage, a wall between)
 * compare the written chain with the pass's output too: the pass leaves them as written, so a pass that merged them
 * anyway would be heard here. The last row shows that the comparison can hear such a merge: the same chains merged by
 * hand render different samples.
 *
 * Besides the probe's plan (the parameters applied before every block, from block 0), the merged chains run with the
 * parameters applied once, with a reset in the middle followed by the parameters again, and over silence with a
 * transient, a NaN, an Infinity, subnormal and all `-0.0` inputs.
 */
class KatalystDslOptimizerRenderSpec : StringSpec({

    val sampleRate = 48000
    val blockFrames = 128
    val blocks = 300
    val frames = blocks * blockFrames

    /** A naive saw from -1 to 1 at [freq], times [level]. */
    fun saw(freq: Double, level: Double): DoubleArray = DoubleArray(frames) {
        val phase = it * freq / sampleRate

        level * (2.0 * (phase - floor(phase)) - 1.0)
    }

    /** White noise in -[level]..[level], seeded. */
    fun noise(seed: Int, level: Double): DoubleArray {
        val random = Random(seed)

        return DoubleArray(frames) { level * (2.0 * random.nextDouble() - 1.0) }
    }

    /** The two inputs: a noise (a seed per channel) and a saw (a pitch per channel). Never the same on both sides. */
    val inputs = listOf(
        ProbeSignal(name = "noise", left = noise(seed = 1, level = 0.5), right = noise(seed = 2, level = 0.5), inputEndFrame = frames),
        ProbeSignal(name = "saw", left = saw(freq = 110.0, level = 0.5), right = saw(freq = 173.3, level = 0.5), inputEndFrame = frames),
    )

    fun render(dsl: KatalystDsl, input: ProbeSignal): ProbeOutput =
        katalystProbeStage(dsl = dsl, sampleRate = sampleRate, blockFrames = blockFrames).render(input)

    /**
     * A block plan other than the probe's: the parameters applied before EVERY block (the owner's re-apply) when
     * [everyBlock], else once; the first apply at block [firstApplyAt] (blocks before it are processed with nothing
     * applied, as a cylinder whose first voice does not claim); and, at [resetAt] (or never, -1), the chain
     * [KatalystChain.reset] before that block and the parameters applied again [applyAfterReset] blocks later, as a
     * cylinder's clean slate is followed by its next owner.
     */
    class Plan(
        val name: String,
        val everyBlock: Boolean,
        val resetAt: Int = -1,
        val firstApplyAt: Int = 0,
        val applyAfterReset: Int = 0,
    )

    /** [dsl] over [input] by [plan]. No chain here declares a duck, so there is no duck pass. */
    fun render(dsl: KatalystDsl, input: ProbeSignal, plan: Plan): ProbeOutput {
        val chain = KatalystChainBuilder.build(
            dsl = dsl,
            sampleRate = sampleRate,
            blockFrames = blockFrames,
            rings = SizedBuffers.forRings(sampleRate),
            reverbs = ReverbUnits(sampleRate),
        )
        val ctx = KatalystContext(blockFrames = blockFrames, mixBuffer = StereoBuffer(blockFrames))
        val left = DoubleArray(input.frames)
        val right = DoubleArray(input.frames)

        for (block in 0 until input.frames / blockFrames) {
            val at = block * blockFrames

            input.left.copyInto(destination = ctx.mixBuffer.left, destinationOffset = 0, startIndex = at, endIndex = at + blockFrames)
            input.right.copyInto(destination = ctx.mixBuffer.right, destinationOffset = 0, startIndex = at, endIndex = at + blockFrames)

            if (block == plan.resetAt) {
                chain.reset()
            }

            // The first block of the current apply phase: after the reset when there was one, else the plan's first.
            val first = if (plan.resetAt in 0..block) plan.resetAt + plan.applyAfterReset else plan.firstApplyAt

            if (block == first || (plan.everyBlock && block > first)) {
                chain.applyParams(null)
            }

            chain.process(ctx)
            ctx.mixBuffer.left.copyInto(destination = left, destinationOffset = at, startIndex = 0, endIndex = blockFrames)
            ctx.mixBuffer.right.copyInto(destination = right, destinationOffset = at, startIndex = 0, endIndex = blockFrames)
        }

        return ProbeOutput(left = left, right = right)
    }

    /** The same double, bit for bit: a signed zero told apart, NaN for NaN. */
    fun sameBits(a: Double, b: Double): Boolean =
        if (a.isNaN() || b.isNaN()) a.isNaN() && b.isNaN() else a == b && 1.0 / a == 1.0 / b

    /** The first frame where [a] and [b] differ, or -1. */
    fun firstDifference(a: DoubleArray, b: DoubleArray): Int {
        for (i in a.indices) {
            if (!sameBits(a[i], b[i])) {
                return i
            }
        }

        return -1
    }

    /**
     * Asserts that [written] and [optimized] render the same bits on both channels of every one of [over], through the
     * probe or by [plan]. [audible] asks for a non-silent output too (not for inputs that are silence or near it).
     */
    fun sameSamples(
        clue: String,
        written: KatalystDsl,
        optimized: KatalystDsl,
        over: List<ProbeSignal> = inputs,
        plan: Plan? = null,
        audible: Boolean = true,
    ) {
        for (input in over) {
            val a = if (plan == null) render(dsl = written, input = input) else render(dsl = written, input = input, plan = plan)
            val b = if (plan == null) render(dsl = optimized, input = input) else render(dsl = optimized, input = input, plan = plan)
            val bRight = b.right ?: DoubleArray(0)
            val aRight = a.right ?: DoubleArray(0)

            if (audible) {
                withClue("$clue, ${input.name}: the output is not silent") {
                    (a.left.any { it != 0.0 }) shouldBe true
                }
            }

            val left = firstDifference(a.left, b.left)
            val right = firstDifference(aRight, bRight)

            withClue("$clue, ${input.name}, left: first differing frame (${if (left >= 0) "${a.left[left]} vs ${b.left[left]}" else ""})") {
                left shouldBe -1
            }

            withClue("$clue, ${input.name}, right: first differing frame") {
                aRight.size shouldBe input.frames
                right shouldBe -1
            }
        }
    }

    /** How many `eq` stages [dsl] holds, inside branches and region bodies included. */
    fun eqStages(dsl: KatalystDsl): Int = dsl.stages.sumOf { stage ->
        when (stage) {
            is KatalystStageDsl.Eq -> 1
            is KatalystStageDsl.Parallel -> stage.branches.sumOf { eqStages(it) }
            is KatalystStageDsl.OversampleBy -> eqStages(stage.body)
            is KatalystStageDsl.OversampleAtLeast -> eqStages(stage.body)
            is KatalystStageDsl.Body, is KatalystStageDsl.Vowel, is KatalystStageDsl.Delay, is KatalystStageDsl.Reverb,
            is KatalystStageDsl.Phaser, is KatalystStageDsl.Compressor, is KatalystStageDsl.Distort,
            is KatalystStageDsl.Shape, is KatalystStageDsl.Duck, is KatalystStageDsl.Gain, is KatalystStageDsl.Plus,
            is KatalystStageDsl.Minus, is KatalystStageDsl.Times, is KatalystStageDsl.Div, is KatalystStageDsl.Pow,
            is KatalystStageDsl.Min, is KatalystStageDsl.Max, is KatalystStageDsl.Abs -> 0
        }
    }

    /** A guitar cabinet as the chain doors write it: three bands, a lowpass of three passes, a highpass of two. */
    val cabinet: (KatalystBuilder) -> KatalystBuilder = { k ->
        k.eq { e -> e.band(freq = 120.kat, db = 2.kat).band(freq = 900.kat, db = (-3).kat).band(freq = 2500.kat, db = 4.kat) }
            .lowpass(freq = 5000.kat) { f -> f.passes(3.0) }
            .highpass(freq = 80.kat) { f -> f.passes(2.0) }
    }

    /**
     * One row: [written] and the pass's form render the same bits, and the pass took it from [before] eq stages to
     * [after] (equal for a refused row). The samples are compared first, so a wrong merge is caught by what it renders.
     */
    fun merged(clue: String, written: KatalystDsl, before: Int, after: Int) {
        val optimized = written.optimize()

        sameSamples(clue = clue, written = written, optimized = optimized)

        withClue("$clue: eq stages as written") { eqStages(written) shouldBe before }
        withClue("$clue: eq stages after the pass") { eqStages(optimized) shouldBe after }
    }

    "the cabinet: three stages become one, the same samples" {
        merged(clue = "cabinet", written = Kat { cabinet(it) }, before = 3, after = 1)
    }

    "a preamp's tail, highpass(100) then lowpass(5500), behind a distort: the same samples" {
        val written = Kat { it.distort(amount = 0.4.kat).highpass(freq = 100.kat).lowpass(freq = 5500.kat) }

        merged(clue = "preamp tail", written = written, before = 2, after = 1)
    }

    "the cabinet inside oversampleAtLeast(4): the same samples" {
        val written = Kat { it.oversampleAtLeast(4) { r -> cabinet(r.distort(amount = 0.3.kat)) } }

        merged(clue = "cabinet in a region", written = written, before = 3, after = 1)
    }

    "a bands split whose bands start with an eq: the same samples, the crossover's taps kept apart" {
        // Three bands at 200 and 2000 Hz. The lowest band carries its crossover sections, then the all-pass of the cut
        // above (a tap, in a stage of its own, which the pass may not merge into the one before), then the author's
        // eq and lowpass, which merge into the tap's stage. The two others merge whole.
        val written = Kat {
            it.bands { b ->
                b.band { x -> x.eq { e -> e.band(freq = 80.kat, db = 3.kat) }.lowpass(freq = 150.kat) }.cut(200.0)
                    .band { x -> x.highpass(freq = 300.kat).eq { e -> e.band(freq = 800.kat, db = (-2).kat) } }.cut(2000.0)
                    .band { x -> x.eq { e -> e.band(freq = 5000.kat, db = 2.kat) } }
            }
        }

        merged(clue = "bands", written = written, before = 9, after = 4)
    }

    "an eq run with a tap in the earlier stage merges: the same samples" {
        val written = Kat {
            it.eq { e -> e.tap(freq = 1200.kat, q = 1.5.kat, gain = 2.kat).band(freq = 400.kat, db = 3.kat) }
                .lowpass(freq = 4000.kat)
        }

        merged(clue = "tap in a", written = written, before = 2, after = 1)
    }

    "refused: a tap in the later stage is left as written, the same samples" {
        val written = Kat {
            it.eq { e -> e.band(freq = 400.kat, db = 6.kat) }
                .eq { e -> e.tap(freq = 400.kat, q = 2.kat, gain = 3.kat) }
        }

        merged(clue = "tap in b", written = written, before = 2, after = 2)
    }

    "refused: a distort between two eq stages is a wall, left as written, the same samples" {
        val written = Kat { it.highpass(freq = 120.kat).distort(amount = 0.5.kat).lowpass(freq = 3000.kat) }

        merged(clue = "wall", written = written, before = 2, after = 2)
    }

    // ── Other block plans and inputs (review round 1) ───────────────────────────────────────────────────────────────

    /** The merged chains of the rows above, written as the doors write them. */
    val mergedChains = listOf(
        "cabinet" to Kat { cabinet(it) },
        "preamp tail" to Kat { it.distort(amount = 0.4.kat).highpass(freq = 100.kat).lowpass(freq = 5500.kat) },
        "cabinet in a region" to Kat { it.oversampleAtLeast(4) { r -> cabinet(r.distort(amount = 0.3.kat)) } },
        "bands" to Kat {
            it.bands { b ->
                b.band { x -> x.eq { e -> e.band(freq = 80.kat, db = 3.kat) }.lowpass(freq = 150.kat) }.cut(200.0)
                    .band { x -> x.highpass(freq = 300.kat).eq { e -> e.band(freq = 800.kat, db = (-2).kat) } }.cut(2000.0)
                    .band { x -> x.eq { e -> e.band(freq = 5000.kat, db = 2.kat) } }
            }
        },
    )

    val everyBlock = Plan(name = "applied every block", everyBlock = true)
    val once = Plan(name = "applied once", everyBlock = false)

    /** Every merged chain under both plans (and the reset of [resetAt]) over [over]: the same bits. */
    fun allChains(label: String, over: List<ProbeSignal>, resetAt: Int = -1, audible: Boolean = true) {
        for ((name, written) in mergedChains) {
            withClue("$name: merged") { (eqStages(written.optimize()) < eqStages(written)) shouldBe true }

            for (plan in listOf(everyBlock, once)) {
                sameSamples(
                    clue = "$label, $name, ${plan.name}",
                    written = written,
                    optimized = written.optimize(),
                    over = over,
                    plan = Plan(name = plan.name, everyBlock = plan.everyBlock, resetAt = resetAt),
                    audible = audible,
                )
            }
        }
    }

    "the parameters applied once, before the first block only: the same samples" {
        allChains(label = "once", over = inputs)
    }

    "a reset in the middle of the render, then the parameters again: the same samples" {
        allChains(label = "reset", over = inputs, resetAt = blocks / 2)
    }

    "silence, then an impulse and a one-block burst: the same samples" {
        val left = DoubleArray(frames)
        val right = DoubleArray(frames)
        val burst = Random(7)

        left[100 * blockFrames + 37] = 1.0
        right[100 * blockFrames + 50] = -0.8

        for (i in 200 * blockFrames until 201 * blockFrames) {
            left[i] = 2.0 * burst.nextDouble() - 1.0
            right[i] = 2.0 * burst.nextDouble() - 1.0
        }

        allChains(label = "transient", over = listOf(ProbeSignal(name = "transient", left = left, right = right, inputEndFrame = frames)))
    }

    "an input holding a NaN sample, and one holding an Infinity sample: the same samples" {
        val nan = saw(freq = 110.0, level = 0.5).also { it[5000] = Double.NaN }
        val inf = saw(freq = 110.0, level = 0.5).also { it[5000] = Double.POSITIVE_INFINITY }
        val negInf = saw(freq = 173.3, level = 0.5).also { it[7000] = Double.NEGATIVE_INFINITY }

        allChains(
            label = "non-finite",
            over = listOf(
                ProbeSignal(name = "NaN", left = nan, right = saw(freq = 173.3, level = 0.5), inputEndFrame = frames),
                ProbeSignal(name = "Infinity", left = inf, right = negInf, inputEndFrame = frames),
            ),
        )
    }

    "a subnormal input (1e-310), and an all -0.0 input: the same samples" {
        val subnormal = DoubleArray(frames) { if (it % 2 == 0) 1e-310 else -1e-310 }
        val negativeZero = DoubleArray(frames) { -0.0 }

        allChains(
            label = "tiny",
            over = listOf(
                ProbeSignal(name = "subnormal", left = subnormal, right = DoubleArray(frames) { 1e-310 }, inputEndFrame = frames),
                ProbeSignal(name = "-0.0", left = negativeZero, right = negativeZero.copyOf(), inputEndFrame = frames),
            ),
            audible = false,
        )
    }

    // ── Blocks before the first apply: the chain configures itself (review round 1, part two) ──────────────────────

    "three blocks processed before the first apply: the same samples, the stages in place from the first block" {
        // Reachable at this level: a chain processed before anything applied it is what a cylinder runs under a first
        // voice that does not claim. `KatalystChain.process` applies such a chain from no state on its first block, so
        // the written chain's three fades from dry and the merged chain's one fade are gone (the cylinder's half of this
        // is `CylinderChainFirstBlockSpec`).
        val chains = listOf(
            "cabinet" to Kat { cabinet(it) },
            "cabinet in a region" to Kat { it.oversampleAtLeast(4) { r -> cabinet(r.distort(amount = 0.3.kat)) } },
            "bands" to mergedChains.first { it.first == "bands" }.second,
            "tap in a" to Kat {
                it.eq { e -> e.tap(freq = 1200.kat, q = 1.5.kat, gain = 2.kat).band(freq = 400.kat, db = 3.kat) }
                    .lowpass(freq = 4000.kat)
            },
        )

        for ((name, written) in chains) {
            for (everyBlockPlan in listOf(true, false)) {
                sameSamples(
                    clue = "late apply, $name, every block $everyBlockPlan",
                    written = written,
                    optimized = written.optimize(),
                    plan = Plan(name = "first apply at block 3", everyBlock = everyBlockPlan, firstApplyAt = 3),
                )

                sameSamples(
                    clue = "reset then one ownerless block, $name, every block $everyBlockPlan",
                    written = written,
                    optimized = written.optimize(),
                    plan = Plan(name = "reset, one block, apply", everyBlock = everyBlockPlan, resetAt = blocks / 2, applyAfterReset = 1),
                )
            }
        }
    }

    "a chain processed before any apply runs its stages from the first sample: the same as one applied first" {
        // The guarantee itself, written against the plain plan: three blocks with nothing applied render what a chain
        // applied before its first block renders, for the written cabinet (three stages) and the merged one.
        for (dsl in listOf(Kat { cabinet(it) }, Kat { cabinet(it) }.optimize())) {
            for (input in inputs) {
                val late = render(dsl = dsl, input = input, plan = Plan(name = "late", everyBlock = false, firstApplyAt = 3))
                val first = render(dsl = dsl, input = input, plan = once)

                withClue("first block, ${dsl.stages.size} stages, ${input.name}, left: first differing frame") {
                    firstDifference(late.left, first.left) shouldBe -1
                }

                withClue("first block, ${dsl.stages.size} stages, ${input.name}, right: first differing frame") {
                    firstDifference(late.right ?: DoubleArray(0), first.right ?: DoubleArray(0)) shouldBe -1
                    (late.right?.size ?: 0) shouldBe input.frames
                }
            }
        }
    }

    "the comparison hears a wrong merge: a tap merged by hand, and sections in the wrong order, render other samples" {
        val tapInB = Kat {
            it.eq { e -> e.band(freq = 400.kat, db = 6.kat) }
                .eq { e -> e.tap(freq = 400.kat, q = 2.kat, gain = 3.kat) }
        }
        val tapMergedByHand = KatalystDsl.of(KatalystStageDsl.Eq(sections = tapInB.stages.flatMap { (it as KatalystStageDsl.Eq).sections }))
        val reversed = Kat { cabinet(it) }.let { written ->
            KatalystDsl.of(KatalystStageDsl.Eq(sections = written.stages.reversed().flatMap { (it as KatalystStageDsl.Eq).sections }))
        }

        val written = Kat { cabinet(it) }

        for (input in inputs) {
            withClue("tap merged by hand, ${input.name}") {
                val a = render(dsl = tapInB, input = input).left
                val b = render(dsl = tapMergedByHand, input = input).left

                firstDifference(a, b) shouldNotBe -1
            }

            withClue("cabinet in reverse, ${input.name}") {
                firstDifference(render(dsl = written, input = input).left, render(dsl = reversed, input = input).left) shouldNotBe -1
            }
        }
    }

    "the cabinet's pipeline holds one EQ effect after the pass, three before" {
        fun eqEffects(dsl: KatalystDsl): Int = KatalystChainBuilder.build(
            dsl = dsl,
            sampleRate = sampleRate,
            blockFrames = blockFrames,
            rings = SizedBuffers.forRings(sampleRate),
            reverbs = ReverbUnits(sampleRate),
        ).pipeline.count { it is KatalystEqEffect }

        val written = Kat { cabinet(it) }

        withClue("as written") { eqEffects(written) shouldBe 3 }
        withClue("after the pass") { eqEffects(written.optimize()) shouldBe 1 }
    }
})
