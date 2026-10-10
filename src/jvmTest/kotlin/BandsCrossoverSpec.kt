/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.cylinders.katalyst.KatalystChainBuilder
import io.peekandpoke.klang.audio_be.cylinders.katalyst.KatalystContext
import io.peekandpoke.klang.audio_be.ignitor.IgniteContext
import io.peekandpoke.klang.audio_be.ignitor.ScratchBuffers
import io.peekandpoke.klang.audio_be.ignitor.buildExciter
import io.peekandpoke.klang.audio_be.warehouse.ReverbUnits
import io.peekandpoke.klang.audio_be.warehouse.SizedBuffers
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.KatalystDsl
import io.peekandpoke.klang.audio_bridge.mul
import io.peekandpoke.klang.script.stdlib.KatalystBandsBuilder
import io.peekandpoke.klang.script.stdlib.KatalystBuilder
import io.peekandpoke.klang.script.stdlib.KlangScriptIgnitorExtensions
import io.peekandpoke.klang.script.stdlib.band
import io.peekandpoke.klang.script.stdlib.bands
import io.peekandpoke.klang.script.stdlib.cut
import io.peekandpoke.klang.script.stdlib.gain
import io.peekandpoke.klang.script.stdlib.tap
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * `bands` (`docs/tasks-archive/2026-10/20261010-parallel-serial-bands.md` step 4), rendered on both hosts: the Linkwitz-Riley split
 * of untouched bands sums to FLAT level (an all-pass), with three or more bands too (the all-pass alignment), and a
 * processed band acts on its range only.
 *
 * **The oracle is the input's own level**: a steady sine through an all-pass keeps its amplitude exactly, whatever the
 * phase does, so every row fits the output's amplitude at the sine's frequency and compares it with the input's, 1. The
 * control row shows the same three bands WITHOUT the alignment are not flat, so the flatness rows bind the all-passes.
 */
