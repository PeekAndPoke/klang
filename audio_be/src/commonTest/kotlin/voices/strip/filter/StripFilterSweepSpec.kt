/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.voices.strip.filter

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.comparables.shouldBeLessThan
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.cylinders.Cylinders
import io.peekandpoke.klang.audio_be.filters.LowPassHighPassFilters
import io.peekandpoke.klang.audio_be.filters.SAT_STATE_SCALE
import io.peekandpoke.klang.audio_be.filters.diodePairResistanceApprox
import io.peekandpoke.klang.audio_be.ignitor.AnalogDrift
import io.peekandpoke.klang.audio_be.ignitor.IgniteContext
import io.peekandpoke.klang.audio_be.ignitor.Ignitors
import io.peekandpoke.klang.audio_be.ignitor.ScratchBuffers
import io.peekandpoke.klang.audio_be.voices.Voice
import io.peekandpoke.klang.audio_be.voices.strip.BlockContext
import io.peekandpoke.klang.audio_bridge.AdsrCurve
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tan
import kotlin.random.Random

/**
 * The voice strip's filter envelope, per block (decision D3, the sampling, commit a2): `FilterModRenderer`
 * takes the cutoff at the block's first rendered frame and at the frame after its last, the drift HELD
 * across the block, and the SVF steps its coefficients linearly from the one to the other, each step
 * applied after a sample. Rendered through the real renderer and a real `SvfLPF` and compared, sample for
 * sample, with an oracle written here: its own linear ADSR, its own coefficients, its own SVF tick.
 */
