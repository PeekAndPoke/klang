/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_engine.KlangOfflineRenderer
import kotlin.math.sqrt

/**
 * `solo(...)` rendered through the real engine (`KlangOfflineRenderer`: the KlangScript compile, the inline-DSL
 * registration, the scheduler with its solo tracker, the orbits and the master), at 48 kHz and `cps = 0.5`, two
 * seconds per cycle. The two bugs of `docs/tasks-archive/2026-10/20261009-bugfix-solo-rests-and-amount.md`:
 *
 * 1. a rest after `.solo()` played a sine once a `gain` and a pitch op followed the solo;
 * 2. the background never went below 5 % (`1 - amount * 0.95`); decided: it plays at `1 - amount`.
 *
 * Here, not in `:klang`, because `:klang` cannot compile sprudel; sprudel has both and depends on `:klang`.
 */
class SoloRenderSpec : StringSpec({

    val sampleRate = 48_000
    val secPerCycle = 2.0

    class Render(val left: DoubleArray, val right: DoubleArray)

    suspend fun render(code: String, cycles: Int): Render {
        val pattern = SprudelPattern.compile(code) ?: error("did not compile: $code")
        val left = ArrayList<Double>()
        val right = ArrayList<Double>()

        KlangOfflineRenderer(sampleRate = sampleRate).render(
            pattern = pattern,
            cycles = cycles,
            cyclesPerSecond = 1.0 / secPerCycle,
            tailSec = 0.0,
            onBlock = { out, frames ->
                for (i in 0 until frames) {
                    left.add(out.left[i])
                    right.add(out.right[i])
                }
            },
        )

        return Render(left = left.toDoubleArray(), right = right.toDoubleArray())
    }

    fun DoubleArray.rms(fromSec: Double, toSec: Double): Double {
        val from = (fromSec * sampleRate).toInt()
        val to = (toSec * sampleRate).toInt()
        var sum = 0.0

        for (i in from until to) {
            sum += this[i] * this[i]
        }

        return sqrt(sum / (to - from))
    }

    // -- bug 1: the rest is silent ---------------------------------------------------------------------------------

    "bug 1: a rest after solo() stays silent with a gain and a pitch op after it, in both chain forms" {
        val chains = listOf(
            """
                let myInst = Ign.saw()
                note("c4 ~").sound(myInst)SOLO.gain(0.5).transpose(0)
            """.trimIndent(),
            // How Der Schmetterling writes it: the shape carries the solo, the arrangement adds orbit, scale and
            // gain, the whole stack is transposed.
            """
                let myInst = Ign.saw()
                stack(
                  n("0 ~").apply(x => x.sound(myInst)SOLO).apply(x => x.orbit(1).scale("e3:minor").gain(0.18))
                ).transpose(0)
            """.trimIndent(),
        )

        for (code in chains) {
            val soloed = render(code.replace("SOLO", ".solo()"), cycles = 4)
            val plain = render(code.replace("SOLO", ""), cycles = 4)

            for (cycle in 1 until 4) {
                val start = cycle * secPerCycle

                withClue("$code | cycle $cycle") {
                    withClue("the note sounds") { soloed.left.rms(start + 0.1, start + 0.9) shouldBeGreaterThan 0.01 }
                    // The rest is the second second of the cycle; from 0.25 s in, the note's 50 ms release is over.
                    withClue("the rest is silent (was -12 dB, a 220 Hz sine)") {
                        soloed.left.rms(start + 1.25, start + 1.95) shouldBeLessThan 1e-6
                    }
                }
            }

            // The only pattern is the soloed one: it plays at full level, and the control events build no voice,
            // so the whole render is the unsoloed render, sample for sample.
            withClue("$code | identical to the same chain without solo()") {
                (soloed.left.contentEquals(plain.left) && soloed.right.contentEquals(plain.right)) shouldBe true
            }
        }
    }

    // -- bug 2: the others play at 1 - amount ----------------------------------------------------------------------

    "bug 2: the background plays at 1 - amount: solo() 0.05, solo(0.7) 0.3, solo(1.0) silence" {
        // The soloed lead (with a rest) at gain 0: it sounds nothing and still carries the solo, so the mix is the
        // bed alone and its level ratio is the solo multiplier. Quiet enough that the master's limiter never acts.
        fun song(solo: String) = """
            stack(
              note("a3").sound(Ign.saw()).gain(0.3),
              note("c4 ~").sound(Ign.sine()).gain(0)$solo
            )
        """.trimIndent()

        fun Render.level(): Double = left.rms(4.0, 8.0) + right.rms(4.0, 8.0)

        val unsoloed = render(song(""), cycles = 4).level()

        withClue("the bed is heard unsoloed") { unsoloed shouldBeGreaterThan 0.01 }

        withClue("solo(): the others at 5 %") {
            (render(song(".solo()"), cycles = 4).level() / unsoloed) shouldBe (0.05 plusOrMinus 1e-6)
        }
        withClue("solo(0.7): the others at 0.3") {
            (render(song(".solo(0.7)"), cycles = 4).level() / unsoloed) shouldBe (0.3 plusOrMinus 1e-6)
        }
        withClue("solo(1.0): the others silent") {
            render(song(".solo(1.0)"), cycles = 4).level() shouldBe 0.0
        }
    }
})