class BandsCrossoverSpec : StringSpec({

    val sampleRate = 48000
    val blockFrames = 128
    val blocks = 300
    val settle = 200

    /**
     * The amplitude of the [freq] sinusoid in [samples], from frame [start] on, by a least-squares fit of
     * `a sin + b cos`: exact for a steady sine whatever the window, where an RMS over a part cycle is biased by the
     * phase (which is what an all-pass changes).
     */
    fun amplitude(samples: DoubleArray, start: Int, freq: Double): Double {
        var ss = 0.0
        var cc = 0.0
        var sc = 0.0
        var xs = 0.0
        var xc = 0.0

        for (i in samples.indices) {
            val w = 2.0 * PI * freq * (start + i) / sampleRate
            val sn = sin(w)
            val cs = cos(w)

            ss += sn * sn
            cc += cs * cs
            sc += sn * cs
            xs += samples[i] * sn
            xc += samples[i] * cs
        }

        val det = ss * cc - sc * sc
        val a = (xs * cc - xc * sc) / det
        val b = (xc * ss - xs * sc) / det

        return sqrt(a * a + b * b)
    }

    /** Steady-state amplitude of [dsl] at [freq] over the last blocks (the first [settle] let the filters settle). */
    fun ignitorLevel(dsl: IgnitorDsl, freq: Double): Double {
        val ctx = IgniteContext(
            sampleRate = sampleRate,
            voiceDurationFrames = sampleRate * 10,
            gateEndFrame = sampleRate * 10,
            scratchBuffers = ScratchBuffers(blockFrames),
            random = Random(7),
        )
        val ignitor = dsl.buildExciter(random = ctx.random, freqHz = 220.0, sampleRate = sampleRate).ignitor
        val buffer = AudioBuffer(blockFrames)
        val tail = DoubleArray((blocks - settle) * blockFrames)

        for (b in 0 until blocks) {
            ctx.updateOffsetAndLength(offset = 0, length = blockFrames)
            ignitor.generate(buffer, 220.0, ctx)

            if (b >= settle) {
                buffer.copyInto(destination = tail, destinationOffset = (b - settle) * blockFrames, startIndex = 0, endIndex = blockFrames)
            }

            ctx.voiceElapsedFrames += blockFrames
        }

        return amplitude(samples = tail, start = settle * blockFrames, freq = freq)
    }

    /** Steady-state amplitude at [freq] of a sine at [freq] (peak 1) through the Katalyst chain [dsl], left channel. */
    fun katalystLevel(dsl: KatalystDsl, freq: Double): Double {
        val chain = KatalystChainBuilder.build(
            dsl = dsl,
            sampleRate = sampleRate,
            blockFrames = blockFrames,
            rings = SizedBuffers.forRings(sampleRate),
            reverbs = ReverbUnits(sampleRate),
        ).also { it.applyParams(null) }
        val ctx = KatalystContext(blockFrames = blockFrames, mixBuffer = StereoBuffer(blockFrames))
        val tail = DoubleArray((blocks - settle) * blockFrames)

        for (b in 0 until blocks) {
            for (i in 0 until blockFrames) {
                val v = sin(2.0 * PI * freq * (b * blockFrames + i) / sampleRate)

                ctx.mixBuffer.left[i] = v
                ctx.mixBuffer.right[i] = v
            }

            chain.process(ctx)

            if (b >= settle) {
                ctx.mixBuffer.left.copyInto(destination = tail, destinationOffset = (b - settle) * blockFrames, startIndex = 0, endIndex = blockFrames)
            }
        }

        return amplitude(samples = tail, start = settle * blockFrames, freq = freq)
    }

    fun sine(freq: Double) = IgnitorDsl.Sine(freq = IgnitorDsl.Constant(freq), analog = IgnitorDsl.Constant(0.0))

    /** An amplitude in dB against the input's own (peak 1). */
    fun db(level: Double): Double = 20.0 * log10(level)


    /** Probe frequencies across the range, the cuts and their neighbourhoods included. */
    val probes = listOf(60.0, 150.0, 200.0, 300.0, 424.0, 600.0, 800.0, 1600.0, 4000.0, 12000.0)

    // ── Ignitor ─────────────────────────────────────────────────────────────────────────────────

    "Ignitor: two untouched bands sum to flat level" {
        for (f in probes) {
            withClue("$f Hz") {
                db(ignitorLevel(KlangScriptIgnitorExtensions.bands(sine(f)) { it.cut(200.0) }, f)) shouldBe (0.0 plusOrMinus 0.001)
            }
        }
    }

    "Ignitor: three and four untouched bands sum to flat level (the all-pass alignment)" {
        for (f in probes) {
            withClue("$f Hz, three bands") {
                db(ignitorLevel(KlangScriptIgnitorExtensions.bands(sine(f)) { it.cut(300.0).cut(600.0) }, f)) shouldBe
                        (0.0 plusOrMinus 0.001)
            }

            withClue("$f Hz, four bands") {
                db(ignitorLevel(KlangScriptIgnitorExtensions.bands(sine(f)) { it.cut(200.0).cut(400.0).cut(800.0) }, f)) shouldBe
                        (0.0 plusOrMinus 0.001)
            }
        }
    }

    "Ignitor: a muted mid band takes the mids out and leaves the lows and the highs" {
        fun muted(f: Double) = db(
            ignitorLevel(KlangScriptIgnitorExtensions.bands(sine(f)) { it.cut(200.0).band { mid -> mid.mul(0.0) }.cut(2000.0) }, f),
        )

        muted(700.0) shouldBeLessThan -20.0
        muted(40.0) shouldBe (0.0 plusOrMinus 0.5)
        muted(12000.0) shouldBe (0.0 plusOrMinus 0.5)
    }

    "Ignitor: a tap written in a band reads the BAND, not the unsplit signal" {
        // A tap reads its EQ's input. Handed the crossover's own EQ, `eq()` would continue it and the tap would read the
        // whole signal: a 2x boost at 50 Hz added in the TOP band of a 200 Hz split, +9.5 dB at 50 Hz. Read from the
        // top band, 50 Hz is about 48 dB down (two octaves under a 24 dB per octave side), so the boost adds almost nothing.
        val f = 50.0
        val split = KlangScriptIgnitorExtensions.bands(sine(f)) {
            it.cut(200.0).band { top -> KlangScriptIgnitorExtensions.eq(top) { e -> e.tap(f, 0.7, 2.0) } }
        }

        db(ignitorLevel(split, f)) shouldBe (0.0 plusOrMinus 0.6)
    }

    // ── Katalyst ────────────────────────────────────────────────────────────────────────────────

    "Katalyst: three and four untouched bands sum to flat level" {
        val split = KatalystBuilder(KatalystDsl(emptyList())).bands { it.cut(300.0).cut(600.0) }.node

        val four = KatalystBuilder(KatalystDsl(emptyList())).bands { it.cut(200.0).cut(400.0).cut(800.0) }.node

        for (f in probes) {
            withClue("$f Hz") {
                db(katalystLevel(split, f)) shouldBe (0.0 plusOrMinus 0.001)
            }

            withClue("$f Hz, four bands") {
                db(katalystLevel(four, f)) shouldBe (0.0 plusOrMinus 0.001)
            }
        }
    }

    "Katalyst: a muted mid band takes the mids out and leaves the lows and the highs" {
        val split = KatalystBuilder(KatalystDsl(emptyList()))
            .bands { it.cut(200.0).band { mid -> mid.gain(0.0) }.cut(2000.0) }
            .node

        db(katalystLevel(split, 700.0)) shouldBeLessThan -20.0
        db(katalystLevel(split, 40.0)) shouldBe (0.0 plusOrMinus 0.5)
        db(katalystLevel(split, 12000.0)) shouldBe (0.0 plusOrMinus 0.5)
    }

    // ── Control ─────────────────────────────────────────────────────────────────────────────────

    "control: the same three bands WITHOUT the all-pass on the low band are not flat" {
        // Hand-built like the split, the low band missing the upper cut's all-pass: around the cuts it sums against the
        // others out of phase. Guards the flatness rows against a split that would be flat without the alignment.
        val q = 1.0 / sqrt(2.0)

        fun lp(c: Double) = IgnitorDsl.EqSection.Lowpass(freq = IgnitorDsl.Constant(c), q = IgnitorDsl.Constant(q))
        fun hp(c: Double) = IgnitorDsl.EqSection.Highpass(freq = IgnitorDsl.Constant(c), q = IgnitorDsl.Constant(q))

        fun unaligned(f: Double): IgnitorDsl {
            val x = sine(f)

            return IgnitorDsl.Parallel(
                branches = listOf(
                    IgnitorDsl.Eq(inner = x, sections = listOf(lp(300.0), lp(300.0))),
                    IgnitorDsl.Eq(inner = x, sections = listOf(hp(300.0), hp(300.0), lp(600.0), lp(600.0))),
                    IgnitorDsl.Eq(inner = x, sections = listOf(hp(300.0), hp(300.0), hp(600.0), hp(600.0))),
                ),
            )
        }

        val worst = probes.maxOf { abs(db(ignitorLevel(unaligned(it), it))) }

        withClue("worst deviation without the alignment: $worst dB") {
            (worst > 1.0) shouldBe true
        }
    }

    "the bands builder coerces a falling cut up to the one before it, and the non-finite ones" {
        fun cutsOf(configure: (KatalystBandsBuilder) -> KatalystBandsBuilder): List<Double> {
            var seen: List<Double> = emptyList()

            KatalystBuilder(KatalystDsl(emptyList())).bands { configure(it).also { b -> seen = b.cuts } }

            return seen
        }

        cutsOf { it.cut(2000.0).cut(200.0) } shouldBe listOf(2000.0, 2000.0)
        cutsOf { it.cut(Double.NaN).cut(500.0) } shouldBe listOf(0.0, 500.0)
        cutsOf { it.cut(Double.POSITIVE_INFINITY) } shouldBe listOf(Double.MAX_VALUE)
    }
})
