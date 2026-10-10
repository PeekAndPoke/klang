/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.ignitor.ScratchBuffers
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.sin

/**
 * The bench of the oversampler (`docs/tasks/in-progress/iir-oversampler.md` step 1): what a round trip does to a
 * signal, measured, at a 48 kHz base. The IIR half-band that replaced the FIR on 2026-10-10 is held to the figures its
 * KDoc states; the FIR's are in that KDoc for comparison (-2 dB at 16 kHz, about 20 dB of alias rejection).
 *
 * **The oracle is a least-squares fit** of `a sin + b cos` at a known frequency over a long, settled stretch: the
 * amplitude and the phase of that one component, whatever else the signal holds. Every expected value is the input's
 * own (a level of 1, a phase of 0) or a bound the design must meet; nothing is taken from the kernel under test.
 */
class OversamplerBenchSpec : StringSpec({

    val sampleRate = 48000.0
    val blockFrames = 128
    val blocks = 400
    val settle = 100

    class Fit(val amplitude: Double, val phase: Double)

    /**
     * The component at [freq] (Hz at [rate]) of [y], from sample [from] on. Exact only when the stretch spans whole
     * periods of EVERY component [y] holds (`freq * (y.size - from) / rate` an integer for each); otherwise the others
     * leak into the fit, about -90 dB for components 13 kHz apart, the size of the bounds below.
     */
    fun fit(y: DoubleArray, freq: Double, rate: Double, from: Int): Fit {
        var ss = 0.0
        var cc = 0.0
        var sc = 0.0
        var xs = 0.0
        var xc = 0.0

        for (i in from until y.size) {
            val w = 2.0 * PI * freq * i / rate
            val sn = sin(w)
            val cs = cos(w)

            ss += sn * sn
            cc += cs * cs
            sc += sn * cs
            xs += y[i] * sn
            xc += y[i] * cs
        }

        val det = ss * cc - sc * sc
        val a = (xs * cc - xc * sc) / det
        val b = (xc * ss - xs * sc) / det

        return Fit(amplitude = hypot(a, b), phase = atan2(b, a))
    }

    fun db(level: Double): Double = 20.0 * log10(level)

    /** A sine at [freq] and [amplitude] through a round trip at [stages], the shaper [shape] between the halves. */
    fun roundTrip(stages: Int, freq: Double, amplitude: Double, shape: (Double) -> Double): DoubleArray {
        val os = Oversampler(stages)
        val scratch = ScratchBuffers(blockFrames)
        val out = DoubleArray(blocks * blockFrames)

        for (b in 0 until blocks) {
            val block = AudioBuffer(blockFrames) { i -> amplitude * sin(2.0 * PI * freq * (b * blockFrames + i) / sampleRate) }

            os.roundTrip(buffer = block, offset = 0, length = blockFrames, scratch = scratch) { work, count ->
                for (i in 0 until count) {
                    work[i] = shape(work[i])
                }
            }

            block.copyInto(destination = out, destinationOffset = b * blockFrames)
        }

        return out
    }

    val from = settle * blockFrames

    "the round trip is flat: 0.01 dB from 50 Hz to 20 kHz, at 2x to 16x" {
        for (stages in 1..4) {
            for (freq in listOf(50.0, 1000.0, 5000.0, 10000.0, 16000.0, 18000.0, 20000.0)) {
                val y = roundTrip(stages = stages, freq = freq, amplitude = 0.5) { it }

                withClue("stages $stages at $freq Hz") {
                    db(fit(y = y, freq = freq, rate = sampleRate, from = from).amplitude / 0.5) shouldBe (0.0 plusOrMinus 0.01)
                }
            }
        }
    }

    "the decimator rejects what lies above the base band: a cubic's third harmonic at 2x is gone, about 100 dB down" {
        // A cube makes exactly one new component, the third harmonic. For a 10 kHz sine it sits at 30 kHz, and for 11
        // kHz at 33 kHz: inside the 96 kHz stream (no folding there), above the base Nyquist, so the decimation alone
        // decides whether it folds back to 18 kHz and 15 kHz. A clip would also fold harmonics INSIDE the stream, the
        // factor's limit rather than the kernel's, which no decimator can undo.
        for ((freq, alias) in listOf(10000.0 to 18000.0, 11000.0 to 15000.0)) {
            val y = roundTrip(stages = 1, freq = freq, amplitude = 0.8) { it * it * it }
            val fundamental = fit(y = y, freq = freq, rate = sampleRate, from = from).amplitude

            withClue("$freq Hz, the fold at $alias Hz relative to the fundamental") {
                db(fit(y = y, freq = alias, rate = sampleRate, from = from).amplitude / fundamental) shouldBeLessThan -90.0
            }
        }
    }

    "the rejection holds at 4x and 8x: the same third harmonic folds nowhere audible" {
        // At 4x and 8x the 30 kHz harmonic sits in the passband of every stage above the first; the base-rate stage
        // alone decides, as at 2x.
        for (stages in 2..3) {
            val y = roundTrip(stages = stages, freq = 10000.0, amplitude = 0.8) { it * it * it }
            val fundamental = fit(y = y, freq = 10000.0, rate = sampleRate, from = from).amplitude

            withClue("stages $stages, the fold at 18 kHz relative to the fundamental") {
                db(fit(y = y, freq = 18000.0, rate = sampleRate, from = from).amplitude / fundamental) shouldBeLessThan -90.0
            }
        }
    }

    "the transition band: a harmonic just above 26 kHz still folds 90 dB down, to just under 22 kHz" {
        // The base-rate stage's stop band starts at 26 kHz (transition 0.04 of its 96 kHz rate), so a fold lands at 22
        // kHz or above; what lands between 22 and 24 kHz is rejected less (about 37 dB at 23 kHz, the design's trade,
        // above hearing). The input, 8667.5 Hz, and its fold, 21997.5 Hz, both span whole periods of the fit.
        val y = roundTrip(stages = 1, freq = 8667.5, amplitude = 0.8) { it * it * it }
        val fundamental = fit(y = y, freq = 8667.5, rate = sampleRate, from = from).amplitude

        db(fit(y = y, freq = 21997.5, rate = sampleRate, from = from).amplitude / fundamental) shouldBeLessThan -90.0
    }

    "the upsampler leaves no image: a 10 kHz sine at 2x has nothing at 38 kHz" {
        val os = Oversampler(1)
        val work = AudioBuffer(2 * blockFrames)
        val up = DoubleArray(2 * blocks * blockFrames)

        for (b in 0 until blocks) {
            val block = AudioBuffer(blockFrames) { i -> sin(2.0 * PI * 10000.0 * (b * blockFrames + i) / sampleRate) }
            val count = os.upsample(source = block, offset = 0, length = blockFrames, work = work)

            work.copyInto(destination = up, destinationOffset = b * 2 * blockFrames, startIndex = 0, endIndex = count)
        }

        val signal = fit(y = up, freq = 10000.0, rate = 2 * sampleRate, from = 2 * from).amplitude
        val image = fit(y = up, freq = 38000.0, rate = 2 * sampleRate, from = 2 * from).amplitude

        signal shouldBe (1.0 plusOrMinus 1e-3)
        db(image / signal) shouldBeLessThan -90.0
    }

    "the delay is the declared one in the bass and the mids, and rises toward the top (the KDoc's catch), at 2x" {
        fun delayAt(freq: Double): Double {
            val y = roundTrip(stages = 1, freq = freq, amplitude = 0.5) { it }
            var lag = -fit(y = y, freq = freq, rate = sampleRate, from = from).phase

            while (lag < 0.0) {
                lag += 2.0 * PI
            }

            return lag / (2.0 * PI * freq / sampleRate)
        }

        delayAt(200.0) shouldBe (Oversampler.groupDelaySamples(1) plusOrMinus 0.01)
        delayAt(1000.0) shouldBe (3.07 plusOrMinus 0.02)
        delayAt(10000.0) shouldBe (3.33 plusOrMinus 0.02)
        // Above 12 kHz a period is shorter than the delay, so the fit's phase wraps once; one period added back.
        (delayAt(16000.0) + 3.0) shouldBe (3.89 plusOrMinus 0.02)
    }
})
