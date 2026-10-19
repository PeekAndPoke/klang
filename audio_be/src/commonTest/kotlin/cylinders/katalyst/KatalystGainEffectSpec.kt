/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.cylinders.Cylinders
import io.peekandpoke.klang.audio_be.ignitor.ScratchBuffers
import io.peekandpoke.klang.audio_be.voices.TestIgnitors
import io.peekandpoke.klang.audio_be.voices.Voice
import io.peekandpoke.klang.audio_be.voices.VoiceTestHelpers
import io.peekandpoke.klang.audio_be.warehouse.ReverbUnits
import io.peekandpoke.klang.audio_be.warehouse.SizedBuffers
import io.peekandpoke.klang.audio_bridge.KatalystDsl
import io.peekandpoke.klang.audio_bridge.KatalystKnobDsl
import io.peekandpoke.klang.audio_bridge.KatalystStageDsl
import io.peekandpoke.klang.audio_bridge.constants.KNOB_GLIDE_SECONDS
import io.peekandpoke.klang.audio_bridge.constants.SLOT_UNSET
import io.peekandpoke.klang.common.infra.klangMapOf
import kotlin.math.abs
import kotlin.math.round

/**
 * The orbit's group fader ([KatalystGainEffect]): exact where it must be, and never a step.
 *
 * The master snaps its gain (its factor is resolved at chain build, so a new number is a new
 * chain and the 60 ms bus crossfade covers it). Here the factor is a SLOT, so `.katp` moves it
 * while the stage stands, and the stage owns the smoothing: the knob-glide LEVEL law
 * (`audio/ref/knob-glide.md`), linear over `KNOB_GLIDE_SECONDS` rounded to whole blocks, per sample
 * within a block, written from the block's end so it lands exactly (Katalyst 5c-8; it was one
 * block, from the start). The oracle below is that law, computed here from the constant.
 */
