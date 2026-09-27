/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.filters.SvfCoeffs
import io.peekandpoke.klang.audio_be.filters.computeSvfCoeffs
import io.peekandpoke.klang.audio_be.flushState
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * **The tree's SVF (`Ignitor.svf`, its lowpass, highpass and notch taps): the laws the voice strip's filter classes
 * pinned, moved onto the node** (phase 3 step 9, commit a2). The strip's `SvfLPF`, `SvfHPF` and `SvfNotch` retired;
 * the node runs the same TPT recurrence and the same `analog`-gated diode-pair damping, so the laws their spec
 * held now read the node. Each row drives the node from a sine the test writes ([renderThroughNode]).
 *
 * The saturated rows ride the shipped `FILTER_DRIVE_PER_ANALOG` (the node reads the constant; there is no
 * per-filter drive to pin), as the lowpass twin in `ExciterCombinatorsSpec` does.
 */
class SvfNodeLawSpec : StringSpec({

    val sampleRate = 44100
    val frames = 4096

    fun sine(freq: Double, amplitude: Double = 1.0, length: Int = frames): DoubleArray =
        DoubleArray(length) { i -> amplitude * sin(2.0 * PI * freq * i / sampleRate) }

    fun rms(buf: DoubleArray, from: Int = 0): Double {
        var sum = 0.0

        for (i in from until buf.size) {
            sum += buf[i] * buf[i]
        }

        return sqrt(sum / (buf.size - from))
    }

    fun peak(buf: DoubleArray, from: Int): Double {
        var max = 0.0

        for (i in from until buf.size) {
            max = maxOf(max, abs(buf[i]))
        }

        return max
    }

    fun through(input: DoubleArray, build: (Ignitor) -> Ignitor): DoubleArray = renderThroughNode(input, sampleRate, build)

    "topology identity: notch[n] == lowpass[n] + highpass[n] on the linear path" {
        // The TPT-SVF's taps: notch `v0 - k*v1`, lowpass `v2`, highpass `v0 - k*v1 - v2`.
        val src = sine(800.0)
        val lp = through(src) { it.lowpass(1500.0, 0.7) }
        val hp = through(src) { it.highpass(1500.0, 0.7) }
        val notch = through(src) { it.notch(1500.0, 0.7) }

        var maxAbsDiff = 0.0

        for (i in src.indices) {
            maxAbsDiff = maxOf(maxAbsDiff, abs(lp[i] + hp[i] - notch[i]))
        }

        withClue("engaged: the lowpass is not the input") { abs(lp[100] - src[100]) shouldBeGreaterThan 1e-3 }
        maxAbsDiff shouldBeLessThan 1e-12
    }

    "a NaN cutoff is guarded: the state stays finite" {
        // `bilinearK` falls back to 1 kHz for a non-finite cutoff, so a NaN cannot poison the IIR state.
        for (build in listOf<(Ignitor) -> Ignitor>({ it.lowpass(Double.NaN, 1.0) }, { it.highpass(Double.NaN, 1.0) })) {
            through(sine(440.0), build).all { it.isFinite() } shouldBe true
        }
    }

    "analog = 0 runs the LINEAR path on both saturating taps: bit-identical to the TPT kernel written here" {
        // Against an oracle, not against another node: an `analog = 0` that fell into the saturated branch
        // (drive 0, so `kEff == k`) is the same filter algebraically and differs only in rounding, so two nodes
        // that both took the wrong branch would still agree with each other. The lowpass reads `v2`, the
        // highpass `v0 - k * v1 - v2`.
        val src = sine(800.0, amplitude = 0.8, length = 1024)
        val c = SvfCoeffs()
        computeSvfCoeffs(800.0, 5.0, sampleRate.toDouble(), c)

        fun oracle(tap: (v0: Double, v1: Double, v2: Double) -> Double): DoubleArray {
            var ic1eq = 0.0
            var ic2eq = 0.0
            val ref = DoubleArray(src.size)

            for (i in src.indices) {
                val v0 = src[i]
                val v3 = v0 - ic2eq
                val v1 = c.a1 * ic1eq + c.a2 * v3
                val v2 = ic2eq + c.a2 * ic1eq + c.a3 * v3
                ic1eq = (2.0 * v1 - ic1eq).flushState()
                ic2eq = (2.0 * v2 - ic2eq).flushState()
                ref[i] = tap(v0, v1, v2)
            }

            return ref
        }

        val taps = listOf(
            Triple("lowpass", through(src) { it.lowpass(800.0, 5.0, analog = 0.0) }, oracle { _, _, v2 -> v2 }),
            Triple("highpass", through(src) { it.highpass(800.0, 5.0, analog = 0.0) }, oracle { v0, v1, v2 -> v0 - c.k * v1 - v2 }),
        )

        for ((name, out, ref) in taps) {
            withClue("$name: first mismatching frame") {
                src.indices.firstOrNull { out[it].toRawBits() != ref[it].toRawBits() } shouldBe null
            }
        }
    }

    "highpass(analog > 0): the resonance peak is compressed under hot drive" {
        // One-sided like its lowpass twin in ExciterCombinatorsSpec: it goes red when the drive is LOWERED
        // below about 0.125 (the shipped 0.25 measures 0.842 against the 0.9 bound).
        val src = sine(800.0)
        val lin = through(src) { it.highpass(800.0, 5.0, analog = 0.0) }
        val sat = through(src) { it.highpass(800.0, 5.0, analog = 5.0) }
        val linPeak = peak(lin, 2048)

        linPeak shouldBeGreaterThan 2.0
        peak(sat, 2048) shouldBeLessThan (linPeak * 0.9)
    }

    "lowpass(analog > 0) is stable at q 10 with hot drive: its peak stays under the LINEAR filter's" {
        // Damping grows with the state, so heavy resonance is MORE damped, not less. A feedback that REMOVES damping
        // as the state grows (the old tanh-in-the-loop trap) runs past the linear peak (about q times the input);
        // measured: the shipped node about 5.7, the linear filter about 10, a sign-flipped damping term about 26.
        val src = sine(800.0)
        val lin = through(src) { it.lowpass(800.0, 10.0, analog = 0.0) }
        val sat = through(src) { it.lowpass(800.0, 10.0, analog = 5.0) }

        peak(sat, 0) shouldBeLessThan peak(lin, 0)
    }

    "lowpass(analog > 0): no DC and no sub-bass pumping under hot resonance" {
        val out = through(sine(1000.0)) { it.lowpass(2000.0, 5.0, analog = 5.0) }
        val settled = frames / 2

        // 1) DC: the diode polynomial's small asymmetry leaves at most a tiny bias.
        var sum = 0.0

        for (i in settled until frames) {
            sum += out[i]
        }

        abs(sum / (frames - settled)) shouldBeLessThan 0.02

        // 2) Sub-bass: a 100 Hz one-pole on the output keeps well under 5% of the input's RMS (0.707).
        val subBass = through(out) { it.onePoleLowpass(100.0) }

        rms(subBass, settled) shouldBeLessThan 0.05
    }

    "lowpass(analog > 0): a low-amplitude signal stays near-linear" {
        // Small state keeps `diodePairResistanceApprox` near 1, so kEff is near k.
        val src = sine(500.0, amplitude = 0.001)
        val lin = through(src) { it.lowpass(2000.0, 1.0, analog = 0.0) }
        val sat = through(src) { it.lowpass(2000.0, 1.0, analog = 3.0) }
        val ratio = rms(sat, 1024) / rms(lin, 1024)

        ratio shouldBeGreaterThan 0.97
        ratio shouldBeLessThan 1.03
    }
})
