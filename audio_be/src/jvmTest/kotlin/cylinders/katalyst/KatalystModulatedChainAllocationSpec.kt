/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

import com.sun.management.ThreadMXBean
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.warehouse.ReverbUnits
import io.peekandpoke.klang.audio_be.warehouse.SizedBuffers
import io.peekandpoke.klang.audio_bridge.KatalystDsl
import io.peekandpoke.klang.audio_bridge.KatalystKnobDsl
import io.peekandpoke.klang.audio_bridge.KatalystStageDsl
import java.lang.management.ManagementFactory

/**
 * Knob expressions step 5b: a chain with a modulated knob on EVERY stage family that takes one allocates nothing per
 * block on the JVM, the hook, the writers' refills and the stages' per-block configures included. Measured by the bytes
 * this thread allocates (`ThreadMXBean`), the [KatalystSettingsAllocationSpec] way: after a warm-up of two LFO cycles
 * of the slowest knob (the delay's ring grows to its longest time once, the resonator tables are built once), ten runs
 * of 1,000 blocks; the fewest bytes of the runs from the fifth on must be 0 (a one-off JIT recompilation lands in one
 * run, a block that allocates does so in every run). V8 is measured in step 6.
 *
 * Every knob a stage reads per block moves, and the duck's pass runs on a sidechain, so every branch of the per-block
 * paths is taken: the body's and the vowel's index swap their banks, the delay's time steps through its crossfade, the
 * reverb's lowpass is written as a primitive, the compressor writes its five setters, the arithmetic covers every
 * operator with an operand. Since step 5c a second body and vowel move their wet and floor every block (the in-place
 * blend, the body's wet through 0), and each installs one bank in all.
 */
