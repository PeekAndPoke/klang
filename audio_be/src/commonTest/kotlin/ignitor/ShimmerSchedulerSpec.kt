/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import kotlin.math.abs
import kotlin.math.sin

/**
 * Guards ledger D6/D7: the shimmer's bypass clear must take the grain SCHEDULER with it
 * (countdown + pitch round-robin), and a zero-length window must not get to decide a bypass.
 * Proof shape as in PhaserClockSpec: a wet gap must resume bit-identically to a first engagement
 * at the same note position — any framing memory in the scheduler breaks that equality.
 */
class ShimmerSchedulerSpec : StringSpec({

    val sampleRate = 44100
    val blockFrames = 128

    fun ctx() = IgniteContext(
        sampleRate = sampleRate, voiceDurationFrames = 200_000, gateEndFrame = 200_000,
        releaseFrames = 0,  scratchBuffers = ScratchBuffers(blockFrames),
    )

    class TestTone : Ignitor {
        override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
            for (i in 0 until ctx.length) {
                buffer[ctx.offset + i] = sin(0.07 * (ctx.voiceElapsedFrames + i))
            }
        }
    }

    class BlockValue(var value: Double) : Ignitor {
        override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
            for (i in 0 until ctx.length) {
                buffer[ctx.offset + i] = value
            }
        }
    }

    // Every-block tick counter — used as dryFloor so both runs of a comparison read it
    // UNCONDITIONALLY (the D1 rule for the fourth slot, review round 5 pinned it): under the
    // read-only-when-engaged mutation the runs\' counters diverge and the equalities break.
    class Tick : Ignitor {
        var n = 0.0
        override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
            n += 0.05
            for (i in 0 until ctx.length) {
                buffer[ctx.offset + i] = n
            }
        }
    }

    fun shimmer(wet: Ignitor) = TestTone().shimmer(
        wet = wet,
        feedback = ParamIgnitor("feedback", 0.5),
        tone = ParamIgnitor("tone", 4000.0),
        dryFloor = Tick(),
    )

    fun render(ig: Ignitor, c: IgniteContext, pos: Int, len: Int): DoubleArray {
        val tmp = AudioBuffer(blockFrames)
        c.updateOffsetAndLength(0, len)
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

    "a wet gap resumes exactly like a first engagement — scheduler carries no framing memory" {
        // Gap run: engaged blocks 0..3 (one grain fired, countdown mid-flight, round-robin at 1),
        // bypassed 4..9, engaged again from 10.
        val gapWet = BlockValue(1.0)
        val gapIg = shimmer(gapWet)
        val gapCtx = ctx()

        // Control run: first engagement at block 10.
        val ctlWet = BlockValue(0.0)
        val ctlIg = shimmer(ctlWet)
        val ctlCtx = ctx()

        // The window must reach the AUDIBLE wet region: a grain looks back 6615 samples into the
        // ring, and after a clear that region is zeros — every grain is silent until the write
        // head has refilled it, so the first NONZERO wet appears ~5.6k samples (~44 blocks) after
        // re-entry. A window that ends before that compares dry against dry and pins nothing
        // (the first formulation of this guard did exactly that — caught by its own mutation
        // check, the O1 lesson repeating).
        var m = 0.0

        for (block in 0 until 90) {
            gapWet.value = if (block < 4 || block >= 10) 1.0 else 0.0
            ctlWet.value = if (block >= 10) 1.0 else 0.0

            val g = render(gapIg, gapCtx, block * blockFrames, blockFrames)
            val c = render(ctlIg, ctlCtx, block * blockFrames, blockFrames)

            if (block >= 10) {
                m = maxOf(m, maxDiff(g, c))
            }
        }

        m shouldBe 0.0
    }

    "the param read order is pinned — wet, feedback, tone, dryFloor" {
        // Same contract as the phaser's order pin: a shared stateful instance deals its draws in
        // read order, observable on hand-built Kotlin graphs. (Pins wet vs feedback —
        // representative; tone/dryFloor permutations are not separately pinned.) The instance
        // ALTERNATES 0.3/0.6 per
        // draw (two draws per block), so the per-block assignment is stable: wet always gets 0.3
        // and feedback always gets 0.6 — unless the read order flips. In-range values so no
        // coercion masks the assignment.
        class Alternator : Ignitor {
            private var next = 0.3
            override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
                val v = next
                next = if (v == 0.3) 0.6 else 0.3

                for (i in 0 until ctx.length) {
                    buffer[ctx.offset + i] = v
                }
            }
        }

        val shared = Alternator()
        val sharedIg = TestTone().shimmer(
            wet = shared,
            feedback = shared,
            tone = ParamIgnitor("tone", 4000.0),
        )
        val sharedCtx = ctx()

        val refIg = TestTone().shimmer(
            wet = ParamIgnitor("wet", 0.3),
            feedback = ParamIgnitor("feedback", 0.6),
            tone = ParamIgnitor("tone", 4000.0),
        )
        val refCtx = ctx()

        // Far enough to reach the audible-wet region, where feedback and wet both shape output.
        var m = 0.0

        for (block in 0 until 70) {
            val a = render(sharedIg, sharedCtx, block * blockFrames, blockFrames)
            val b = render(refIg, refCtx, block * blockFrames, blockFrames)

            if (block >= 60) {
                m = maxOf(m, maxDiff(a, b))
            }
        }

        m shouldBe 0.0
    }

    "a zero-length window does not get to decide the bypass (ledger D7)" {
        val refIg = shimmer(BlockValue(1.0))
        val refCtx = ctx()

        val probeIg = shimmer(BlockValue(1.0))
        val probeCtx = ctx()

        var m = 0.0

        // The zero-length probe sits at block 65 — inside the truly AUDIBLE wet region. In a
        // continuous run the first hearable wet arrives only when the fifth-pitched grain's
        // lookback wraps the ring seam into written content (~block 61); before that every grain
        // plays zeros and a wipe is invisible (this probe sat at block 45 first and pinned
        // nothing — its own mutation check said so).
        for (block in 0 until 82) {
            val r = render(refIg, refCtx, block * blockFrames, blockFrames)

            if (block == 65) {
                // A zero-length window: blockStartValue reads the modulated wet as 0.0 there —
                // the false bypass must not wipe the cloud.
                render(probeIg, probeCtx, block * blockFrames, 0)
            }
            val p = render(probeIg, probeCtx, block * blockFrames, blockFrames)

            if (block >= 65) {
                m = maxOf(m, maxDiff(r, p))
            }
        }

        m shouldBe 0.0
    }
})