class KatalystGainEffectSpec : StringSpec({

    val blockFrames = 128
    val sampleRate = 44100

    /** The DC probe: a constant makes every step in the output the ramp's own. */
    val probe = 0.5

    /** The decided glide length: the glide time rounded to whole blocks (17 at 44.1 kHz). */
    val glideBlocks = round(KNOB_GLIDE_SECONDS * sampleRate / blockFrames).toInt()

    fun fader(): KatalystGainEffect = KatalystGainEffect(sampleRate = sampleRate, blockFrames = blockFrames)

    /**
     * The decided law for sample [i] of the [k]th block of a glide from [from] to [to] (k = 1 is the
     * block the change arrives in): the level at the end of block k is `from + (to - from) * k / G`,
     * `to` itself from block G on, and a block ramps linearly from where the previous one ended to
     * its own end.
     */
    fun law(from: Double, to: Double, k: Int, i: Int): Double {
        fun end(j: Int): Double = if (j <= 0) from else if (j >= glideBlocks) to else from + (to - from) * j / glideBlocks

        val step = (end(k) - end(k - 1)) / blockFrames

        return end(k) - step * (blockFrames - 1 - i)
    }

    /**
     * Asserts that [blocks] (consecutive blocks of the DC probe, the change arriving in the first)
     * follow the law from [from] to [to], and land on [to] exactly: the glide's last sample and every
     * sample after it are `probe * to` bit for bit.
     */
    fun shouldGlide(blocks: List<DoubleArray>, from: Double, to: Double) {
        for ((index, samples) in blocks.withIndex()) {
            val k = index + 1

            for (i in 0 until blockFrames) {
                withClue("$from -> $to, glide block $k frame $i") {
                    if (k > glideBlocks || k == glideBlocks && i == blockFrames - 1) {
                        samples[i] shouldBe probe * to
                    } else {
                        samples[i] shouldBe (probe * law(from = from, to = to, k = k, i = i) plusOrMinus 1e-12)
                    }
                }
            }
        }
    }

    fun ctx(): KatalystContext = KatalystContext(
        blockFrames = blockFrames,
        mixBuffer = StereoBuffer(blockFrames),
    )

    /** One block of DC through [fx], returning the left channel. */
    fun block(fx: KatalystGainEffect, level: Double = probe): DoubleArray {
        val ctx = ctx()

        for (i in 0 until blockFrames) {
            ctx.mixBuffer.left[i] = level
            ctx.mixBuffer.right[i] = level
        }

        fx.process(ctx)

        return ctx.mixBuffer.left.copyOf()
    }

    /** The largest sample-to-sample step in [samples], the zipper measure. */
    fun maxStep(samples: DoubleArray): Double {
        var worst = 0.0

        for (i in 1 until samples.size) {
            val step = abs(samples[i] - samples[i - 1])

            if (step > worst) {
                worst = step
            }
        }

        return worst
    }

    // ── Exactness ────────────────────────────────────────────────────────────────────────────────

    "unity is bit-transparent, negative zero included" {
        val fx = fader()
        fx.configure(1.0)

        val ctx = ctx()

        for (i in 0 until blockFrames) {
            ctx.mixBuffer.left[i] = if (i % 2 == 0) 0.3 else -0.0
            ctx.mixBuffer.right[i] = -0.0
        }

        fx.process(ctx)

        withClue("unity to unity is not a ramp: the stage takes the constant path") {
            fx.ramps shouldBe 0
        }

        for (i in 0 until blockFrames) {
            withClue("frame $i") {
                // Raw bits, not equality: `-0.0 == 0.0` is true, so a stage that flipped the sign
                // of every silent sample would pass a plain compare. A multiply by exactly 1.0
                // keeps the sign anyway, which is why the skip is a saving and not a change.
                ctx.mixBuffer.left[i].toRawBits() shouldBe (if (i % 2 == 0) 0.3 else -0.0).toRawBits()
                ctx.mixBuffer.right[i].toRawBits() shouldBe (-0.0).toRawBits()
            }
        }
    }

    "a gain of 2 doubles every sample exactly, from the first block on" {
        val fx = fader()
        fx.configure(2.0)

        val first = block(fx)
        val steady = block(fx)

        for (i in 0 until blockFrames) {
            withClue("frame $i") {
                // An ARRIVING factor is not a move: nothing was produced under the old one, so
                // there is nothing to be continuous with and the stage snaps.
                first[i] shouldBe probe * 2.0
                steady[i] shouldBe probe * 2.0
            }
        }

        fx.ramps shouldBe 0
    }

    "the fader is raw: a negative factor flips polarity and nothing is clamped" {
        val fx = fader()
        fx.configure(-3.0)

        block(fx)
        val steady = block(fx)

        steady[blockFrames - 1] shouldBe probe * -3.0
    }

    "a non-finite factor is UNSET: the fader keeps what it had, and nothing reaches the mix" {
        // The trap the body resonator fell into (found in review 2026-09-18), closed at this door
        // too. With a non-finite target `from == to` in `process` is FALSE FOREVER for a NaN, so
        // every block ramps, multiplies the whole orbit by NaN and counts a ramp, without bound;
        // an infinity settles but poisons the mix just the same. Unreachable through
        // `KatalystGainWriter`, which reads an unset slot as unity, so this guards the stage's own
        // door, where the cost of being wrong is unbounded.
        listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY).forEach { unset ->
            val fx = fader()

            fx.configure(0.5)
            block(fx) // the arriving factor snaps, so the fader now stands at 0.5

            val rampsBefore = fx.ramps

            fx.configure(unset)

            val out = block(fx)

            withClue("gain = $unset") {
                fx.gain shouldBe 0.5
                fx.ramps shouldBe rampsBefore
                out[blockFrames - 1] shouldBe probe * 0.5
            }
        }
    }

    "configureFrom moves the fader exactly as configure does with the holder's number" {
        // Engine follow-up 45: the writer hands its factor over in a holder (`configureFrom`), where it called
        // `configure` with the number. Two faders take the same factors, one each way, block by block: a snap, a
        // glide, a change mid-glide, unity, a polarity flip, an unset factor; every sample must match bit for bit.
        val factors = listOf(0.7, 0.7, 2.0, 2.0, 0.25, 1.0, 1.0, -1.5, Double.NaN, 0.0, 0.0, 3.0)
        val byNumber = fader()
        val byHolder = fader()
        val holder = DoubleArray(1)

        factors.forEachIndexed { n, factor ->
            byNumber.configure(factor)
            holder[0] = factor
            byHolder.configureFrom(holder)

            val expected = block(byNumber)
            val actual = block(byHolder)

            for (i in 0 until blockFrames) {
                withClue("block $n (factor $factor), frame $i") {
                    actual[i].toRawBits() shouldBe expected[i].toRawBits()
                }
            }
        }
    }

    // ── The glide ────────────────────────────────────────────────────────────────────────────────

    "a change glides per sample over the glide time and lands exactly, across the fader's full range" {
        // Both ways, extremes included, and through exactly 0 (a polarity flip passes it mid-glide).
        // 4 -> 0.01 is the pair on which a ramp written from the block's START misses the target by
        // a rounding in the last block (the gain stage's old formula), so the exact landing below can
        // fail.
        val jumps = listOf(0.0 to 1.0, 1.0 to 0.0, 0.01 to 4.0, 4.0 to 0.01, -1.0 to 1.0, 1.0 to 2.0)

        for ((from, to) in jumps) {
            val fx = fader()
            fx.configure(from)
            block(fx)

            fx.configure(to)
            val glide = List(glideBlocks + 2) { block(fx) }

            shouldGlide(blocks = glide, from = from, to = to)

            withClue("$from -> $to: one glide, $glideBlocks blocks long, then a constant") {
                fx.ramps shouldBe glideBlocks
                fx.gain shouldBe to
            }
        }
    }

    "the glide bounds the step at the change, where a one-block ramp and a snap would not" {
        val fx = fader()
        fx.configure(1.0)
        val before = block(fx)

        fx.configure(2.0)
        val across = before + List(glideBlocks + 1) { block(fx) }.reduce { a, b -> a + b }

        // Derived from the law, not read off a run: the level moves by `|new - old| / (G * n)` per
        // sample, so the DC output steps by at most `probe * 1.0 / (17 * 128)`. A one-block ramp
        // would step 17 times more, a snap `probe * 1.0`.
        maxStep(across) shouldBeLessThan (probe * 1.0 / (glideBlocks * blockFrames)) * 1.01
    }

    "a new target mid-glide turns from where the fader stands, never from where the old glide was going" {
        // The plan's first question: the level in force outlives every glide, so a retarget starts
        // from it. Glide 1 -> 2 for five blocks, then the owner changes and wants 0.
        val fx = fader()
        fx.configure(1.0)
        block(fx)

        fx.configure(2.0)
        repeat(5) { block(fx) }

        val stands = law(from = 1.0, to = 2.0, k = 5, i = blockFrames - 1)

        withClue("where the fader stands after five blocks of the first glide") {
            fx.gain shouldBe (stands plusOrMinus 1e-12)
        }

        fx.configure(0.0)
        val turn = List(glideBlocks + 1) { block(fx) }

        // A full glide from THERE, continuous with the last sample of the first glide.
        shouldGlide(blocks = turn, from = stands, to = 0.0)
    }

    "a reset mid-glide: the next life snaps, it does not glide from the old life's level" {
        // The plan's second question: the record of a finished life (the level and the running glide)
        // is forgotten in reset, and the next life's first factor arrives at once.
        val fx = fader()
        fx.configure(1.0)
        block(fx)
        fx.configure(4.0)
        repeat(3) { block(fx) }

        fx.reset()
        fx.configure(0.25)

        val rampsBefore = fx.ramps
        val next = block(fx)

        for (i in 0 until blockFrames) {
            withClue("frame $i") {
                next[i] shouldBe probe * 0.25
            }
        }

        fx.ramps shouldBe rampsBefore
    }

    "a steady fader never glides at all: it arrives and then multiplies by a constant" {
        val fx = fader()

        repeat(20) {
            fx.configure(0.25)
            block(fx)
        }

        // The work pin (the `hasRawTap` / `staticConfigureSkips` precedent): re-applying the same
        // number every block, which is what a chain without `.katp` does, must not keep gliding.
        fx.ramps shouldBe 0
        fx.gain shouldBe 0.25
    }

    "reset puts the fader back to unity, so a reused orbit starts transparent" {
        val fx = fader()
        fx.configure(4.0)
        block(fx)
        block(fx)

        fx.reset()

        val after = block(fx)

        // The FIRST sample, not just the last: a reset that only re-targeted unity would glide
        // DOWN from the factor it still held, so the orbit's next tenant would hear 50 ms of
        // the previous one's level. The clean slate is immediate, which is also the plan's fourth
        // question: the stage holds no reference, so reset completes on the spot.
        after[0] shouldBe probe
        after[blockFrames - 1] shouldBe probe
        fx.gain shouldBe 1.0

        withClue("and getting there was not a ramp either") {
            fx.ramps shouldBe 0
        }
    }

    "retire puts the fader back to unity too: a shelved stage holds no level" {
        val fx = fader()
        fx.configure(4.0)
        block(fx)

        fx.retire()

        // Retiring is the clean slate for a stage going to the shelf WITH its cylinder, and the
        // next tenant configures it from scratch. A retire that forgot the factor would scale the
        // first block of whoever rents this cylinder next.
        block(fx)[0] shouldBe probe
        fx.gain shouldBe 1.0
    }

    // ── Through the chain: the writer's one guard ────────────────────────────────────────────────

    "an unset gain slot is unity, not a NaN in the mix" {
        // The writer's NaN guard: a
        // non-finite slot was never set, and an unset fader is the identity. Without it the whole
        // orbit would be NaN for good.
        val chain = KatalystChainBuilder.build(
            dsl = KatalystDsl.of(KatalystStageDsl.Gain(gain = KatalystKnobDsl.Param("gain", SLOT_UNSET))),
            sampleRate = sampleRate,
            blockFrames = blockFrames,
            rings = SizedBuffers.forRings(sampleRate),
            reverbs = ReverbUnits(sampleRate),
        )

        chain.applyParams(null)

        val ctx = ctx()

        for (i in 0 until blockFrames) {
            ctx.mixBuffer.left[i] = probe
            ctx.mixBuffer.right[i] = probe
        }

        chain.process(ctx)

        ctx.mixBuffer.left[blockFrames - 1] shouldBe probe
    }

    "the gain slot is what `katp` moves, and the move glides to the value the pattern wrote" {
        val chain = KatalystChainBuilder.build(
            dsl = KatalystDsl.of(KatalystStageDsl.Gain(gain = KatalystKnobDsl.Param("gain", 1.0))),
            sampleRate = sampleRate,
            blockFrames = blockFrames,
            rings = SizedBuffers.forRings(sampleRate),
            reverbs = ReverbUnits(sampleRate),
        )

        fun renderBlock(): DoubleArray {
            val ctx = ctx()

            for (i in 0 until blockFrames) {
                ctx.mixBuffer.left[i] = probe
                ctx.mixBuffer.right[i] = probe
            }

            chain.process(ctx)

            return ctx.mixBuffer.left.copyOf()
        }

        // In service first, at the authored default, so what follows is a MOVE and not an arrival.
        chain.applyParams(null)
        renderBlock()

        chain.applyParams(klangMapOf("gain" to 0.5))

        // The glide, landing exactly on what the pattern wrote.
        shouldGlide(blocks = List(glideBlocks + 1) { renderBlock() }, from = 1.0, to = 0.5)
    }

    "an expression over the gain slot moves over the same glide: katp(fader) on gain(fader * 0.5)" {
        // Knob expressions step 4 (`docs/tasks-archive/2026-10/20261012-katalyst-knob-expressions.md`): the expression is
        // evaluated at the resolve, and the stage glides to its value as it glides to a plain slot's.
        val chain = KatalystChainBuilder.build(
            dsl = KatalystDsl.of(
                KatalystStageDsl.Gain(
                    gain = KatalystKnobDsl.Times(
                        left = KatalystKnobDsl.Param("fader", 1.0),
                        right = KatalystKnobDsl.Constant(0.5),
                    ),
                )
            ),
            sampleRate = sampleRate,
            blockFrames = blockFrames,
            rings = SizedBuffers.forRings(sampleRate),
            reverbs = ReverbUnits(sampleRate),
        )

        fun renderBlock(): DoubleArray {
            val ctx = ctx()

            for (i in 0 until blockFrames) {
                ctx.mixBuffer.left[i] = probe
                ctx.mixBuffer.right[i] = probe
            }

            chain.process(ctx)

            return ctx.mixBuffer.left.copyOf()
        }

        chain.applyParams(null)

        withClue("in service at the default, 1 * 0.5") { renderBlock()[blockFrames - 1] shouldBe probe * 0.5 }

        chain.applyParams(klangMapOf("fader" to 0.6))

        val first = renderBlock()

        // By hand, at 44.1 kHz: 17 glide blocks from 0.5 to 0.6 * 0.5 = 0.3. The first block ends at
        // 0.5 - 0.2 / 17 = 0.48823529411764705, and its frame 63 sits 64 frames of 0.2 / 17 / 128 earlier on the ramp,
        // at 0.4941176470588235; times the probe 0.5.
        withClue("frame 63 of the first glide block") { first[63] shouldBe (0.24705882352941178 plusOrMinus 1e-12) }
        withClue("the first glide block's last frame") {
            first[blockFrames - 1] shouldBe (0.24411764705882352 plusOrMinus 1e-12)
        }

        shouldGlide(blocks = listOf(first) + List(glideBlocks) { renderBlock() }, from = 0.5, to = 0.3)
    }

    // ── The mute idiom, through a real cylinder ──────────────────────────────────────────────────

    "a gain-0 orbit stays exactly silent across the deactivation grace, block after block" {
        // `k.classic().gain(0)` is how a mix is muted at the bus. The mix is then silent while the
        // voices play; until Katalyst 5c-8 `Cylinder.tryDeactivate` reached its silence grace every
        // tenth block, deactivated and RESET the chain, and the next voice to claim the orbit
        // reconfigured the fader, so an arriving factor that ramped came out at nearly full level
        // every tenth block: a buzz out of an orbit the author muted. Since 5c-8 the
        // orbit does not deactivate while its voice plays (`CylinderFaderThroughZeroSpec`); the row
        // stays, because the fader must be silent from the orbit's first block whichever way it
        // gets there.
        val registry = KatalystRegistry()
        val cylinders = Cylinders(blockFrames = blockFrames, sampleRate = sampleRate, katalysts = registry)
        val orbit = 1

        registry.register(
            "mute",
            KatalystDsl(KatalystDsl.classic.stages + KatalystStageDsl.Gain(gain = KatalystKnobDsl.Constant(0.0))),
        )
        cylinders.requestChain(orbit, "mute")

        val renderCtx = Voice.RenderContext(
            cylinders = cylinders,
            sampleRate = sampleRate,
            blockFrames = blockFrames,
            voiceBuffer = AudioBuffer(blockFrames),
            scratchBuffers = ScratchBuffers(blockFrames),
        )

        val blocks = 40
        val voice = VoiceTestHelpers.createSynthVoice(
            endFrame = (blocks * blockFrames).toDouble(),
            gateEndFrame = (blocks * blockFrames).toDouble(),
            cylinderId = orbit,
            sampleRate = sampleRate,
            blockFrames = blockFrames,
            signal = TestIgnitors.constant,
        )

        val fusion = StereoBuffer(blockFrames)

        for (b in 0 until blocks) {
            renderCtx.blockStart = (b * blockFrames).toDouble()
            cylinders.clearAll()
            fusion.clear()
            voice.render(renderCtx)
            cylinders.processAndMix(fusion, renderCtx.blockStart)

            for (i in 0 until blockFrames) {
                withClue("block $b frame $i") {
                    fusion.left[i] shouldBe 0.0
                    fusion.right[i] shouldBe 0.0
                }
            }
        }
    }
})
