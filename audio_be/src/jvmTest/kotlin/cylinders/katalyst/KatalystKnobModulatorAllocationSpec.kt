/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

import com.sun.management.ThreadMXBean
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.KatalystKnobDsl
import java.lang.management.ManagementFactory

/**
 * Katalyst knob expressions step 5a: ticking a program that holds every modulator kind allocates nothing on the JVM.
 * Measured by the bytes this thread allocates (`ThreadMXBean`), the [KatalystSettingsAllocationSpec]
 * way: ten runs of 1,000 ticks, the fewest bytes of the runs from the fifth on must be 0 (a one-off, a JIT
 * recompilation, lands in one run; a tick that allocates does so in every run). V8 is measured in step 6.
 *
 * The program moves every path a tick can take: a slot written before every tick (the saw's `freq`, so its shape
 * rebakes every tick), a duty that is itself a modulator (the square rebakes every tick), the noises' walks and fBm
 * octaves, and arithmetic over all of it.
 */
class KatalystKnobModulatorAllocationSpec : StringSpec({

    val mx = ManagementFactory.getThreadMXBean() as ThreadMXBean

    fun bytes(): Double = mx.currentThreadAllocatedBytes.toDouble()

    fun c(v: Double) = KatalystKnobDsl.Constant(v)

    "10,000 ticks of a program with every modulator kind allocate nothing" {
        val tree = listOf(
            KatalystKnobDsl.Sine(freq = c(0.7), phase = KatalystKnobDsl.Param("ph", 0.1)),
            KatalystKnobDsl.Saw(freq = KatalystKnobDsl.Param("rate", 3.0)),
            KatalystKnobDsl.Ramp(freq = c(1.3)),
            KatalystKnobDsl.Tri(freq = c(0.2), phase = c(0.4)),
            KatalystKnobDsl.Square(
                freq = c(2.5),
                duty = KatalystKnobDsl.Range(inner = KatalystKnobDsl.Sine(freq = c(0.31)), from = c(0.2), to = c(0.8)),
            ),
            KatalystKnobDsl.PerlinNoise(rate = c(0.5), octaves = c(4.0), persistence = c(0.6)),
            KatalystKnobDsl.BerlinNoise(rate = c(1.5), octaves = c(2.0)),
        ).reduce<KatalystKnobDsl, KatalystKnobDsl> { acc, node -> KatalystKnobDsl.Plus(left = acc, right = node) }

        val program = KatalystKnobProgram.compile(
            node = KatalystKnobDsl.Abs(inner = tree),
            sampleRate = 48000,
            blockFrames = 128,
            noises = KatalystNoiseOrdinals(),
        )
        val rateSlot = program.slotRegisters[program.slotNames.indexOf("rate")]
        val rates = doubleArrayOf(2.0, 3.0, 5.0, 7.0, 11.0)
        var tick = 0
        var sum = 0.0
        var fewest = Double.MAX_VALUE

        for (run in 0 until 10) {
            val b0 = bytes()

            for (i in 0 until 1000) {
                program.registers[rateSlot] = rates[tick % rates.size]
                program.tick()
                sum += program.registers[program.resultRegister]
                tick++
            }

            val b1 = bytes()

            if (run >= 4) {
                fewest = minOf(fewest, b1 - b0)
            }
        }

        withClue("the fewest bytes a run of 1,000 ticks took was $fewest") {
            fewest shouldBe 0.0
        }
        withClue("the ticks really ran and gave finite values (the sum is $sum)") {
            tick shouldBe 10_000
            sum.isFinite().shouldBeTrue()
        }
    }
})
