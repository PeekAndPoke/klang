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
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.filters.SvfCoeffs
import io.peekandpoke.klang.audio_be.filters.computeSvfCoeffs
import io.peekandpoke.klang.audio_be.filters.diodePairResistanceApprox
import io.peekandpoke.klang.audio_be.flushState
import io.peekandpoke.klang.audio_bridge.AdsrCurve
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.random.Random

/** This file's one seeded stream: every run draws the same, and successive builds still draw
 *  differently (as they did from the process-wide stream these calls used before). */
private val testRandom = Random(0x5EED)

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
        val lp = through(src) { it.lowpass(cutoffHz = 1500.0, q = 0.7) }
        val hp = through(src) { it.highpass(cutoffHz = 1500.0, q = 0.7) }
        val notch = through(src) { it.notch(cutoffHz = 1500.0, q = 0.7) }

        var maxAbsDiff = 0.0

        for (i in src.indices) {
            maxAbsDiff = maxOf(maxAbsDiff, abs(lp[i] + hp[i] - notch[i]))
        }

        withClue("engaged: the lowpass is not the input") { abs(lp[100] - src[100]) shouldBeGreaterThan 1e-3 }
        maxAbsDiff shouldBeLessThan 1e-12
    }

    "a NaN cutoff is guarded: the state stays finite" {
        // `bilinearK` falls back to 1 kHz for a non-finite cutoff, so a NaN cannot poison the IIR state.
        for (build in listOf<(Ignitor) -> Ignitor>({ it.lowpass(cutoffHz = Double.NaN, q = 1.0) }, { it.highpass(cutoffHz = Double.NaN, q = 1.0) })) {
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
        computeSvfCoeffs(cutoffHz = 800.0, q = 5.0, sampleRate = sampleRate.toDouble(), out = c)

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
            Triple("lowpass", through(src) { it.lowpass(cutoffHz = 800.0, q = 5.0, analog = 0.0) }, oracle { _, _, v2 -> v2 }),
            Triple("highpass", through(src) { it.highpass(cutoffHz = 800.0, q = 5.0, analog = 0.0) }, oracle { v0, v1, v2 -> v0 - c.k * v1 - v2 }),
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
        val lin = through(src) { it.highpass(cutoffHz = 800.0, q = 5.0, analog = 0.0) }
        val sat = through(src) { it.highpass(cutoffHz = 800.0, q = 5.0, analog = 5.0) }
        val linPeak = peak(lin, 2048)

        linPeak shouldBeGreaterThan 2.0
        peak(sat, 2048) shouldBeLessThan (linPeak * 0.9)
    }

    "lowpass(analog > 0) is stable at q 10 with hot drive: its peak stays under the LINEAR filter's" {
        // Damping grows with the state, so heavy resonance is MORE damped, not less. A feedback that REMOVES damping
        // as the state grows (the old tanh-in-the-loop trap) runs past the linear peak (about q times the input);
        // measured: the shipped node about 5.7, the linear filter about 10, a sign-flipped damping term about 26.
        val src = sine(800.0)
        val lin = through(src) { it.lowpass(cutoffHz = 800.0, q = 10.0, analog = 0.0) }
        val sat = through(src) { it.lowpass(cutoffHz = 800.0, q = 10.0, analog = 5.0) }

        peak(sat, 0) shouldBeLessThan peak(lin, 0)
    }

    "lowpass(analog > 0): no DC and no sub-bass pumping under hot resonance" {
        val out = through(sine(1000.0)) { it.lowpass(cutoffHz = 2000.0, q = 5.0, analog = 5.0) }
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
        val lin = through(src) { it.lowpass(cutoffHz = 2000.0, q = 1.0, analog = 0.0) }
        val sat = through(src) { it.lowpass(cutoffHz = 2000.0, q = 1.0, analog = 3.0) }
        val ratio = rms(sat, 1024) / rms(lin, 1024)

        ratio shouldBeGreaterThan 0.97
        ratio shouldBeLessThan 1.03
    }

    "the swept cutoff, loop by loop: each tap's per-sample coefficient step against the swept TPT kernel written here" {
        // Test consolidation gap (2026-09-28): every mode's loop in `Ignitor.svf` carries its OWN copy of the step
        // (`a1 += a1Step`, ..., `g += gStep`), and before this row only the baseline's whole-voice rows pinned the
        // bandpass, notch, highpass and saturated loops while the cutoff moves (`EnvelopeLawSpec` has the linear
        // lowpass). The oracle: the cutoff envelope written out (linear attack to 1 over 300 frames, linear decay to
        // 0.25 over 400, +24 semitones over 500 Hz), read at each block's first frame and one past its last; the
        // textbook coefficients `g = tan(pi fc / fs)`, `k = 1 / q`, `a1 = 1 / (1 + g (g + k))`, `a2 = g a1`,
        // `a3 = g a2` at both ends; every coefficient stepped by `(end - start) / n` after each sample. The saturated
        // loops' damping `kEff = k + 2 (0.25 analog) (diode(0.0876 ic1) - 1)` takes the documented state scale 0.0876 and
        // drive 0.25 per analog as literals and the diode curve from production, which the row below pins.
        val block = 128
        val total = 1280
        val attack = 300.0
        val decay = 400.0
        val sustain = 0.25
        val base = 500.0
        val depth = 24.0
        val q = 3.0
        val analog = 2.0
        val sr = sampleRate.toDouble()

        fun level(p: Int): Double = when {
            p < attack -> p / attack
            p < attack + decay -> sustain + (1.0 - sustain) * (1.0 - (p - attack) / decay)
            else -> sustain
        }

        fun coeffs(p: Int): DoubleArray {
            val fc = base * 2.0.pow(depth / 12.0 * level(p).coerceIn(0.0, 1.0))
            val g = tan(PI * fc / sr)
            val k = 1.0 / q
            val a1 = 1.0 / (1.0 + g * (g + k))

            return doubleArrayOf(a1, g * a1, g * g * a1, k, g)
        }

        val input = sine(1500.0, amplitude = 0.8, length = total)

        fun oracle(saturated: Boolean, tap: (v0: Double, v1: Double, v2: Double, vHp: Double, k: Double) -> Double): DoubleArray {
            val out = DoubleArray(total)
            var ic1 = 0.0
            var ic2 = 0.0
            var start = 0

            while (start < total) {
                val n = minOf(block, total - start)
                val c0 = coeffs(start)
                val c1 = coeffs(start + n)
                val c = c0.copyOf()

                for (i in 0 until n) {
                    val v0 = input[start + i]
                    val (a1, a2, a3, k, g) = c

                    if (saturated) {
                        val kEff = k + 2.0 * (analog * 0.25) * (diodePairResistanceApprox(ic1 * 0.0876) - 1.0)
                        val vHp = (v0 - (kEff + g) * ic1 - ic2) / (1.0 + g * (kEff + g))
                        val vBp = g * vHp + ic1
                        val vLp = g * vBp + ic2

                        ic1 = (2.0 * vBp - ic1).flushState()
                        ic2 = (2.0 * vLp - ic2).flushState()
                        out[start + i] = tap(v0, vBp, vLp, vHp, k)
                    } else {
                        val v3 = v0 - ic2
                        val v1 = a1 * ic1 + a2 * v3
                        val v2 = ic2 + a2 * ic1 + a3 * v3

                        ic1 = (2.0 * v1 - ic1).flushState()
                        ic2 = (2.0 * v2 - ic2).flushState()
                        out[start + i] = tap(v0, v1, v2, v0 - k * v1 - v2, k)
                    }

                    for (j in c.indices) {
                        c[j] += (c1[j] - c0[j]) / n
                    }
                }

                start += n
            }

            return out
        }

        val env = FilterEnvDef(
            depth = depth, attackSec = attack / sr, decaySec = decay / sr, sustainLevel = sustain, releaseSec = 0.0,
            attackCurve = AdsrCurve.Linear, decayCurve = AdsrCurve.Linear, releaseCurve = AdsrCurve.Linear,
        )

        /** In blocks, the gate far past the render, so every block end reads the open envelope. */
        fun node(mode: SvfMode, analogKnob: Double): DoubleArray {
            val ig = ArrayIgnitor(input).svf(mode = mode, cutoffHz = ParamIgnitor("f", base), q = ParamIgnitor("q", q), env = env, analog = ParamIgnitor("analog", analogKnob))
            val ctx = IgniteContext(
                sampleRate = sampleRate, voiceDurationFrames = 10 * total, gateEndFrame = 10 * total,
                scratchBuffers = ScratchBuffers(block),
                random = testRandom,
            )
            val out = DoubleArray(total)
            val buffer = AudioBuffer(block)

            for (at in 0 until total step block) {
                ctx.voiceElapsedFrames = at
                ctx.updateOffsetAndLength(offset = 0, length = block)
                ig.generate(buffer, 220.0, ctx)
                buffer.copyInto(destination = out, destinationOffset = at, startIndex = 0, endIndex = block)
            }

            return out
        }

        val loops = listOf(
            Triple("lowpass", node(SvfMode.LOWPASS, 0.0), oracle(false) { _, _, v2, _, _ -> v2 }),
            Triple("highpass", node(SvfMode.HIGHPASS, 0.0), oracle(false) { _, _, _, vHp, _ -> vHp }),
            Triple("bandpass", node(SvfMode.BANDPASS, 0.0), oracle(false) { _, v1, _, _, k -> k * v1 }),
            Triple("notch", node(SvfMode.NOTCH, 0.0), oracle(false) { v0, v1, _, _, k -> v0 - k * v1 }),
            Triple("saturated lowpass", node(SvfMode.LOWPASS, analog), oracle(true) { _, _, vLp, _, _ -> vLp }),
            Triple("saturated highpass", node(SvfMode.HIGHPASS, analog), oracle(true) { _, _, _, vHp, _ -> vHp }),
        )

        withClue("the cutoff moves: 500 Hz at the onset, 2 kHz at the attack's end") { coeffs(300)[4] / coeffs(0)[4] shouldBeGreaterThan 3.0 }

        for ((name, out, ref) in loops) {
            var worst = 0.0

            for (i in 0 until total) {
                worst = maxOf(worst, abs(out[i] - ref[i]))
            }

            // 1e-13: measured at most 4.4e-16 (JVM, 2026-09-28). The envelope's frame arithmetic
            // (`pos * (1 / attackFrames)`), `computeSvfCoeffs`' operand order and the step's `* (1 / n)` round
            // differently from the textbook forms here, in the last bits.
            withClue("$name: the worst sample's distance from the swept oracle is $worst") { worst shouldBeLessThan 1e-13 }
        }
    }

    "the diode-pair damping curve of the saturated loops, at literal values of its polynomial" {
        // `1 + 0.05 x + 0.185 x² + 0.00920833 x³ + 0.0103592 x⁴`, Horner form; the literals are its values in doubles.
        val table = listOf(
            -2.0 to 1.73208056,
            -0.5 to 1.02074640875,
            0.0 to 1.0,
            0.3 to 1.03198253443,
            1.0 to 1.25456753,
            2.5 to 2.82978640625,
        )

        for ((x, expected) in table) {
            withClue("diodePairResistanceApprox($x)") { diodePairResistanceApprox(x) shouldBe expected }
        }
    }
})
