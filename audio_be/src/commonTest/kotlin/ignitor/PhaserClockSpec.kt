/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.effects.Phaser
import kotlin.math.abs
import kotlin.math.sin

/**
 * Guards ledger D1/D2/D5/D7: the phaser LFO is a CLOCK — it advances with note-relative time on
 * every door, bypassed or not — and a bypass hands over a CLEAN cascade instead of freezing a
 * stale one. The proof shape used throughout: a run whose wet/depth dips through a gap must be
 * bit-identical, from the re-entry block on, to a run that first engages at the re-entry point —
 * that equality holds only if (a) the LFO advanced through the gap in both runs and (b) the gap
 * run dropped its pre-gap cascade state on bypass entry.
 */
class PhaserClockSpec : StringSpec({

    val sampleRate = 44100
    val blockFrames = 128

    fun ctx() = IgniteContext(
        sampleRate = sampleRate, voiceDurationFrames = 100_000, gateEndFrame = 100_000,
        releaseFrames = 0,  scratchBuffers = ScratchBuffers(blockFrames),
    )

    // Deterministic, stateless tone — depends only on the note-relative sample position.
    class TestTone : Ignitor {
        override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
            for (i in 0 until ctx.length) {
                buffer[ctx.offset + i] = sin(0.07 * (ctx.voiceElapsedFrames + i))
            }
        }
    }

    // Control-rate param the test flips between blocks.
    class BlockValue(var value: Double) : Ignitor {
        override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
            for (i in 0 until ctx.length) {
                buffer[ctx.offset + i] = value
            }
        }
    }

    fun render(ig: Ignitor, c: IgniteContext, pos: Int, len: Int): DoubleArray {
        val tmp = AudioBuffer(blockFrames)
        c.offset = 0
        c.length = len
        c.voiceElapsedFrames = pos
        ig.generate(tmp, 220.0, c)
        return tmp.copyOf(maxOf(len, 1))
    }

    fun maxDiff(a: DoubleArray, b: DoubleArray): Double {
        var m = 0.0
        for (i in a.indices) {
            m = maxOf(m, abs(a[i] - b[i]))
        }
        return m
    }

    "ignitor door: a wet gap resumes exactly like a first engagement — LFO advanced, cascade clean" {
        // Every-block tick counter as dryFloor: both runs read it UNCONDITIONALLY (the D1 rule for
        // the fourth slot, review round 5 pinned it) — under the read-only-when-engaged mutation
        // the two runs\' counters diverge and the equality below breaks.
        class Tick : Ignitor {
            var n = 0.0
            override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
                n += 0.05
                for (i in 0 until ctx.length) {
                    buffer[ctx.offset + i] = n
                }
            }
        }

        // Gap run: wet 1 for blocks 0..3, 0 for 4..9, 1 again from 10.
        val gapWet = BlockValue(1.0)
        val gapIg = TestTone().phaser(rate = ParamIgnitor("rate", 1.5), wet = gapWet, dryFloor = Tick())
        val gapCtx = ctx()

        // Control run: never engaged before block 10.
        val ctlWet = BlockValue(0.0)
        val ctlIg = TestTone().phaser(rate = ParamIgnitor("rate", 1.5), wet = ctlWet, dryFloor = Tick())
        val ctlCtx = ctx()

        var gapBlock10 = DoubleArray(0)
        var ctlBlock10 = DoubleArray(0)
        var gapBlock11 = DoubleArray(0)
        var ctlBlock11 = DoubleArray(0)

        for (block in 0 until 12) {
            gapWet.value = if (block < 4 || block >= 10) 1.0 else 0.0
            ctlWet.value = if (block >= 10) 1.0 else 0.0

            val g = render(gapIg, gapCtx, block * blockFrames, blockFrames)
            val c = render(ctlIg, ctlCtx, block * blockFrames, blockFrames)

            if (block == 10) {
                gapBlock10 = g
                ctlBlock10 = c
            }

            if (block == 11) {
                gapBlock11 = g
                ctlBlock11 = c
            }
        }

        // Identical from the FIRST re-entry sample: a frozen LFO would displace the notch
        // (ledger D1), stale allpass state would transient right here (ledger D5).
        maxDiff(gapBlock10, ctlBlock10) shouldBe 0.0
        maxDiff(gapBlock11, ctlBlock11) shouldBe 0.0
    }

    "ignitor door: a zero-length window does not get to decide the bypass (ledger D7)" {
        // Reference: continuous rendering, wet 1 throughout.
        val refIg = TestTone().phaser(rate = ParamIgnitor("rate", 1.5), wet = BlockValue(1.0))
        val refCtx = ctx()

        // Probe: same, but a ZERO-LENGTH window between blocks 5 and 6. blockStartValue reads a
        // modulated wet as 0.0 there (the deterministic E5 value) — the false bypass must not
        // wipe the cascade.
        val probeIg = TestTone().phaser(rate = ParamIgnitor("rate", 1.5), wet = BlockValue(1.0))
        val probeCtx = ctx()

        var refBlock6 = DoubleArray(0)
        var probeBlock6 = DoubleArray(0)

        for (block in 0 until 7) {
            val r = render(refIg, refCtx, block * blockFrames, blockFrames)

            if (block == 6) {
                render(probeIg, probeCtx, block * blockFrames, 0)
            }
            val p = render(probeIg, probeCtx, block * blockFrames, blockFrames)

            if (block == 6) {
                refBlock6 = r
                probeBlock6 = p
            }
        }

        maxDiff(refBlock6, probeBlock6) shouldBe 0.0
    }

    "bus door: a depth gap resumes exactly like a first engagement — LFO advanced, cascade clean" {
        fun tone(block: Int, i: Int) = sin(0.07 * (block * blockFrames + i))

        fun fill(buf: StereoBuffer, block: Int, silent: Boolean) {
            for (i in 0 until blockFrames) {
                val v = if (silent) 0.0 else tone(block, i)
                buf.left[i] = v
                buf.right[i] = v
            }
        }

        fun phaser() = Phaser(sampleRate).apply {
            rate = 1.5
            depth = 0.0
            center = 1000.0
            sweep = 1000.0
            feedback = 0.5
        }

        // Gap run: engaged blocks 0..3, gated 4..9 (with signal flowing!), engaged from 10.
        val gap = phaser()
        // Control run: never engaged before block 10 (fed silence so its cascade stays zero).
        val ctl = phaser()

        var diff = -1.0

        for (block in 0 until 12) {
            gap.depth = if (block < 4 || block >= 10) 0.8 else 0.0
            ctl.depth = if (block >= 10) 0.8 else 0.0

            val gapBuf = StereoBuffer(blockFrames)
            fill(gapBuf, block, silent = false)
            gap.process(gapBuf, blockFrames)

            val ctlBuf = StereoBuffer(blockFrames)
            fill(ctlBuf, block, silent = block < 10)
            ctl.process(ctlBuf, blockFrames)

            if (block == 10) {
                var m = 0.0
                for (i in 0 until blockFrames) {
                    m = maxOf(m, abs(gapBuf.left[i] - ctlBuf.left[i]), abs(gapBuf.right[i] - ctlBuf.right[i]))
                }
                diff = m
            }
        }

        diff shouldBe 0.0
    }

    "ignitor door: the param read order is pinned — wet before the kernel params" {
        // A single stateful instance wired into TWO slots gets its per-block draws dealt in READ
        // order. Hand-built Kotlin graphs may share bare instances (the DSL door memoizes shared
        // nodes and never observes the order), so the pre-D1 order (wet, rate, center, sweep,
        // dryFloor) is contract: here the shared counter must deal 1.0 to wet and 2.0 to rate.
        // (This pins wet vs the kernel group — representative; center/sweep/dryFloor permutations
        // among themselves are not separately pinned.)
        class Counter : Ignitor {
            var n = 0.0
            override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
                n += 1.0
                for (i in 0 until ctx.length) {
                    buffer[ctx.offset + i] = n
                }
            }
        }

        val shared = Counter()
        val sharedIg = TestTone().phaser(rate = shared, wet = shared)
        val sharedCtx = ctx()

        val refIg = TestTone().phaser(rate = ParamIgnitor("rate", 2.0), wet = ParamIgnitor("wet", 1.0))
        val refCtx = ctx()

        val s0 = render(sharedIg, sharedCtx, 0, blockFrames)
        val r0 = render(refIg, refCtx, 0, blockFrames)

        maxDiff(s0, r0) shouldBe 0.0
    }

    "bus door: a rate-0 static notch still clears its cascade when gated" {
        // The fast path skips everything for phaser-less orbits, and its !engaged term is what
        // keeps it from skipping the gate-close CLEAR of an engaged static notch (rate = 0.0 is
        // legal and documented: a frozen notch). Review round 3 found the term unkilled.
        fun tone(i: Int) = sin(0.11 * i)

        fun charged() = Phaser(sampleRate).apply {
            rate = 0.0
            depth = 0.8
            center = 1000.0
            sweep = 1000.0
            feedback = 0.5
        }

        fun buf(): StereoBuffer {
            val b = StereoBuffer(blockFrames)
            for (i in 0 until blockFrames) {
                b.left[i] = tone(i)
                b.right[i] = tone(i)
            }
            return b
        }

        val gap = charged()

        repeat(3) { gap.process(buf(), blockFrames) }

        // Gate closes (a no-phaser owner): the engaged static notch MUST clear its cascade here.
        gap.depth = 0.0
        gap.process(buf(), blockFrames)

        // Re-engage and compare against a fresh phaser: identical only if the clear ran.
        gap.depth = 0.8
        val gapOut = buf()
        gap.process(gapOut, blockFrames)

        val fresh = charged()
        val freshOut = buf()
        fresh.process(freshOut, blockFrames)

        var m = 0.0
        for (i in 0 until blockFrames) {
            m = maxOf(m, abs(gapOut.left[i] - freshOut.left[i]), abs(gapOut.right[i] - freshOut.right[i]))
        }
        m shouldBe 0.0
    }

    "bus door: reset clears the cascade — silence renders exact zeros afterwards" {
        val phaser = Phaser(sampleRate).apply {
            rate = 1.5
            depth = 1.0
            feedback = 0.5
        }

        // Charge the cascade.
        val hot = StereoBuffer(blockFrames)
        hot.fill(0.8)
        phaser.process(hot, blockFrames)

        phaser.reset()

        // A clean cascade maps silence to exactly silence (depth 1, floor 1: out = dry + wet).
        val cold = StereoBuffer(blockFrames)
        phaser.process(cold, blockFrames)

        var residue = 0.0
        for (i in 0 until blockFrames) {
            residue = maxOf(residue, abs(cold.left[i]), abs(cold.right[i]))
        }

        residue shouldBe 0.0
    }
})
