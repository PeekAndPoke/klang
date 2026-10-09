/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.effects

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.peekandpoke.klang.audio_be.ignitor.ArrayIgnitor
import io.peekandpoke.klang.audio_be.ignitor.ParamIgnitor
import io.peekandpoke.klang.audio_be.ignitor.phaser
import io.peekandpoke.klang.audio_be.ignitor.renderNodeWindows
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan
import kotlin.random.Random

/**
 * **The phaser's law, against an oracle written here** (test consolidation gap, 2026-09-28). Before this spec every
 * phaser row was relative (a stage against the bare core, floor differences, clock resumes), so a change inside
 * [PhaserCore] moved every host together and nothing saw it.
 *
 * The documented law ([PhaserCore]'s KDoc):
 * - the LFO `lfo = (sin(phase) + 1) / 2` sets the breakpoint `f = center + (lfo - 0.5) sweep`, clamped to
 *   [100 Hz, 18 kHz]; the allpass coefficient is `α = (tan(π f / fs) - 1) / (tan(π f / fs) + 1)`;
 * - α is evaluated at each block's two ends (the phase advancing `2π rate n / fs` over the block, the breakpoint
 *   moving to the block's new center and sweep at its end) and interpolated linearly per sample;
 * - the cascade: `signal = x + feedback · lastOutput`, then per stage `y = α signal + s`, `s = signal - α y`; the
 *   output is the last stage's `y`, which is also `lastOutput` (feedback clamped to [0, 0.95]);
 * - the node (`Ignitor.phaser`) runs 4 stages at the kernel's feedback 0.5 and mixes by the C4 law, correlated
 *   branch: `max(floor, cos²(w π / 2)) · dry + sin²(w π / 2) · wet`.
 *
 * Tolerance 1e-12, measured at most 7.3e-15 (JVM, 2026-09-28): the oracle writes the textbook forms (`π f / fs`, the
 * phase wrapped by `floor`), the kernel its own operand order (`π f · (1 / fs)`, `wrapPhase`); they part in the last
 * bits, which the feedback loop carries.
 */