class StripFilterSweepSpec : StringSpec({

    val sampleRate = 48000
    val sr = sampleRate.toDouble()
    val blockFrames = 128

    fun ctx(startFrame: Double): BlockContext = BlockContext(
        audioBuffer = AudioBuffer(blockFrames),
        freqModBuffer = DoubleArray(blockFrames),
        scratchBuffers = ScratchBuffers(blockFrames),
        sampleRate = sampleRate,
        startFrame = startFrame,
        endFrame = 1_000_000.0,
        gateEndFrame = 1_000_000.0,
        freqHz = 440.0,
        signal = Ignitors.silence(),
        signalCtx = IgniteContext(
            sampleRate = sampleRate,
            voiceDurationFrames = 1_000_000,
            gateEndFrame = 1_000_000,
            releaseFrames = 0,
            scratchBuffers = ScratchBuffers(blockFrames),
        ),
        cylinders = Cylinders(blockFrames = blockFrames, sampleRate = sampleRate),
    )

    fun input(n: Int): Double = 0.6 * sin(2.0 * PI * 330.0 * n / sr) + 0.3 * sin(2.0 * PI * 2900.0 * n / sr)

    /** The oracle's SVF: coefficients from a cutoff, the textbook TPT form. */
    fun coeffs(fc: Double, q: Double): DoubleArray {
        val g = tan(PI * fc / sr)
        val k = 1.0 / q
        val a1 = 1.0 / (1.0 + g * (g + k))

        return doubleArrayOf(a1, g * a1, g * g * a1)
    }

    // The envelope under test: LINEAR stages (so the oracle's ADSR is three lines), a 300-frame attack,
    // a 500-frame decay to 0.25 (the attack/decay corner falls inside a block), depth 24 semitones.
    val attack = 300.0
    val decay = 500.0
    val sustain = 0.25
    val depth = 24.0
    val base = 500.0
    val q = 2.0

    fun level(pos: Int): Double = when {
        pos < attack -> pos / attack
        pos < attack + decay -> sustain + (1.0 - sustain) * (1.0 - (pos - attack) / decay)
        else -> sustain
    }

    fun cutoff(pos: Int, drift: Double, tolerance: Double): Double = base * 2.0.pow(depth / 12.0 * level(pos)) * drift * tolerance

    /**
     * Renders [blocks] blocks, the voice starting at frame 37 of the first, through `FilterModRenderer` and
     * a real `SvfLPF` (with [tolerance] as its cutoff offset and a drift lane seeded [seed], or none), and
     * the oracle beside it. Returns the largest sample difference.
     */
    fun maxDiffAgainstOracle(blocks: Int, tolerance: Double, seed: Int?): Double {
        val startFrame = 37.0
        val filter = LowPassHighPassFilters.SvfLPF(base, q, sr, cutoffOffsetMul = tolerance)
        val mod = Voice.FilterModulator(
            filter = filter,
            envelope = Voice.Envelope(attack, decay, sustain, 1000.0, AdsrCurve.Linear, AdsrCurve.Linear, AdsrCurve.Linear),
            depth = depth,
            baseCutoff = base,
            drift = seed?.let { AnalogDrift(2.0, 375, Random(it)) },
        )
        val oracleDrift = seed?.let { AnalogDrift(2.0, 375, Random(it)) }
        val renderer = FilterModRenderer(listOf(mod), startFrame)
        val c = ctx(startFrame)
        var ic1 = 0.0
        var ic2 = 0.0
        var maxDiff = 0.0

        for (block in 0 until blocks) {
            val offset = if (block == 0) 37 else 0
            val length = blockFrames - offset
            val pos = block * blockFrames + offset - 37

            c.updateOffsetAndLength(offset, length)
            c.blockStart = (block * blockFrames).toDouble()

            val buf = c.audioBuffer

            for (i in offset until offset + length) {
                buf[i] = input(pos + i - offset)
            }

            renderer.render(c)
            filter.process(buf, offset, length)

            // The oracle: this block's drift, held; the coefficients at both ends; a linear walk.
            val drift = oracleDrift?.nextMultiplier() ?: 1.0
            val s = coeffs(cutoff(pos, drift, tolerance), q)
            val e = coeffs(cutoff(pos + length, drift, tolerance), q)

            for (j in 0 until length) {
                val t = j.toDouble() / length
                val a1 = s[0] + (e[0] - s[0]) * t
                val a2 = s[1] + (e[1] - s[1]) * t
                val a3 = s[2] + (e[2] - s[2]) * t
                val v0 = input(pos + j)
                val v3 = v0 - ic2
                val v1 = a1 * ic1 + a2 * v3
                val v2 = ic2 + a2 * ic1 + a3 * v3

                ic1 = 2.0 * v1 - ic1
                ic2 = 2.0 * v2 - ic2
                maxDiff = maxOf(maxDiff, abs(buf[offset + j] - v2))
            }
        }

        return maxDiff
    }

    "the sweep: a mid-block onset, the attack/decay corner inside a block, sample for sample the oracle" {
        maxDiffAgainstOracle(blocks = 20, tolerance = 1.0, seed = null) shouldBeLessThan 1e-9
    }

    "the drift is held across the block and the tolerance scales both ends" {
        maxDiffAgainstOracle(blocks = 20, tolerance = 1.013, seed = 5) shouldBeLessThan 1e-9
    }

    /**
     * One filter of each tap for the hold rows: the SVF under test, and the oracle's tick for that tap,
     * linear (`v0`, `v1`, `v2` and `k` of the textbook form) or saturated (the closed-form solve with the
     * state-dependent damping, which also reads `g`).
     */
    class Tap(
        val name: String,
        val make: (fc: Double, q: Double) -> LowPassHighPassFilters.BaseSvf,
        val analogDrive: Double = 0.0,
        val out: (v0: Double, hp: Double, bp: Double, lp: Double, k: Double) -> Double,
    )

    val holdDrive = 0.5

    val taps = listOf(
        Tap("SvfLPF", { fc, q -> LowPassHighPassFilters.SvfLPF(fc, q, sr) }) { _, _, _, lp, _ -> lp },
        Tap("SvfHPF", { fc, q -> LowPassHighPassFilters.SvfHPF(fc, q, sr) }) { v0, _, bp, lp, k -> v0 - k * bp - lp },
        Tap("SvfBPF", { fc, q -> LowPassHighPassFilters.SvfBPF(fc, q, sr) }) { _, _, bp, _, k -> k * bp },
        Tap("SvfNotch", { fc, q -> LowPassHighPassFilters.SvfNotch(fc, q, sr) }) { v0, _, bp, _, k -> v0 - k * bp },
        Tap(
            name = "SvfLPF saturated",
            make = { fc, q -> LowPassHighPassFilters.SvfLPF(fc, q, sr, analog = 1.0, drivePerAnalog = holdDrive) },
            analogDrive = holdDrive,
        ) { _, _, _, lp, _ -> lp },
        Tap(
            name = "SvfHPF saturated",
            make = { fc, q -> LowPassHighPassFilters.SvfHPF(fc, q, sr, analog = 1.0, drivePerAnalog = holdDrive) },
            analogDrive = holdDrive,
        ) { _, hp, _, _, _ -> hp },
    )

    /**
     * Sweeps [tap] from 400 Hz to 3 kHz over 64 frames, then processes 128 frames in the [chunks] given
     * (one `process` call each), and compares every sample with the oracle: the coefficients walk
     * linearly for 64 samples and then hold. Returns the largest sample difference.
     */
    fun holdDiff(tap: Tap, chunks: IntArray): Double {
        val q = 1.5
        val sweepFrames = 64
        val filter = tap.make(400.0, q)
        val buf = AudioBuffer(128) { input(it) }

        filter.sweepCutoff(400.0, 3000.0, sweepFrames)

        var at = 0

        for (n in chunks) {
            filter.process(buf, at, n)
            at += n
        }

        val s = coeffs(400.0, q)
        val e = coeffs(3000.0, q)
        val gs = tan(PI * 400.0 / sr)
        val ge = tan(PI * 3000.0 / sr)
        val k = 1.0 / q
        var ic1 = 0.0
        var ic2 = 0.0
        var maxDiff = 0.0

        for (j in 0 until at) {
            val t = minOf(j, sweepFrames).toDouble() / sweepFrames
            val v0 = input(j)
            val hp: Double
            val bp: Double
            val lp: Double

            if (tap.analogDrive > 0.0) {
                val g = gs + (ge - gs) * t
                val kEff = k + 2.0 * tap.analogDrive * (diodePairResistanceApprox(ic1 * SAT_STATE_SCALE) - 1.0)

                hp = (v0 - (kEff + g) * ic1 - ic2) / (1.0 + g * (kEff + g))
                bp = g * hp + ic1
                lp = g * bp + ic2
            } else {
                val a1 = s[0] + (e[0] - s[0]) * t
                val a2 = s[1] + (e[1] - s[1]) * t
                val a3 = s[2] + (e[2] - s[2]) * t
                val v3 = v0 - ic2

                hp = Double.NaN // the linear taps never read it
                bp = a1 * ic1 + a2 * v3
                lp = ic2 + a2 * ic1 + a3 * v3
            }

            ic1 = 2.0 * bp - ic1
            ic2 = 2.0 * lp - ic2
            maxDiff = maxOf(maxDiff, abs(buf[j] - tap.out(v0, hp, bp, lp, k)))
        }

        return maxDiff
    }

    "after `frames` samples the sweep HOLDS the end coefficients (a block longer than the sweep)" {
        for (tap in taps) {
            withClue("${tap.name}: largest sample difference against the oracle, one call of 128") {
                holdDiff(tap, intArrayOf(128)) shouldBeLessThan 1e-9
            }
        }
    }

    "the sweep carries across process calls: 40 + 88 frames walk 64 samples and hold, as one call does" {
        // The countdown of the sweep lives in the filter between calls: a call that ends inside the
        // sweep (40 of 64) hands the next one the 24 steps left, never a fresh 64 (an overshoot past
        // the end cutoff), and a call that begins after it steps nothing.
        for (tap in taps) {
            withClue("${tap.name}: largest sample difference against the oracle, calls of 40 + 88") {
                holdDiff(tap, intArrayOf(40, 88)) shouldBeLessThan 1e-9
            }

            withClue("${tap.name}: largest sample difference against the oracle, calls of 80 + 48") {
                holdDiff(tap, intArrayOf(80, 48)) shouldBeLessThan 1e-9
            }
        }
    }
})
