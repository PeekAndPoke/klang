/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.effects.Phaser
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * The contract of [KatalystPhaserEffect] (Katalyst 5c-9, `docs/plans/knob-glide.md` and
 * `docs/tasks/katalyst-dsl.md` step 5c).
 *
 * Every oracle here is written out by hand from the decided law, never read back from the stage:
 * the glide is the straight line from where the knob stands to its target over
 * `KNOB_GLIDE_SECONDS` rounded to whole blocks (17 at 44.1 kHz and 128 frames), per sample written
 * from the block's END so the last sample of the last block IS the target. The audio oracles run
 * the BARE [Phaser] with those hand-written numbers, which is the only thing the stage may claim
 * to do.
 */
class KatalystPhaserEffectSpec : StringSpec({

    val sampleRate = 44100
    val frames = 128

    // 0.05 s * 44100 = 2205 samples, 17.2 blocks of 128, rounded to 17.
    val glideBlocks = 17

    /** A saw band-limited to 3 kHz, so nothing in the source can be mistaken for an artifact. */
    fun source(blocks: Int, f0: Double = 110.0): DoubleArray {
        val n = blocks * frames
        val out = DoubleArray(n)
        val maxH = (3000.0 / f0).toInt()
        var norm = 0.0

        for (h in 1..maxH) {
            norm += 1.0 / h
        }

        for (i in 0 until n) {
            var v = 0.0

            for (h in 1..maxH) {
                v += sin(2.0 * PI * f0 * h * (i.toDouble() / sampleRate)) / h
            }

            out[i] = 0.5 * v / norm
        }

        return out
    }

    fun stage(): KatalystPhaserEffect = KatalystPhaserEffect(
        phaser = Phaser(sampleRate),
        sampleRate = sampleRate,
        blockFrames = frames,
    )

    data class P(
        val wet: Double,
        val rate: Double = 1.7,
        val center: Double = 1000.0,
        val sweep: Double = 1000.0,
        val floor: Double = 1.0,
    )

    /** Drives the stage the way the chain's writer does: one `configure` and one `process` per block. */
    fun run(src: DoubleArray, resetAt: Int = -1, settingsAt: (Int) -> P): DoubleArray {
        val fx = stage()
        val ctx = KatalystContext(blockFrames = frames, mixBuffer = StereoBuffer(frames))
        val out = DoubleArray(src.size)
        val blocks = src.size / frames

        for (k in 0 until blocks) {
            if (k == resetAt) {
                fx.reset()
            }

            val s = settingsAt(k)

            fx.configure(depth = s.wet, rate = s.rate, center = s.center, sweep = s.sweep, floor = s.floor)

            for (i in 0 until frames) {
                ctx.mixBuffer.left[i] = src[k * frames + i]
                ctx.mixBuffer.right[i] = src[k * frames + i]
            }

            fx.process(ctx)

            for (i in 0 until frames) {
                out[k * frames + i] = ctx.mixBuffer.left[i]
            }
        }

        return out
    }

    /** The same blocks through the BARE DSP, with the knobs as they stand: the settled reference. */
    fun bare(src: DoubleArray, settingsAt: (Int) -> P): DoubleArray {
        val p = Phaser(sampleRate)
        val buf = StereoBuffer(frames)
        val out = DoubleArray(src.size)
        val blocks = src.size / frames

        for (k in 0 until blocks) {
            val s = settingsAt(k)

            p.depth = s.wet

            if (p.depth >= Phaser.MIN_ACTIVE_DEPTH) {
                p.rate = s.rate
                p.center = s.center
                p.sweep = s.sweep
                p.floor = s.floor
                p.feedback = 0.5
            }

            for (i in 0 until frames) {
                buf.left[i] = src[k * frames + i]
                buf.right[i] = src[k * frames + i]
            }

            p.process(buf, frames)

            for (i in 0 until frames) {
                out[k * frames + i] = buf.left[i]
            }
        }

        return out
    }

    /**
     * The decided law, written out: the value the knob holds at the END of each of [blocks] blocks
     * of one glide, starting from [from] and landing on [to] exactly.
     */
    fun line(from: Double, to: Double, blocks: Int): DoubleArray = DoubleArray(blocks) { k ->
        if (k == blocks - 1) to else from + (to - from) * ((k + 1).toDouble() / blocks)
    }

    /**
     * The per-sample value inside a block that ends on [end] and started the block at [begin],
     * written from the END like the stage's own ramp.
     *
     * `/ frames` and not `* (1 / frames)`, and the two agree here: 128 is a power of two, so both
     * scalings are exact. The per-BLOCK [line] has no such luck, which is why these rows compare
     * within a tolerance rather than bit for bit.
     */
    fun rampedAt(begin: Double, end: Double, i: Int): Double = end - ((end - begin) / frames) * (frames - 1 - i)

    "settled: a phaser whose knobs stand renders exactly what the bare DSP does" {
        val src = source(24)
        val settings = { _: Int -> P(wet = 0.8, rate = 1.7, center = 1400.0, sweep = 900.0, floor = 0.4) }

        val got = run(src, settingsAt = settings)
        val want = bare(src, settings)

        for (i in src.indices) {
            withClue("sample $i") {
                got[i].toRawBits() shouldBe want[i].toRawBits()
            }
        }
    }

    "the first initialisation is instant: the first block is the bare DSP, breakpoint included" {
        // The breakpoint is the sharp one: the cores hold 1 kHz until something writes them, so a
        // stage that did not SNAP its centre would start alpha at 1 kHz and ramp away from it.
        val src = source(1)
        val settings = { _: Int -> P(wet = 0.9, rate = 3.0, center = 4000.0, sweep = 2500.0, floor = 0.2) }

        val got = run(src, settingsAt = settings)
        val want = bare(src, settings)

        for (i in src.indices) {
            withClue("sample $i") {
                got[i].toRawBits() shouldBe want[i].toRawBits()
            }
        }
    }

    "a wet change ramps the C4 coefficients on the straight line, per sample, landing exactly" {
        val blocks = 4 + glideBlocks + 3
        val src = source(blocks)
        val change = 4
        val before = P(wet = 0.2)
        val after = P(wet = 1.0)

        val got = run(src) { k -> if (k < change) before else after }

        // The wet SAMPLES: the same cascade fed the same input, read at dry 0 / wet 1.
        val wetOnly = run(src) { _ -> P(wet = 1.0, floor = 0.0) }

        val dryFrom = cosPow2(before.wet).coerceAtLeast(before.floor)
        val dryTo = cosPow2(after.wet).coerceAtLeast(after.floor)
        val wetFrom = sinPow2(before.wet)
        val wetTo = sinPow2(after.wet)

        val dryLine = line(dryFrom, dryTo, glideBlocks)
        val wetLine = line(wetFrom, wetTo, glideBlocks)

        for (g in 0 until glideBlocks) {
            val k = change + g
            val dryBegin = if (g == 0) dryFrom else dryLine[g - 1]
            val wetBegin = if (g == 0) wetFrom else wetLine[g - 1]

            for (i in 0 until frames) {
                val at = k * frames + i
                val want = src[at] * rampedAt(dryBegin, dryLine[g], i) + wetOnly[at] * rampedAt(wetBegin, wetLine[g], i)

                withClue("glide block $g sample $i") {
                    abs(got[at] - want) shouldBeLessThanOrEqual 1e-12
                }
            }
        }

        // Landed: from here on it is the settled path at the new knobs.
        val settled = run(src) { _ -> P(wet = 1.0) }

        for (k in (change + glideBlocks) until blocks) {
            for (i in 0 until frames) {
                val at = k * frames + i

                withClue("settled block $k sample $i") {
                    abs(got[at] - settled[at]) shouldBeLessThanOrEqual 1e-9
                }
            }
        }
    }

    "at a floor below 1 the DRY coefficient ramps too: a wet change crossfades both ways" {
        // With the default floor of 1.0 the C4 law pins dryC at 1 for every wet, so a wet change
        // moves the WET coefficient alone and the dry ramp is never exercised. Below 1 the knob is
        // a crossfade and the dry coefficient is what carries the old signal out.
        val blocks = 4 + glideBlocks + 3
        val src = source(blocks)
        val change = 4
        val floor = 0.2
        val before = P(wet = 0.2, floor = floor)
        val after = P(wet = 1.0, floor = floor)

        val got = run(src) { k -> if (k < change) before else after }
        val wetOnly = run(src) { _ -> P(wet = 1.0, floor = 0.0) }

        val dryFrom = cosPow2(before.wet).coerceAtLeast(floor)
        val dryTo = cosPow2(after.wet).coerceAtLeast(floor)

        withClue("the row is about a MOVING dry coefficient") {
            abs(dryTo - dryFrom) shouldBeGreaterThan 0.5
        }

        val dryLine = line(dryFrom, dryTo, glideBlocks)
        val wetLine = line(sinPow2(before.wet), sinPow2(after.wet), glideBlocks)

        for (g in 0 until glideBlocks) {
            val k = change + g
            val dryBegin = if (g == 0) dryFrom else dryLine[g - 1]
            val wetBegin = if (g == 0) sinPow2(before.wet) else wetLine[g - 1]

            for (i in 0 until frames) {
                val at = k * frames + i
                val want = src[at] * rampedAt(dryBegin, dryLine[g], i) + wetOnly[at] * rampedAt(wetBegin, wetLine[g], i)

                withClue("glide block $g sample $i") {
                    abs(got[at] - want) shouldBeLessThanOrEqual 1e-12
                }
            }
        }
    }

    "at a floor below 1 a floor change ramps the dry coefficient alone" {
        // The wet coefficient does not move at all here, so this row fails on nothing BUT the dry
        // ramp.
        val blocks = 4 + glideBlocks + 3
        val src = source(blocks)
        val change = 4
        val wet = 0.5
        val before = P(wet = wet, floor = 1.0)
        val after = P(wet = wet, floor = 0.2)

        val got = run(src) { k -> if (k < change) before else after }
        val wetOnly = run(src) { _ -> P(wet = 1.0, floor = 0.0) }

        val dryFrom = cosPow2(wet).coerceAtLeast(1.0)
        val dryTo = cosPow2(wet).coerceAtLeast(0.2)
        val wetC = sinPow2(wet)

        withClue("the dry coefficient really moves") {
            abs(dryTo - dryFrom) shouldBeGreaterThan 0.4
        }

        val dryLine = line(dryFrom, dryTo, glideBlocks)

        for (g in 0 until glideBlocks) {
            val k = change + g
            val dryBegin = if (g == 0) dryFrom else dryLine[g - 1]

            for (i in 0 until frames) {
                val at = k * frames + i
                val want = src[at] * rampedAt(dryBegin, dryLine[g], i) + wetOnly[at] * wetC

                withClue("glide block $g sample $i") {
                    abs(got[at] - want) shouldBeLessThanOrEqual 1e-12
                }
            }
        }
    }

    "at a floor below 1 the OFF edge rides the dry coefficient back up to 1" {
        val blocks = 4 + glideBlocks + 3
        val src = source(blocks)
        val change = 4

        val got = run(src) { k -> if (k < change) P(wet = 0.9, floor = 0.2) else P(wet = 0.0, floor = 0.2) }

        // Before the edge the whole orbit mix is floored, so the stage is far from the dry mix.
        var floored = 0.0

        for (i in 0 until frames) {
            val at = (change - 1) * frames + i
            floored = maxOf(floored, abs(got[at] - src[at]))
        }

        withClue("the floored stage really differs from the dry mix") {
            floored shouldBeGreaterThan 0.05
        }

        // The landing block ends on the dry mix exactly: dryC back at 1, wetC at 0.
        val landing = change + glideBlocks - 1

        got[landing * frames + frames - 1].toRawBits() shouldBe src[landing * frames + frames - 1].toRawBits()

        // And it got there by riding, not by cutting: the block before the landing is still wet.
        var before = 0.0

        for (i in 0 until frames) {
            val at = (landing - 1) * frames + i
            before = maxOf(before, abs(got[at] - src[at]))
        }

        before shouldBeGreaterThan 1e-6
    }

    "the OFF edge stays wet until the glide lands, and is the dry mix from the landing sample on" {
        val blocks = 4 + glideBlocks + 4
        val src = source(blocks)
        val change = 4

        val got = run(src) { k -> if (k < change) P(wet = 0.8) else P(wet = 0.0) }

        // Every glide block but the landing one is still audibly the phaser.
        for (g in 0 until glideBlocks - 1) {
            val k = change + g
            var worst = 0.0

            for (i in 0 until frames) {
                val at = k * frames + i
                worst = maxOf(worst, abs(got[at] - src[at]))
            }

            withClue("glide block $g still runs the cascade") {
                worst shouldBeGreaterThan 1e-6
            }
        }

        // The landing block ends on identity exactly, and everything after it is the dry mix.
        val landing = change + glideBlocks - 1

        withClue("the landing block's last sample is the dry mix, bit for bit") {
            got[landing * frames + frames - 1].toRawBits() shouldBe src[landing * frames + frames - 1].toRawBits()
        }

        for (k in (landing + 1) until blocks) {
            for (i in 0 until frames) {
                val at = k * frames + i

                withClue("block $k sample $i is the dry mix") {
                    got[at].toRawBits() shouldBe src[at].toRawBits()
                }
            }
        }
    }

    "the ON edge starts from the dry mix and rises: the first glide block is within one step of dry" {
        val blocks = 4 + glideBlocks + 2
        val src = source(blocks)
        val change = 4

        val got = run(src) { k -> if (k < change) P(wet = 0.0) else P(wet = 1.0) }
        val settled = run(src) { _ -> P(wet = 1.0) }

        // Before the edge the stage is the dry mix.
        for (i in 0 until change * frames) {
            withClue("sample $i before the edge") {
                got[i].toRawBits() shouldBe src[i].toRawBits()
            }
        }

        // The first glide block carries about one of 17 shares of the wet coefficient, against
        // the control that switched on at full weight on the same block.
        var worst = 0.0
        var full = 0.0

        for (i in 0 until frames) {
            val at = change * frames + i
            worst = maxOf(worst, abs(got[at] - src[at]))
            full = maxOf(full, abs(settled[at] - src[at]))
        }

        withClue("the control is a real difference") {
            full shouldBeGreaterThan 1e-3
        }

        withClue("the ON edge does not arrive at full wet") {
            worst shouldBeLessThanOrEqual full * 0.25
        }
    }

    "a breakpoint change is continuous: the first sample after it is what it would have been" {
        val blocks = 4 + glideBlocks + 2
        val src = source(blocks)
        val change = 4

        val moved = run(src) { k -> if (k < change) P(wet = 0.8, center = 400.0) else P(wet = 0.8, center = 6000.0) }
        val held = run(src) { _ -> P(wet = 0.8, center = 400.0) }

        withClue("alpha is continuous across the block seam") {
            moved[change * frames].toRawBits() shouldBe held[change * frames].toRawBits()
        }

        // The change is real: by the end of the glide the two runs are far apart.
        var worst = 0.0
        val at = (change + glideBlocks - 1) * frames

        for (i in 0 until frames) {
            worst = maxOf(worst, abs(moved[at + i] - held[at + i]))
        }

        withClue("the breakpoint really moved") {
            worst shouldBeGreaterThan 1e-3
        }
    }

    "the breakpoint glides on the straight line, one value per block" {
        val fx = stage()
        val ctx = KatalystContext(blockFrames = frames, mixBuffer = StereoBuffer(frames))

        fx.configure(depth = 0.8, rate = 1.7, center = 400.0, sweep = 800.0, floor = 1.0)
        fx.process(ctx)

        fx.center shouldBe 400.0
        fx.sweep shouldBe 800.0

        val centerLine = line(400.0, 6000.0, glideBlocks)
        val sweepLine = line(800.0, 200.0, glideBlocks)

        for (g in 0 until glideBlocks) {
            fx.configure(depth = 0.8, rate = 1.7, center = 6000.0, sweep = 200.0, floor = 1.0)
            fx.process(ctx)

            withClue("centre at glide block $g") {
                abs(fx.center - centerLine[g]) shouldBeLessThanOrEqual 1e-9
            }

            withClue("sweep at glide block $g") {
                abs(fx.sweep - sweepLine[g]) shouldBeLessThanOrEqual 1e-9
            }
        }

        fx.center shouldBe 6000.0
        fx.sweep shouldBe 200.0
    }

    "rate does not glide: it takes effect at once, and the LFO phase carries on" {
        val blocks = 8
        val src = source(blocks)
        val change = 4

        val moved = run(src) { k -> if (k < change) P(wet = 0.8, rate = 1.0) else P(wet = 0.8, rate = 9.0) }
        val held = run(src) { _ -> P(wet = 0.8, rate = 1.0) }

        // The phase is continuous, so the first sample of the change block is unmoved ...
        withClue("the sweep clock keeps its position") {
            moved[change * frames].toRawBits() shouldBe held[change * frames].toRawBits()
        }

        // ... and the rest of that very block already runs at the new rate, without a glide.
        var worst = 0.0

        for (i in 0 until frames) {
            val at = change * frames + i
            worst = maxOf(worst, abs(moved[at] - held[at]))
        }

        withClue("the new rate is in force on the change block itself") {
            worst shouldBeGreaterThan 1e-6
        }
    }

    "a gated owner does not write the kernel params: the retained breakpoint survives" {
        val fx = stage()
        val ctx = KatalystContext(blockFrames = frames, mixBuffer = StereoBuffer(frames))

        fx.configure(depth = 0.8, rate = 1.7, center = 2500.0, sweep = 1500.0, floor = 1.0)
        fx.process(ctx)

        fx.center shouldBe 2500.0

        // An owner without a phaser: its centre default must NOT reach the kernel.
        repeat(glideBlocks + 4) {
            fx.configure(depth = 0.0, rate = 0.0, center = 1000.0, sweep = 1000.0, floor = 1.0)
            fx.process(ctx)
        }

        fx.center shouldBe 2500.0
        fx.sweep shouldBe 1500.0
        fx.dryCoeff shouldBe 1.0
        fx.wetCoeff shouldBe 0.0
    }

    "a reset mid-glide: the next life snaps, it does not glide from the old life's coefficients" {
        val fx = stage()
        val ctx = KatalystContext(blockFrames = frames, mixBuffer = StereoBuffer(frames))

        fx.configure(depth = 0.9, rate = 1.7, center = 3000.0, sweep = 500.0, floor = 1.0)
        fx.process(ctx)

        // Half a glide towards dry, then the host tears the orbit down.
        repeat(glideBlocks / 2) {
            fx.configure(depth = 0.0, rate = 0.0, center = 1000.0, sweep = 1000.0, floor = 1.0)
            fx.process(ctx)
        }

        (fx.wetCoeff > 0.0) shouldBe true

        fx.reset()

        fx.dryCoeff shouldBe 1.0
        fx.wetCoeff shouldBe 0.0
        fx.center shouldBe 1000.0

        // The next life's first setting is in force at once, breakpoint and all.
        fx.configure(depth = 0.6, rate = 2.0, center = 700.0, sweep = 300.0, floor = 1.0)

        // A tolerance, not an exact match: the law goes through `pow(x, 2)`, which is not bit-
        // guaranteed on Kotlin/JS, and what this row is about is the SNAP, not the last bit.
        abs(fx.wetCoeff - sinPow2(0.6)) shouldBeLessThanOrEqual 1e-12
        fx.center shouldBe 700.0
    }

    "below the gate the stage is the dry mix, and above it the phaser runs" {
        val src = source(4)

        val gated = run(src) { _ -> P(wet = 0.005) }

        for (i in src.indices) {
            withClue("sample $i") {
                gated[i].toRawBits() shouldBe src[i].toRawBits()
            }
        }

        val engaged = run(src) { _ -> P(wet = 0.8, rate = 5.0) }
        var worst = 0.0

        for (i in src.indices) {
            worst = maxOf(worst, abs(engaged[i] - src[i]))
        }

        worst shouldBeGreaterThan 1e-3
    }
})

/** The C4 law's dry coefficient without the floor, `cos(w*pi/2)^2`, written out. */
private fun cosPow2(w: Double): Double {
    val c = kotlin.math.cos(w * PI / 2.0)

    return c * c
}

/** The C4 law's wet coefficient, `sin(w*pi/2)^2`, written out. */
private fun sinPow2(w: Double): Double {
    val s = sin(w * PI / 2.0)

    return s * s
}
