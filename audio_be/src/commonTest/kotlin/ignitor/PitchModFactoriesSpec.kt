/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import kotlin.math.abs
import kotlin.math.sqrt

class PitchModFactoriesSpec : StringSpec({

    val sampleRate = 44100
    val blockFrames = 4410 // 100ms — long enough to capture LFO cycles

    fun createCtx(frames: Int = blockFrames): IgniteContext = IgniteContext(
        sampleRate = sampleRate,
        voiceDurationFrames = sampleRate, // 1 second
        gateEndFrame = sampleRate,
        releaseFrames = 0,
        scratchBuffers = ScratchBuffers(frames),
    ).apply {
        offset = 0
        length = frames
        voiceElapsedFrames = 0
    }

    fun render(ig: Ignitor, freqHz: Double = 440.0, ctx: IgniteContext = createCtx()): AudioBuffer {
        val buf = AudioBuffer(ctx.length)
        ig.generate(buf, freqHz, ctx)
        return buf
    }

    fun AudioBuffer.mean(): Double = sumOf { it } / size
    fun AudioBuffer.rms(): Double {
        var s = 0.0; for (x in this) s += x * x; return sqrt(s / size)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Vibrato mod
    // ═══════════════════════════════════════════════════════════════════════════

    "vibratoMod: depth=0 produces all ones (no modulation)" {
        val mod = vibratoModIgnitor(rate = 5.0, semitones = 0.0)
        val out = render(mod)
        for (s in out) s shouldBe 1.0
    }

    "vibratoMod: output is centered near 1.0 (ratio space)" {
        val mod = vibratoModIgnitor(rate = 10.0, semitones = 1.0)
        // Use a full second to average over many complete LFO cycles
        val ctx = createCtx(sampleRate)
        val out = render(mod, ctx = ctx)
        // Mean should be near 1.0 (symmetric LFO over complete cycles, ratio space)
        abs(out.mean() - 1.0) shouldBeLessThan 0.01
    }

    "vibratoMod: output has RMS > 1.0 (actual modulation above unity)" {
        val mod = vibratoModIgnitor(rate = 5.0, semitones = 1.0)
        val out = render(mod)
        out.rms() shouldBeGreaterThan 0.9
    }

    "vibratoMod: larger depth produces larger deviation from 1.0" {
        val small = vibratoModIgnitor(rate = 5.0, semitones = 0.25)
        val large = vibratoModIgnitor(rate = 5.0, semitones = 2.0)
        fun deviationRms(buf: AudioBuffer): Double {
            var s = 0.0; for (x in buf) {
                val d = x - 1.0; s += d * d
            }; return sqrt(s / buf.size)
        }
        deviationRms(render(large)) shouldBeGreaterThan deviationRms(render(small)) * 2.0
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Accelerate mod
    // ═══════════════════════════════════════════════════════════════════════════

    "accelerateMod: amount=0 produces all ones" {
        val mod = accelerateModIgnitor(semitones = 0.0)
        val out = render(mod)
        for (s in out) s shouldBe 1.0
    }

    "accelerateMod: starts near 1.0 at voice start (progress=0)" {
        val mod = accelerateModIgnitor(semitones = 2.0)
        val ctx = createCtx()
        ctx.voiceElapsedFrames = 0
        val out = render(mod, ctx = ctx)
        // First sample: 2^(2.0 * 0/44100) ≈ 1.0
        out[0] shouldBe (1.0 plusOrMinus 0.001)
    }

    "accelerateMod: positive semitones produce an increasing ratio (semitone law)" {
        // 24 semitones over the voice: at progress=0.5 the ratio is 2^((24/12)·0.5) = 2.0
        val mod = accelerateModIgnitor(semitones = 24.0)
        val ctx = createCtx()
        ctx.voiceElapsedFrames = sampleRate / 2 // halfway
        val out = render(mod, ctx = ctx)
        out[0] shouldBe (2.0 plusOrMinus 0.01)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Pitch envelope mod
    // ═══════════════════════════════════════════════════════════════════════════

    "pitchEnvelopeMod: amount=0 produces all ones" {
        val mod = pitchEnvelopeModIgnitor(
            attackSec = ParamIgnitor("a", 0.1),
            decaySec = ParamIgnitor("d", 0.1),
            semitones = ParamIgnitor("amount", 0.0),
        )
        val out = render(mod)
        for (s in out) s shouldBe 1.0
    }

    "pitchEnvelopeMod: produces non-zero deviation when amount is non-zero" {
        val mod = pitchEnvelopeModIgnitor(
            attackSec = ParamIgnitor("a", 0.01),
            decaySec = ParamIgnitor("d", 0.05),
            semitones = ParamIgnitor("amount", 12.0), // one octave
        )
        val out = render(mod)
        out.rms() shouldBeGreaterThan 0.01
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // FM mod
    // ═══════════════════════════════════════════════════════════════════════════

    "fmMod: depth=0 produces all ones" {
        val mod = fmModIgnitor(
            modulator = Ignitors.sine(),
            ratio = ParamIgnitor("ratio", 2.0),
            depth = ParamIgnitor("depth", 0.0),
        )
        val out = render(mod)
        for (s in out) s shouldBe 1.0
    }

    "fmMod: output is centered near 1.0 (ratio space)" {
        val mod = fmModIgnitor(
            modulator = Ignitors.sine(),
            ratio = ParamIgnitor("ratio", 2.0),
            depth = ParamIgnitor("depth", 200.0),
        )
        val out = render(mod)
        // Sine modulator is symmetric → ratio mean near 1.0
        abs(out.mean() - 1.0) shouldBeLessThan 0.1
    }

    "fmMod: larger depth produces larger deviation from 1.0" {
        val small = fmModIgnitor(
            modulator = Ignitors.sine(),
            ratio = ParamIgnitor("ratio", 2.0),
            depth = ParamIgnitor("depth", 50.0),
        )
        val large = fmModIgnitor(
            modulator = Ignitors.sine(),
            ratio = ParamIgnitor("ratio", 2.0),
            depth = ParamIgnitor("depth", 400.0),
        )

        fun deviationRms(buf: AudioBuffer): Double {
            var s = 0.0; for (x in buf) {
                val d = x - 1.0; s += d * d
            }; return sqrt(s / buf.size)
        }
        deviationRms(render(large)) shouldBeGreaterThan deviationRms(render(small)) * 2.0
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Deviation space contract: all mods output values near 0
    // ═══════════════════════════════════════════════════════════════════════════

    "all mods: output values are valid phase ratios (near 1.0)" {
        val mods = listOf(
            vibratoModIgnitor(rate = 5.0, semitones = 0.5),
            accelerateModIgnitor(semitones = 1.0),
        )
        for (mod in mods) {
            val out = render(mod)
            for (s in out) {
                s shouldBeGreaterThan 0.5
                s shouldBeLessThan 2.0
            }
        }
    }

    // ── Block-framing ledger E2: state advances once per rendered sample, whatever the output ──

    /** 300 except absPos in [128, 256) — one exactly block-aligned zero window at blockFrames 128. */
    val gapDepth = object : Ignitor {
        override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
            for (i in ctx.offset until ctx.offset + ctx.length) {
                val abs = ctx.voiceElapsedFrames + (i - ctx.offset)
                buffer[i] = if (abs in 128..255) 0.0 else 300.0
            }
        }
    }

    /** Renders [ig] over [totalFrames] in 128-frame blocks and returns the whole ratio signal. */
    fun renderBlocks(ig: Ignitor, totalFrames: Int): DoubleArray {
        val ctx = IgniteContext(
            sampleRate = sampleRate,
            voiceDurationFrames = totalFrames * 4,
            gateEndFrame = totalFrames * 4,   // gate far away: the env release never engages
            releaseFrames = 0,
            scratchBuffers = ScratchBuffers(128),
        )
        val out = DoubleArray(totalFrames)
        val tmp = AudioBuffer(128)
        var pos = 0
        while (pos < totalFrames) {
            ctx.offset = 0
            ctx.length = 128
            ctx.voiceElapsedFrames = pos
            ig.generate(tmp, 220.0, ctx)
            for (i in 0 until 128) out[pos + i] = tmp[i]
            pos += 128
        }
        return out
    }

    "fm: the modulator's phase survives a depth gap (ledger E2)" {
        // depth reads 0 for exactly the block [128, 256), so the OLD code skipped
        // modulator.generate there and the sine resumed 128 frames stale. After the gap the
        // gapped and the constant-depth renders must agree EXACTLY: same modulator phase, same
        // depth, same math.
        val gapped = renderBlocks(
            fmModIgnitor(Ignitors.sine(), ConstantIgnitor(1.4), gapDepth), totalFrames = 512,
        )
        val constant = renderBlocks(
            fmModIgnitor(Ignitors.sine(), ConstantIgnitor(1.4), ConstantIgnitor(300.0)), totalFrames = 512,
        )
        for (i in 128 until 256) gapped[i] shouldBe 1.0     // the gap itself outputs unity
        var m = 0.0
        for (i in 256 until 512) m = maxOf(m, abs(gapped[i] - constant[i]))
        m shouldBe 0.0
    }

    "vibrato: the LFO's phase survives a depth gap (ledger E2)" {
        val gapSemitones = object : Ignitor {
            override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
                for (i in ctx.offset until ctx.offset + ctx.length) {
                    val abs = ctx.voiceElapsedFrames + (i - ctx.offset)
                    buffer[i] = if (abs in 128..255) 0.0 else 0.5
                }
            }
        }
        val gapped = renderBlocks(
            vibratoModIgnitor(ConstantIgnitor(8.0), gapSemitones), totalFrames = 512,
        )
        val constant = renderBlocks(
            vibratoModIgnitor(ConstantIgnitor(8.0), ConstantIgnitor(0.5)), totalFrames = 512,
        )
        for (i in 128 until 256) gapped[i] shouldBe 1.0
        var m = 0.0
        for (i in 256 until 512) m = maxOf(m, abs(gapped[i] - constant[i]))
        m shouldBe 0.0
    }

    "fm env: a nonzero release RAMPS the depth at gate end instead of stepping it (ledger E10)" {
        // With release = 0 the depth collapses in one sample at gate end: a hard frequency step
        // that ticks on every note-off (raw semantics, kept). A nonzero release must glide.
        // Comparative on purpose: if the engine lost its release-ramp handling, both renders
        // collapse identically and the ratio below hits 1.0 -> red.
        val gate = 6000
        fun renderAcrossGate(releaseSec: Double): DoubleArray {
            val total = gate + 2400
            val ig = fmModIgnitor(
                Ignitors.sine(), ConstantIgnitor(1.4), ConstantIgnitor(300.0),
                envAttackSec = ConstantIgnitor(0.001),
                envDecaySec = ConstantIgnitor(0.5),
                envSustainLevel = ConstantIgnitor(0.0),
                envReleaseSec = ConstantIgnitor(releaseSec),
            )
            val ctx = IgniteContext(
                sampleRate = sampleRate, voiceDurationFrames = gate, gateEndFrame = gate,
                releaseFrames = 2400,  scratchBuffers = ScratchBuffers(128),
            )
            val out = DoubleArray(total)
            val tmp = AudioBuffer(128)
            var pos = 0
            while (pos < total) {
                val n = minOf(128, total - pos)
                ctx.offset = 0
                ctx.length = n
                ctx.voiceElapsedFrames = pos
                ig.generate(tmp, 523.25, ctx)
                for (i in 0 until n) out[pos + i] = tmp[i]
                pos += n
            }
            return out
        }

        val steppedRender = renderAcrossGate(0.0)
        val rampedRender = renderAcrossGate(0.05)
        // PHASE-FREE formulation (round-2 audio review): with release = 0 the depth is exactly 0
        // from the sample after gate end, so the ratio output is EXACTLY 1.0 there — regardless of
        // where the modulator's phase sat at the collapse. With a release, the depth is still
        // ~half at gate end, so the ratio keeps swinging hard through the same window. Two exact
        // facts, no thresholds riding on modulator phase (a d2-based form failed for ~6% of gate
        // positions when the modulator crossed zero at gate end).
        fun maxDev(x: DoubleArray, from: Int, until: Int): Double {
            var m = 0.0
            for (i in from until until) m = maxOf(m, abs(x[i] - 1.0))
            return m
        }
        maxDev(steppedRender, gate + 2, gate + 100) shouldBe 0.0          // the raw hard cut: 0 means 0
        (maxDev(rampedRender, gate + 2, gate + 100) > 0.3) shouldBe true  // a release keeps the depth alive
    }

    "fm env: a RELEASE-ONLY envelope is honoured, not silently dropped (hasEnv counts release)" {
        // attack 0 / decay 0 / sustain 1 / release 0.05: the exact E10 remedy shape on a sound
        // with no other envelope shaping. A hasEnv gate that ignores envReleaseSec treats this as
        // "no envelope" and drops the release silently — the depth then holds FULL through the
        // whole tail. During the gate a release-only envelope is exactly 1.0, so both renders
        // below must be bit-identical up to the gate; after it they must diverge hard.
        val gate = 6000
        val rel = 2400
        fun render(releaseSec: Double): DoubleArray {
            val total = gate + rel
            val ig = fmModIgnitor(
                Ignitors.sine(), ConstantIgnitor(1.4), ConstantIgnitor(300.0),
                envAttackSec = ConstantIgnitor(0.0),
                envDecaySec = ConstantIgnitor(0.0),
                envSustainLevel = ConstantIgnitor(1.0),
                envReleaseSec = ConstantIgnitor(releaseSec),
            )
            val ctx = IgniteContext(
                sampleRate = sampleRate, voiceDurationFrames = gate, gateEndFrame = gate,
                releaseFrames = rel,  scratchBuffers = ScratchBuffers(128),
            )
            val out = DoubleArray(total)
            val tmp = AudioBuffer(128)
            var pos = 0
            while (pos < total) {
                val n = minOf(128, total - pos)
                ctx.offset = 0
                ctx.length = n
                ctx.voiceElapsedFrames = pos
                ig.generate(tmp, 523.25, ctx)
                for (i in 0 until n) out[pos + i] = tmp[i]
                pos += n
            }
            return out
        }

        val relOnly = render(0.05)
        val noEnv = render(0.0)

        var preGate = 0.0
        for (i in 0 until gate) preGate = maxOf(preGate, abs(relOnly[i] - noEnv[i]))
        preGate shouldBe 0.0                                 // env == 1.0 exactly during the gate

        fun maxDev(x: DoubleArray, from: Int, until: Int): Double {
            var m = 0.0
            for (i in from until until) m = maxOf(m, abs(x[i] - 1.0))
            return m
        }
        // Deep in the release the ramped depth is ~gone while the dropped-release depth still
        // swings at full modulation index.
        (maxDev(relOnly, gate + 2200, gate + 2395) < 0.02) shouldBe true
        (maxDev(noEnv, gate + 2200, gate + 2395) > 0.3) shouldBe true
    }
})
