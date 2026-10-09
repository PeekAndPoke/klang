/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_bridge.LfoShapes
import kotlin.math.abs
import kotlin.random.Random

/**
 * Modulation class guards (block-framing ledger W1-W3): the coarse hold grid is note-anchored and
 * survives window boundaries and factor crossings; the tremolo LFO is a clock. W5's row (the fused
 * Distort node renders the runtime `fusedDistort`, not the doors' Drive+Shape chain) is
 * `WaveshaperKnobsSpec`'s "the Distort node reads its shape and factor ..." row; W5's hazard is
 * `StripLawCoresSpec`'s continuity row.
 */
class ModulationClockSpec : StringSpec({

    val sampleRate = 44100
    val blockFrames = 128

    /** Writes (note-relative sample index + 1) x 0.01 — a deterministic ramp whose sample 0 is
     *  NON-zero: a zero start is indistinguishable from the uninitialized `lastValue = 0.0`,
     *  which let a bootstrap-dropped mutant pass every W1 row (review round 1). */
    class RampProbe : Ignitor {
        override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
            val end = ctx.windowEnd
            for (i in ctx.offset until end) {
                buffer[i] = (ctx.voiceElapsedFrames + (i - ctx.offset) + 1) * 0.01
            }
        }
    }

    /** Per-window value decided by the window's start sample (a block-rate step control). */
    class ElapsedStep(private val valueAt: (Int) -> Double) : Ignitor {
        override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
            val v = valueAt(ctx.voiceElapsedFrames)
            val end = ctx.windowEnd
            for (i in ctx.offset until end) {
                buffer[i] = v
            }
        }
    }

    fun createCtx() = IgniteContext(
        sampleRate = sampleRate,
        voiceDurationFrames = blockFrames * 64,
        gateEndFrame = blockFrames * 64,
        scratchBuffers = ScratchBuffers(blockFrames),
        random = Random(7),
    )

    /** Renders [ignitor] over [segments] (window lengths); returns the concatenated stream. */
    fun renderSegments(ignitor: Ignitor, segments: List<Int>, freqHz: Double = 220.0): DoubleArray {
        val ctx = createCtx()
        val total = segments.sum()
        val out = DoubleArray(total)
        val buf = AudioBuffer(blockFrames)
        var elapsed = 0

        for (seg in segments) {
            ctx.voiceElapsedFrames = elapsed
            ctx.updateOffsetAndLength(offset = 0, length = seg)
            buf.fill(0.0)
            ignitor.generate(buf, freqHz, ctx)

            for (i in 0 until seg) {
                out[elapsed + i] = buf[i]
            }
            elapsed += seg
        }

        return out
    }

    fun maxDiff(a: DoubleArray, b: DoubleArray): Double {
        var m = 0.0
        for (i in a.indices) {
            m = maxOf(m, abs(a[i] - b[i]))
        }
        return m
    }

    "W1: the coarse hold grid is note-anchored — a window boundary on the grid does not re-anchor" {
        // factor 4 (power of two): the old block-indexed bootstrap latch re-armed exactly at
        // note-relative sample `factor`, so a window STARTING there re-anchored the grid for
        // the rest of the note. Segments [4, 124, 128...] put a window start on that sample.
        fun coarse() = RampProbe().coarse(ParamIgnitor("factor", 4.0))

        val contiguous = renderSegments(coarse(), List(8) { blockFrames })
        val split = renderSegments(coarse(), listOf(4, 124) + List(7) { blockFrames })

        contiguous.any { it != 0.0 } shouldBe true
        maxDiff(a = contiguous, b = split) shouldBe 0.0
    }

    "W1: the first hold is `factor` samples, like every other hold (the 2x bootstrap quirk is gone)" {
        val out = renderSegments(RampProbe().coarse(ParamIgnitor("factor", 4.0)), listOf(blockFrames))
        val input = renderSegments(RampProbe(), listOf(blockFrames))

        for (i in 0 until 32) {
            out[i] shouldBe input[(i / 4) * 4]
        }
    }

    "W3: a factor crossing through the passthrough range keeps the S&H clock contiguous" {
        // factor 5, passthrough (0.5) for blocks 2-3, back to 5 at sample 512. The counter runs
        // through the passthrough region (every sample takes), so the resume grid anchors at
        // 512 exactly; the OLD frozen counter + stale lastValue resumed on the pre-gap grid and
        // replayed input[255] as a DC step.
        val factor = ElapsedStep { elapsed ->
            if (elapsed < 256 || elapsed >= 512) 5.0 else 0.5
        }
        val out = renderSegments(RampProbe().coarse(factor), List(8) { blockFrames })
        val input = renderSegments(RampProbe(), List(8) { blockFrames })

        // The crossing is CONTIGUOUS, not snapped: sample 256 still carries the last grid
        // cell's held value (the counter tops up one sample later), then the passthrough
        // region is an exact copy.
        out[256] shouldBe input[255]
        for (i in 258 until 512) {
            out[i] shouldBe input[i]
        }
        // Resume grid: fresh take AT 512; the counter's 0.2 residual from the passthrough
        // region places the next take at 516, then the clean 5-grid (521, ...) — exactly what
        // a never-frozen clock does. The OLD code held input[255] here as a DC step.
        for (i in 512 until 516) {
            out[i] shouldBe input[512]
        }
        for (i in 516 until 521) {
            out[i] shouldBe input[516]
        }
    }

    "W3/W4: a non-finite factor reads as passthrough and HEALS, no permanent DC latch" {
        // NaN first (caught by the !(amt > 0) NaN-guard), then +Inf (caught by the isInfinite
        // arm — without it, Inf engages with invAmt 0: one take, an eternal hold, a displaced
        // counter), then a finite heal.
        val factor = ElapsedStep { elapsed ->
            when {
                elapsed < 128 -> Double.NaN
                elapsed < 256 -> Double.POSITIVE_INFINITY
                else -> 4.0
            }
        }
        val out = renderSegments(RampProbe().coarse(factor), List(4) { blockFrames })
        val input = renderSegments(RampProbe(), List(4) { blockFrames })

        // During NaN and Inf: copy-through (the old code engaged, poisoned the counter and
        // latched a frozen value for the note's life).
        for (i in 0 until 256) {
            out[i] shouldBe input[i]
        }
        // After heal: the counter (frozen through the bypass — the defensible W3 residual) takes
        // at 256 and runs the clean 4-grid from there.
        out.all { it == it && it.isFinite() } shouldBe true
        out[300] shouldBe input[300]
        out[301] shouldBe input[300]
    }

    "W3 parity: a NaN INPUT sample does not latch into the held value" {
        // The strip door has always nanGuard()ed the captured sample; the ignitor door gained
        // it in the W-batch. One poisoned input sample must cost at most its own cell, held as
        // the guard's 0.0, never NaN for `factor` frames.
        class NaNAtProbe(private val at: Int) : Ignitor {
            override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
                val end = ctx.windowEnd
                for (i in ctx.offset until end) {
                    val n = ctx.voiceElapsedFrames + (i - ctx.offset)
                    buffer[i] = if (n == at) Double.NaN else (n + 1) * 0.01
                }
            }
        }

        val out = renderSegments(NaNAtProbe(at = 8).coarse(ParamIgnitor("factor", 4.0)), listOf(blockFrames))

        out.all { it == it } shouldBe true // no NaN anywhere in the output
    }

    "W2: the tremolo LFO advances through a depth gap — resume equals a free-running clock" {
        val rate = 4.0
        val gated = TremoloDoor.tremolo(
            upstream = RampProbe(), rate = ParamIgnitor("rate", rate),
            depth = ElapsedStep { e -> if (e < 256 || e >= 512) 0.6 else 0.0 },
        )
        val ungated = TremoloDoor.tremolo(
            upstream = RampProbe(), rate = ParamIgnitor("rate", rate), depth = ParamIgnitor("depth", 0.6),
        )

        val g = renderSegments(gated, List(8) { blockFrames })
        val u = renderSegments(ungated, List(8) { blockFrames })
        val input = renderSegments(RampProbe(), List(8) { blockFrames })

        // Reference: dry inside the gap, the free-running tremolo everywhere else. The LFO is an
        // oscillator that renders every block whatever the depth, so it cannot fall behind; the
        // gated side maps through `range`'s signal-bound path and the ungated side through its
        // constant-bound path, which round differently, so the match is to ~1e-15, not bit-exact.
        // A stale-phase LFO would diverge at O(0.05).
        var m = 0.0
        for (i in g.indices) {
            val expected = if (i in 256 until 512) input[i] else u[i]
            m = maxOf(m, abs(g[i] - expected))
        }
        (m < 1e-12) shouldBe true
    }

    "W2: a non-finite rate no longer kills the LFO for the voice's life" {
        val rate = ElapsedStep { e -> if (e < 256) Double.NaN else 5.0 }
        val out = renderSegments(
            TremoloDoor.tremolo(upstream = RampProbe(), rate = rate, depth = ParamIgnitor("depth", 0.5)),
            List(8) { blockFrames },
        )

        out.all { it == it && it.isFinite() } shouldBe true
        // The healed LFO actually modulates: the late region is not a constant-gain copy.
        val late = out.copyOfRange(768, 1024)
        val input = renderSegments(RampProbe(), List(8) { blockFrames }).copyOfRange(768, 1024)
        var varies = false
        for (i in 1 until late.size) {
            if (input[i] != 0.0 && late[i] / input[i] != late[i - 1] / input[i - 1]) {
                varies = true
            }
        }
        varies shouldBe true
    }
})

/**
 * The tremolo as the DSL runtime composes it (the sine LFO's gain multiplied into the signal), with Ignitor-level knobs,
 * so the rows can drive the depth and the rate with probes the DSL cannot spell.
 */
private object TremoloDoor {
    fun tremolo(upstream: Ignitor, rate: Ignitor, depth: Ignitor): Ignitor =
        upstream * tremoloGain(rate = rate, depth = depth, lfoShapeIndex = LfoShapes.SINE_INDEX, sampleRate = 44100)
}
