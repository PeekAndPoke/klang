/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.EnvelopeCore
import io.peekandpoke.klang.audio_be.filters.filterEnvCutoff
import io.peekandpoke.klang.audio_bridge.AdsrCurve
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.random.Random

/**
 * The filter envelope's knots (KLANG-00432, L4-02, decisions pass C): the cutoff is exact at knots that belong to the
 * NOTE and the coefficients step linearly between them (`SvfIgnitor.renderEnvelope`). Three things are pinned here:
 *
 *  - the node against an ORACLE written here, the TPT lowpass with the envelope's cutoff recomputed at every sample from
 *    the textbook coefficients (`tan`, no engine coefficient helper): the largest difference re the oracle's peak and
 *    the RMS of the difference re its RMS, under bounds set from the measurement in the ticket (the block law this
 *    replaced read -4.7 dB and -30 dB on the first case);
 *  - note-anchoring: the same note at every onset inside a block renders the same samples, bit for bit;
 *  - a gate learned while the note sounds re-aims at once: from the gate on, the note renders what it renders with the
 *    gate known from its start.
 *
 * And the two helpers the knots read: [EnvelopeCore.nextStageFrame] against a scan of the law, and [filterEnvCutoff].
 */
class FilterEnvKnotsSpec : StringSpec({

    val sr = 48000
    val block = 128

    /** A bare tree driven as `Voice.render` drives it, the onset at frame [offset] of the first block; out in NOTE frames. */
    fun renderAtOffset(
        tree: IgnitorDsl,
        freqHz: Double,
        offset: Int,
        noteFrames: Int,
        gateFrames: Int,
        gateKnownFrom: Int = 0,
    ): DoubleArray {
        val random = Random(7)
        val built = tree.buildExciter(ignitorParams = null, random = random, freqHz = freqHz, sampleRate = sr, blockFrames = block)
        val ctx = IgniteContext(sampleRate = sr, voiceDurationFrames = noteFrames, gateEndFrame = gateFrames, scratchBuffers = ScratchBuffers(block), random = random)
        val buffer = AudioBuffer(block)
        val out = DoubleArray(noteFrames + block)
        var elapsed = 0
        var first = true

        while (elapsed < noteFrames) {
            val off = if (first) offset else 0
            val len = block - off

            // A gate not known yet reads as far away (Int.MAX_VALUE), as an open realtime note does.
            ctx.gateEndFrame = if (elapsed >= gateKnownFrom) gateFrames else Int.MAX_VALUE
            ctx.voiceElapsedFrames = elapsed
            ctx.updateOffsetAndLength(offset = off, length = len)
            built.ignitor.generate(buffer, freqHz, ctx)

            for (i in 0 until len) {
                out[elapsed + i] = buffer[off + i]
            }

            elapsed += len
            first = false
        }

        return out.copyOf(noteFrames)
    }

    /** The oracle: the TPT SVF, its cutoff the envelope's at EVERY sample, its coefficients the textbook's. */
    fun exactFilter(input: DoubleArray, cutoffAt: (Int) -> Double, q: Double, highpass: Boolean): DoubleArray {
        val out = DoubleArray(input.size)
        var ic1 = 0.0
        var ic2 = 0.0

        for (i in input.indices) {
            val g = tan(PI * cutoffAt(i) / sr)
            val k = 1.0 / q
            val a1 = 1.0 / (1.0 + g * (g + k))
            val a2 = g * a1
            val a3 = g * a2
            val v0 = input[i]
            val v3 = v0 - ic2
            val v1 = a1 * ic1 + a2 * v3
            val v2 = ic2 + a2 * ic1 + a3 * v3

            ic1 = 2.0 * v1 - ic1
            ic2 = 2.0 * v2 - ic2
            out[i] = if (highpass) v0 - k * v1 - v2 else v2
        }

        return out
    }

    /** The envelope's level at a frame, the law written here: exponential stages (`MOD_ENV_CURVE`, k = 3). */
    fun oracleLevel(pos: Int, a: Double, d: Double, s: Double, r: Double, gate: Int): Double {
        val k = 3.0
        val norm = 1.0 / (kotlin.math.exp(k) - 1.0)

        fun shape(x: Double) = (kotlin.math.exp(k * x) - 1.0) * norm

        fun ads(p: Int): Double = when {
            p < a -> shape(p / a)
            p < a + d -> s + (1.0 - s) * shape(1.0 - (p - a) / d)
            else -> s
        }

        if (pos < gate) {
            return ads(pos)
        }

        val rf = kotlin.math.floor(r)
        val progress = if (rf > 1.0) ((pos - gate) / (rf - 1.0)).coerceAtMost(1.0) else 1.0

        return ads(gate) * shape(1.0 - progress)
    }

    class Case(
        val label: String,
        val freq: Double,
        val base: Double,
        val depth: Double,
        val q: Double,
        val a: Double,
        val d: Double,
        val s: Double,
        val r: Double,
        val gateSeconds: Double,
        val highpass: Boolean,
        val maxDb: Double,
        val rmsDb: Double,
    )

    fun tree(case: Case): IgnitorDsl {
        val saw = IgnitorDsl.Saw(analog = IgnitorDsl.Constant(0.0))
        val c = { v: Double -> IgnitorDsl.Constant(v) }

        return if (case.highpass) {
            IgnitorDsl.Highpass(
                inner = saw, freq = c(case.base), q = c(case.q), analog = c(0.0), env = c(case.depth),
                attack = c(case.a), decay = c(case.d), sustain = c(case.s), release = c(case.r),
            )
        } else {
            IgnitorDsl.Lowpass(
                inner = saw, freq = c(case.base), q = c(case.q), analog = c(0.0), env = c(case.depth),
                attack = c(case.a), decay = c(case.d), sustain = c(case.s), release = c(case.r),
            )
        }
    }

    // The bounds, dB re the oracle's peak and RMS, from the node as built (JVM, 2026-11-11): -64.5 and -82.2, -54.3 and
    // -58.0 (q 8: a resonant filter shows a cent's difference most), -68.0 and -85.2, -68.2 and -80.9. The block law it
    // replaced read -4.7 and -30.0 on the first case, -9.5 and -25.7 on the second (worst onset).
    val cases = listOf(
        Case("a 1 ms, 48 st, lowpass 300, q 0.707", 110.0, 300.0, 48.0, 0.707, 0.001, 0.1, 0.3, 0.05, 0.3, false, maxDb = -60.0, rmsDb = -75.0),
        Case("a 5 ms, 48 st, lowpass 300, q 8", 110.0, 300.0, 48.0, 8.0, 0.005, 0.1, 0.3, 0.05, 0.3, false, maxDb = -50.0, rmsDb = -55.0),
        Case("the defaults, 24 st, lowpass 300", 110.0, 300.0, 24.0, 0.707, 0.01, 0.1, 1.0, 0.1, 0.3, false, maxDb = -60.0, rmsDb = -80.0),
        Case("a 2 ms, -36 st, highpass 2000, q 2", 220.0, 2000.0, -36.0, 2.0, 0.002, 0.2, 0.2, 0.005, 0.25, true, maxDb = -60.0, rmsDb = -75.0),
    )

    "the node against the exact per-sample cutoff (the oracle), in bounds" {
        for (case in cases) {
            val gate = (case.gateSeconds * sr).toInt()
            val span = gate + (case.r * sr).toInt() + 2400
            val saw = IgnitorDsl.Saw(analog = IgnitorDsl.Constant(0.0))
            val source = renderAtOffset(tree = saw, freqHz = case.freq, offset = 0, noteFrames = span, gateFrames = gate)
            val node = renderAtOffset(tree = tree(case), freqHz = case.freq, offset = 0, noteFrames = span, gateFrames = gate)
            val cutoffAt = { pos: Int ->
                case.base * 2.0.pow(case.depth / 12.0 * oracleLevel(pos, case.a * sr, case.d * sr, case.s, case.r * sr, gate).coerceIn(0.0, 1.0))
            }
            val exact = exactFilter(input = source, cutoffAt = cutoffAt, q = case.q, highpass = case.highpass)
            var peak = 0.0
            var sumSq = 0.0
            var worst = 0.0
            var diffSq = 0.0

            for (i in 0 until span) {
                val e = abs(node[i] - exact[i])

                peak = max(peak, abs(exact[i]))
                sumSq += exact[i] * exact[i]
                worst = max(worst, e)
                diffSq += e * e
            }

            val maxDb = 20.0 * log10(worst / peak)
            val rmsDb = 20.0 * log10(sqrt(diffSq / span) / sqrt(sumSq / span))

            withClue("${case.label}: largest difference re the oracle's peak, dB") { maxDb shouldBeLessThan case.maxDb }
            withClue("${case.label}: RMS difference re the oracle's RMS, dB") { rmsDb shouldBeLessThan case.rmsDb }
        }
    }

    "note-anchored: the same note at every onset inside a block renders the same samples, bit for bit" {
        for (case in cases) {
            val gate = (case.gateSeconds * sr).toInt()
            val span = gate + (case.r * sr).toInt() + 2400
            val reference = renderAtOffset(tree = tree(case), freqHz = case.freq, offset = 0, noteFrames = span, gateFrames = gate)

            for (offset in 1 until block) {
                val shifted = renderAtOffset(tree = tree(case), freqHz = case.freq, offset = offset, noteFrames = span, gateFrames = gate)
                var firstDiff = -1

                for (i in 0 until span) {
                    if (shifted[i].toRawBits() != reference[i].toRawBits()) {
                        firstDiff = i
                        break
                    }
                }

                withClue("${case.label}: onset at offset $offset differs from offset 0 first at note frame $firstDiff") { firstDiff shouldBe -1 }
            }
        }
    }

    "a gate learned while the note sounds re-aims at once: from the gate on, the note is the one with the gate known" {
        for (case in cases) {
            val gate = (case.gateSeconds * sr).toInt()
            val span = gate + (case.r * sr).toInt() + 2400
            val known = renderAtOffset(tree = tree(case), freqHz = case.freq, offset = 0, noteFrames = span, gateFrames = gate)

            // The gate becomes known three blocks before it falls, and in the very block it falls in (a realtime note-off).
            // In the first case no sustain knot lies between that block's start (14336) and the gate (14400; the sustain's
            // knots fall 112 frames into a block), so only the re-aim puts one at the gate.
            for (learnedAt in listOf((gate / block - 3) * block, (gate / block) * block)) {
                val late = renderAtOffset(tree = tree(case), freqHz = case.freq, offset = 0, noteFrames = span, gateFrames = gate, gateKnownFrom = learnedAt)
                var worstAfter = 0.0
                var peak = 0.0

                for (i in 0 until span) {
                    peak = max(peak, abs(known[i]))

                    if (i >= gate) {
                        worstAfter = max(worstAfter, abs(late[i] - known[i]))
                    }
                }

                // The release must start at the gate: a node that kept the sustain's knot would hold the sustain cutoff on.
                withClue("${case.label}, gate learned at frame $learnedAt: after the gate, re the peak") { (worstAfter / peak) shouldBeLessThan 1e-3 }
            }
        }
    }

    "nextStageFrame: the first frame after a position where the law's stage changes, against a scan" {
        val core = EnvelopeCore()

        // Fractional attack and decay, a gate inside the sustain, then a release of 50 frames.
        core.prepare(
            attackFrames = 44.1, decayFrames = 100.5, sustainLevel = 0.4, releaseFrames = 50.0, gateEndPos = 400,
            attackCurve = AdsrCurve.Linear, decayCurve = AdsrCurve.Linear, releaseCurve = AdsrCurve.Linear,
        )

        // The law's stage frames: the first decay frame 45 (44 < 44.1), the first sustain frame 145 (144.6), the gate 400,
        // and the release's end 449 (p reaches 1 on relPos 49, the release of 50 frames dividing by 49).
        val stages = listOf(45, 145, 400, 449)

        for (pos in 0 until 600) {
            val expected = stages.firstOrNull { it > pos } ?: Int.MAX_VALUE

            withClue("pos $pos") { core.nextStageFrame(pos) shouldBe expected }
        }

        // From the release's end on the level holds: equal at every later frame.
        core.at(449) shouldBe core.at(10_000)

        // A gate before the attack ends: the gate comes first.
        core.prepare(
            attackFrames = 480.0, decayFrames = 100.0, sustainLevel = 0.4, releaseFrames = 20.0, gateEndPos = 100,
            attackCurve = AdsrCurve.Linear, decayCurve = AdsrCurve.Linear, releaseCurve = AdsrCurve.Linear,
        )

        core.nextStageFrame(0) shouldBe 100
        core.nextStageFrame(100) shouldBe 119
        core.nextStageFrame(119) shouldBe 480
    }

    "filterEnvCutoff: base * 2^(depth/12 * level), the level clamped to [0, 1]" {
        // A linear attack of 100 frames into a RAW sustain of 1.5 (the core does not clamp it).
        val core = EnvelopeCore()

        core.prepare(
            attackFrames = 100.0, decayFrames = 100.0, sustainLevel = 1.5, releaseFrames = 0.0, gateEndPos = 100_000,
            attackCurve = AdsrCurve.Linear, decayCurve = AdsrCurve.Linear, releaseCurve = AdsrCurve.Linear,
        )

        // Frame 25 of the attack: level 0.25.
        core.filterEnvCutoff(pos = 25, baseCutoff = 600.0, depthSemitones = 24.0) shouldBe (600.0 * 2.0.pow(24.0 / 12.0 * 0.25) plusOrMinus 1e-9)
        // The sustain: level 1.5, clamped to 1.
        core.filterEnvCutoff(pos = 5000, baseCutoff = 600.0, depthSemitones = 24.0) shouldBe (600.0 * 4.0 plusOrMinus 1e-9)
        // A negative depth sweeps down.
        core.filterEnvCutoff(pos = 5000, baseCutoff = 600.0, depthSemitones = -12.0) shouldBe (300.0 plusOrMinus 1e-9)
    }
})