class PhaserCoreLawSpec : StringSpec({

    val block = 128
    val frames = 2048

    class Oracle(val stages: Int, val sampleRate: Int, val rate: Double, feedback: Double) {
        val feedback = feedback.coerceIn(0.0, 0.95)
        val s = DoubleArray(stages)
        var last = 0.0
        var phase = 0.0
        var alpha = 0.0
        var alphaStep = 0.0

        fun alphaAt(phase: Double, center: Double, sweep: Double): Double {
            val lfo = (sin(phase) + 1.0) / 2.0
            val f = (center + (lfo - 0.5) * sweep).coerceIn(100.0, 18000.0)
            val t = tan(PI * f / sampleRate)

            return (t - 1.0) / (t + 1.0)
        }

        fun block(n: Int, centerFrom: Double, sweepFrom: Double, centerTo: Double, sweepTo: Double) {
            val start = alphaAt(phase = phase, center = centerFrom, sweep = sweepFrom)
            val next = phase + 2.0 * PI * rate * n / sampleRate

            phase = next - 2.0 * PI * floor(next / (2.0 * PI))
            alpha = start
            alphaStep = (alphaAt(phase = phase, center = centerTo, sweep = sweepTo) - start) / n
        }

        fun step(x: Double): Double {
            var signal = x + last * feedback

            for (i in 0 until stages) {
                val y = alpha * signal + s[i]

                s[i] = flush(signal - alpha * y)
                signal = y
            }

            last = flush(signal)
            alpha += alphaStep

            return signal
        }

        private fun flush(v: Double): Double = if (abs(v) >= 1e-15) v else 0.0
    }

    fun noise(n: Int, seed: Int): DoubleArray {
        val r = Random(seed)

        return DoubleArray(n) { r.nextDouble() * 2.0 - 1.0 }
    }

    fun worst(a: DoubleArray, b: DoubleArray): Double = a.indices.maxOf { abs(a[it] - b[it]) }

    "the kernel: the allpass cascade, the LFO sweep and its bounds, the feedback, against the documented law" {
        // (stages, rate, center, sweep, feedback): the canonical four, a longer cascade at a different sweep, a sweep
        // past BOTH bounds (-1 kHz to 19 kHz asked, 100 Hz to 18 kHz given; at 20 Hz the LFO passes its crest and its
        // trough inside the render, so both clamps act), a feedback past the 0.95 ceiling, a negative feedback (clamped
        // to 0, not mirrored).
        val configs = listOf(
            doubleArrayOf(4.0, 3.0, 1000.0, 1000.0, 0.5),
            doubleArrayOf(6.0, 0.7, 2000.0, 3000.0, 0.8),
            doubleArrayOf(4.0, 20.0, 9000.0, 20000.0, 0.3),
            doubleArrayOf(3.0, 1.5, 800.0, 1200.0, 2.0),
            doubleArrayOf(4.0, 2.5, 1500.0, 2000.0, -0.5),
        )
        val input = noise(n = frames, seed = 7)

        for (cfg in configs) {
            val stages = cfg[0].toInt()
            val label = "stages $stages, rate ${cfg[1]}, center ${cfg[2]}, sweep ${cfg[3]}, feedback ${cfg[4]}"

            for (sampleRate in listOf(48000, 44100)) {
                val core = PhaserCore(stages = stages, sampleRate = sampleRate).apply {
                    rate = cfg[1]
                    center = cfg[2]
                    sweep = cfg[3]
                    feedback = cfg[4]
                }
                val oracle = Oracle(stages = stages, sampleRate = sampleRate, rate = cfg[1], feedback = cfg[4])
                val out = DoubleArray(frames)
                val ref = DoubleArray(frames)
                var at = 0

                while (at < frames) {
                    core.prepareBlock(block)
                    oracle.block(n = block, centerFrom = cfg[2], sweepFrom = cfg[3], centerTo = cfg[2], sweepTo = cfg[3])

                    for (i in 0 until block) {
                        out[at + i] = core.step(input[at + i])
                        ref[at + i] = oracle.step(input[at + i])
                    }

                    at += block
                }

                withClue("$label at $sampleRate: the phaser is not the input") { worst(a = out, b = input) shouldBeGreaterThan 0.1 }
                withClue("$label at $sampleRate: worst distance ${worst(a = out, b = ref)}") { worst(a = out, b = ref) shouldBeLessThan 1e-12 }
            }
        }
    }

    "the kernel: a moving breakpoint ramps α from the old breakpoint's value to the new one's across the block" {
        val sampleRate = 48000
        val input = noise(n = frames, seed = 11)
        val core = PhaserCore(stages = 4, sampleRate = sampleRate).apply {
            rate = 1.3
            center = 600.0
            sweep = 400.0
        }
        val oracle = Oracle(stages = 4, sampleRate = sampleRate, rate = 1.3, feedback = 0.5)
        val out = DoubleArray(frames)
        val ref = DoubleArray(frames)
        var center = 600.0
        var sweep = 400.0
        var at = 0

        while (at < frames) {
            // The breakpoint climbs every block: a glide, the Katalyst stage's use of the three-argument form.
            val centerTo = center * 1.25
            val sweepTo = sweep + 150.0

            core.prepareBlock(blockFrames = block, centerTo = centerTo, sweepTo = sweepTo)
            oracle.block(n = block, centerFrom = center, sweepFrom = sweep, centerTo = centerTo, sweepTo = sweepTo)

            for (i in 0 until block) {
                out[at + i] = core.step(input[at + i])
                ref[at + i] = oracle.step(input[at + i])
            }

            center = centerTo
            sweep = sweepTo
            at += block
        }

        withClue("worst distance ${worst(a = out, b = ref)}") { worst(a = out, b = ref) shouldBeLessThan 1e-12 }
    }

    "the node: four stages at feedback 0.5, mixed by max(floor, cos²(wπ/2)) · dry + sin²(wπ/2) · wet" {
        val sampleRate = 48000
        val input = noise(n = frames, seed = 3)
        val windows = List(frames / block) { block }

        // (wet, floor): a crossfade, a floor above cos² (0.345 at wet 0.6, so the floor holds the dry), full wet.
        for ((wet, floor) in listOf(0.6 to 0.0, 0.6 to 0.5, 1.0 to 0.0)) {
            val out = renderNodeWindows(
                ArrayIgnitor(input).phaser(
                    wet = ParamIgnitor("wet", wet), rate = ParamIgnitor("rate", 2.0), center = ParamIgnitor("center", 1200.0),
                    sweep = ParamIgnitor("sweep", 1600.0), floor = ParamIgnitor("floor", floor),
                ),
                windows,
                sampleRate,
            )
            val oracle = Oracle(stages = 4, sampleRate = sampleRate, rate = 2.0, feedback = 0.5)
            val dryC = maxOf(floor, cos(wet * PI / 2.0).let { it * it })
            val wetC = sin(wet * PI / 2.0).let { it * it }
            val ref = DoubleArray(frames)
            var at = 0

            while (at < frames) {
                oracle.block(n = block, centerFrom = 1200.0, sweepFrom = 1600.0, centerTo = 1200.0, sweepTo = 1600.0)

                for (i in 0 until block) {
                    val x = input[at + i]

                    ref[at + i] = dryC * x + wetC * oracle.step(x)
                }

                at += block
            }

            withClue("wet $wet, floor $floor: worst distance ${worst(a = out, b = ref)}") { worst(a = out, b = ref) shouldBeLessThan 1e-12 }
        }
    }
})