class KatalystModulatedChainAllocationSpec : StringSpec({

    val mx = ManagementFactory.getThreadMXBean() as ThreadMXBean
    val sampleRate = 48000
    val blockFrames = 128

    fun bytes(): Double = mx.currentThreadAllocatedBytes.toDouble()

    fun c(v: Double) = KatalystKnobDsl.Constant(v)

    /** A sine at [freq] Hz, phase [phase], mapped onto [from]..[to]. */
    fun sine(freq: Double, from: Double, to: Double, phase: Double = 0.0) = KatalystKnobDsl.Range(
        inner = KatalystKnobDsl.Sine(freq = c(freq), phase = c(phase)),
        from = c(from),
        to = c(to),
    )

    "1,000 blocks of a chain with a modulated knob on every stage family allocate nothing" {
        val square = KatalystKnobDsl.Range(
            inner = KatalystKnobDsl.Square(freq = c(1.5), duty = sine(freq = 0.3, from = 0.3, to = 0.7)),
            from = c(1.0),
            to = c(3.0),
        )
        val dsl = KatalystDsl.of(
            KatalystStageDsl.Body(material = square, wet = c(0.4), floor = c(0.4)),
            KatalystStageDsl.Vowel(
                vowel = KatalystKnobDsl.Range(inner = KatalystKnobDsl.Tri(freq = c(0.9)), from = c(1.0), to = c(4.0)),
                wet = c(0.3),
                floor = c(0.2),
            ),
            // Step 5c: a second body and vowel whose wet AND floor move, the blend gliding in place every block. The
            // body's wet crosses 0, so the bypass, its landing and the return from rest run too.
            KatalystStageDsl.Body(
                material = c(2.0),
                wet = sine(freq = 0.85, from = -0.2, to = 0.8),
                floor = sine(freq = 0.65, from = 0.2, to = 0.6),
            ),
            KatalystStageDsl.Vowel(
                vowel = c(3.0),
                wet = sine(freq = 0.95, from = 0.2, to = 0.9),
                floor = sine(freq = 0.55, from = 0.0, to = 0.5),
            ),
            KatalystStageDsl.Delay(
                wet = sine(freq = 0.7, from = -0.1, to = 0.4),
                time = sine(freq = 0.5, from = 0.1, to = 0.3),
                feedback = KatalystKnobDsl.Range(
                    inner = KatalystKnobDsl.Saw(freq = c(0.4)),
                    from = c(0.2),
                    to = c(0.6),
                ),
                cap = sine(freq = 1.1, from = 0.8, to = 1.2),
            ),
            KatalystStageDsl.Reverb(
                wet = sine(freq = 0.6, from = 0.1, to = 0.3),
                size = KatalystKnobDsl.Range(inner = KatalystKnobDsl.Ramp(freq = c(0.3)), from = c(3.0), to = c(7.0)),
                lowpass = sine(freq = 0.8, from = 2000.0, to = 6000.0),
            ),
            KatalystStageDsl.Phaser(
                wet = sine(freq = 0.9, from = 0.3, to = 0.7),
                rate = sine(freq = 0.2, from = 0.5, to = 2.0),
                center = sine(freq = 0.1, from = 500.0, to = 1500.0),
                sweep = sine(freq = 0.15, from = 200.0, to = 600.0),
                floor = sine(freq = 0.25, from = 0.5, to = 1.0),
            ),
            KatalystStageDsl.Compressor(
                threshold = sine(freq = 0.5, from = -30.0, to = -10.0),
                ratio = sine(freq = 0.6, from = 2.0, to = 6.0),
                knee = sine(freq = 0.7, from = 0.0, to = 6.0),
                attack = sine(freq = 0.8, from = 0.001, to = 0.02),
                release = sine(freq = 0.9, from = 0.05, to = 0.2),
            ),
            KatalystStageDsl.Distort(
                amount = KatalystKnobDsl.Range(
                    inner = KatalystKnobDsl.PerlinNoise(rate = c(0.01), octaves = c(3.0)),
                    from = c(0.1),
                    to = c(0.5),
                ),
            ),
            KatalystStageDsl.Plus(other = sine(freq = 1.3, from = -0.01, to = 0.01)),
            KatalystStageDsl.Times(other = sine(freq = 1.7, from = 0.9, to = 1.1)),
            KatalystStageDsl.Div(other = sine(freq = 0.35, from = 0.8, to = 1.2)),
            KatalystStageDsl.Pow(exp = sine(freq = 0.45, from = 0.95, to = 1.05)),
            KatalystStageDsl.Min(other = sine(freq = 0.55, from = 0.8, to = 1.0)),
            KatalystStageDsl.Max(other = sine(freq = 0.65, from = -1.0, to = -0.8)),
            KatalystStageDsl.Gain(
                gain = KatalystKnobDsl.Range(
                    inner = KatalystKnobDsl.BerlinNoise(rate = c(0.02), octaves = c(2.0)),
                    from = c(0.7),
                    to = c(1.0),
                ),
            ),
            KatalystStageDsl.Duck(
                orbit = KatalystKnobDsl.Range(
                    inner = KatalystKnobDsl.Square(freq = c(0.5)),
                    from = c(1.0),
                    to = c(2.0),
                ),
                depth = sine(freq = 0.75, from = -0.2, to = 0.8),
                attack = sine(freq = 0.95, from = 0.01, to = 0.05),
            ),
        )
        val chain = KatalystChainBuilder.build(
            dsl = dsl,
            sampleRate = sampleRate,
            blockFrames = blockFrames,
            rings = SizedBuffers.forRings(sampleRate),
            reverbs = ReverbUnits(sampleRate),
        )
        val ctx = KatalystContext(blockFrames = blockFrames, mixBuffer = StereoBuffer(blockFrames))
        val sidechain = StereoBuffer(blockFrames)
        var b = 0
        var sum = 0.0

        ctx.sidechainBuffer = sidechain
        chain.applyParams(null)

        // No lambda: a block is a plain loop body, so the harness boxes nothing of its own.
        fun runBlocks(n: Int) {
            for (j in 0 until n) {
                for (i in 0 until blockFrames) {
                    ctx.mixBuffer.left[i] = ((i * 37 + b) % 101) / 101.0 - 0.5
                    ctx.mixBuffer.right[i] = ((i * 53 + b) % 89) / 89.0 - 0.5
                    sidechain.left[i] = ((i * 11 + b) % 23) / 23.0
                    sidechain.right[i] = sidechain.left[i]
                }

                chain.process(ctx)
                chain.processDuck(ctx)
                sum += ctx.mixBuffer.left[blockFrames - 1]
                b++
            }
        }

        // Two cycles of the slowest knob (the phaser's center at 0.1 Hz is 3,750 blocks a cycle at 48 kHz).
        runBlocks(7500)

        var fewest = Double.MAX_VALUE

        for (run in 0 until 10) {
            val b0 = bytes()

            runBlocks(1000)

            val b1 = bytes()

            if (run >= 4) {
                fewest = minOf(fewest, b1 - b0)
            }
        }

        withClue("the fewest bytes a run of 1,000 blocks took was $fewest") {
            fewest shouldBe 0.0
        }
        withClue("the moving wet and floor installed nothing: one bank each for the 5c body and vowel") {
            chain.body.shouldNotBeNull().installs shouldBe 1
            chain.vowel.shouldNotBeNull().installs shouldBe 1
        }
        withClue("the blocks ran and the output stayed finite (the sum is $sum)") {
            b shouldBe 17_500
            sum.isFinite().shouldBeTrue()
        }
    }
})
