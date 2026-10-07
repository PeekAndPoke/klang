/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.pattern

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.peekandpoke.klang.script.klangScript
import io.peekandpoke.klang.script.klangScriptLibrary
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.SprudelPatternEvent
import io.peekandpoke.klang.sprudel.lang.note
import io.peekandpoke.klang.sprudel.lang.solo
import io.peekandpoke.klang.sprudel.lang.sprudelLib
import kotlin.math.ceil
import kotlin.math.floor

/**
 * `solo(...)` covers its rests with CONTROL events: engine state (the amount and the solo id), never a voice
 * (`docs/tasks/bugfix-solo-rests-and-amount.md`). Until 2026-10-07 the rests were filled with a sounding sine at
 * gain 1e-6 and 0 Hz, which any `gain` plus any pitch op after the `solo()` turned into a sine at the part's level.
 */
class SoloPatternSpec : StringSpec({

    fun script(code: String): SprudelPattern = SprudelPattern.compile(code) ?: error("did not compile: $code")

    fun List<SprudelPatternEvent>.sounding() = filter { it.data.control != true }

    fun List<SprudelPatternEvent>.control() = filter { it.data.control == true }

    // -- a rest is control only --------------------------------------------------------------------------------------

    "a rest yields control events only: control, the amount, the solo id, and no note, freq, sound or gain" {
        val pattern = note("a ~").solo()
        val soloId = pattern.shouldBeInstanceOf<SoloPattern>().soloId

        for (cycle in 0 until 4) {
            withClue("cycle $cycle") {
                val inRest = pattern.queryArc(cycle + 0.5, cycle + 1.0)

                inRest.shouldNotBeEmpty()

                for (event in inRest) {
                    event.data.control shouldBe true
                    event.data.solo shouldBe 0.95
                    event.data.patternId shouldBe soloId
                    event.data.note shouldBe null
                    event.data.freqHz shouldBe null
                    event.data.sound shouldBe null
                    event.data.gain shouldBe null
                    event.toVoiceData().control shouldBe true
                    event.toVoiceData().sourceId shouldBe soloId
                }
            }
        }
    }

    // -- bug 1: nothing after solo() makes a rest sound --------------------------------------------------------------

    "control survives every op after solo(): the bug-1 chains and the Der Schmetterling form" {
        val chains = listOf(
            """note("c4 ~").sound("sine").solo().gain(0.5).transpose(0)""",
            """note("c4 ~").sound("sine").solo().transpose(3).gain(0.5)""",
            """note("a ~").sound("sine").solo().scale("C4:major")""",
            """note("a ~").sound("sine").solo().note("c4")""",
            """note("a ~").solo().sound("saw")""",
            // Der Schmetterling: the shape carries the solo, the arrangement adds scale and gain, the
            // whole stack is transposed.
            """
                let myInst = Ign.saw()
                stack(
                  n("0 ~").apply(x => x.sound(myInst).solo()).apply(x => x.orbit(1).scale("e3:minor").gain(0.18))
                ).transpose(2)
            """.trimIndent(),
        )

        for (code in chains) {
            val pattern = script(code)

            for (cycle in 0 until 4) {
                withClue("$code | cycle $cycle") {
                    // Queried as the frontend does (onsets of each window), the note half and the rest half apart.
                    val noteHalf = pattern.queryArc(cycle.toDouble(), cycle + 0.5).filter { it.isOnset }
                    val restHalf = pattern.queryArc(cycle + 0.5, cycle + 1.0).filter { it.isOnset }

                    noteHalf.sounding() shouldHaveSize 1
                    restHalf.shouldNotBeEmpty()
                    restHalf.sounding() shouldHaveSize 0
                    restHalf.forEach { it.toVoiceData().control shouldBe true }
                }
            }
        }
    }

    // -- coverage --------------------------------------------------------------------------------------------------

    "the control events cover each query window without a gap, also when the window is queried in chunks" {
        val pattern = script("""note("a ~ b ~ ~").solo("<1 0.5>")""")

        for (chunk in listOf(1.0, 0.25, 1.0 / 3.0, 0.7)) {
            var from = 0.0

            while (from < 4.0) {
                val to = minOf(from + chunk, 4.0)

                withClue("chunk $chunk, window [$from, $to)") {
                    val spans = pattern.queryArc(from, to).control().map { it.part }.sortedBy { it.begin }

                    spans.shouldNotBeEmpty()
                    spans.first().begin.toDouble() shouldBe (from plusOrMinus 1e-9)
                    spans.last().end.toDouble() shouldBe (to plusOrMinus 1e-9)

                    for (i in 1 until spans.size) {
                        spans[i].begin shouldBe spans[i - 1].end
                    }

                    // whole == part: every control event is an onset in its window.
                    pattern.queryArc(from, to).control().forEach { it.isOnset shouldBe true }
                }

                from = to
            }
        }
    }

    "the control events are cut on the 1/8-cycle grid: a live edit's resend reaches the solo within 1/8 cycle" {
        // A live edit resends only the events that START after its cutoff (now plus 0.2 s), from a one-cycle
        // chunk. Before the grid the cycle's single control event started before every cutoff, so the edit waited
        // for the next cycle. Now the pieces starting after any cutoff leave less than 1/8 cycle uncovered.
        val pattern = script("""note("a ~").solo()""")
        val step = 1.0 / 8.0

        for (cycle in 0 until 3) {
            val chunk = pattern.queryArc(cycle.toDouble(), cycle + 1.0).control()

            for (event in chunk) {
                withClue("cycle $cycle, piece ${event.part}") {
                    val begin = event.part.begin.toDouble()
                    val end = event.part.end.toDouble()

                    // Within one grid cell.
                    floor(begin / step + 1e-9) shouldBe (ceil(end / step - 1e-9) - 1.0)
                }
            }

            for (cutoff in listOf(0.0, 0.05, 0.37, 0.5, 0.95)) {
                val at = cycle + cutoff
                val resent = chunk.filter { it.part.begin.toDouble() >= at }.sortedBy { it.part.begin }

                // Nothing resent from this chunk only when the cutoff is inside its last grid cell; the next
                // chunk's events start at its end.
                val firstResent = resent.firstOrNull()?.part?.begin?.toDouble() ?: (cycle + 1.0)

                withClue("cycle $cycle, cutoff $at") {
                    (firstResent - at) shouldBeLessThan step
                    resent.lastOrNull()?.let { it.part.end.toDouble() shouldBe ((cycle + 1.0) plusOrMinus 1e-9) }
                }
            }
        }
    }

    "solo(\"<1 0>\") gives control events at 1, then 0" {
        val pattern = script("""note("a ~").solo("<1 0>")""")

        for (cycle in 0 until 4) {
            withClue("cycle $cycle") {
                val control = pattern.queryArc(cycle.toDouble(), cycle + 1.0).control()

                control.shouldNotBeEmpty()
                control.forEach { it.data.solo shouldBe (if (cycle % 2 == 0) 1.0 else 0.0) }
            }
        }
    }

    "a non-finite amount reads as 0.0 on the control events and the notes" {
        val pattern = note("a ~").solo(Double.NaN)
        val events = pattern.queryArc(0.0, 1.0)

        events.control().shouldNotBeEmpty()
        events.forEach { it.data.solo shouldBe 0.0 }
    }

    // -- purity and the solo id ------------------------------------------------------------------------------------

    "querying the same SoloPattern twice gives equal results" {
        val pattern = script("""note("a ~ c").solo("<1 0.5>")""")

        for (cycle in 0 until 4) {
            pattern.queryArc(cycle.toDouble(), cycle + 1.0) shouldBe pattern.queryArc(cycle.toDouble(), cycle + 1.0)
        }
    }

    "two compiles of the same code give the same id; two solo calls give two ids" {
        val code = """stack(note("a").solo(), note("b").solo())"""

        fun idsByNote(pattern: SprudelPattern): Map<String?, String?> =
            pattern.queryArc(0.0, 1.0).sounding().associate { it.data.note to it.data.patternId }

        val first = idsByNote(script(code))
        val second = idsByNote(script(code))

        first shouldBe second
        first["a"] shouldNotBe first["b"]
    }

    "stack(p.solo(), p.transpose(7)): the unsoloed copy keeps p's own id" {
        // p alone, its literal at the same line and column as below, so its atom gets the same id.
        val p = script(
            """
                let p = note("a")
                p
            """.trimIndent()
        )
        val ownId = p.queryArc(0.0, 1.0).single().data.patternId

        val stacked = script(
            """
                let p = note("a")
                stack(p.solo(), p.transpose(7))
            """.trimIndent()
        )
        val sounding = stacked.queryArc(0.0, 1.0).sounding()
        val soloed = sounding.single { it.data.solo != null }
        val copy = sounding.single { it.data.solo == null }

        copy.data.patternId shouldNotBe soloed.data.patternId
        copy.data.patternId shouldBe ownId
    }

    "two solo calls at the same line and column in two modules give two ids" {
        // The atom ids hash line and column only, so before 2026-10-07 (the solo took its source's atom id)
        // these two solos were one source to the engine.
        val partA = klangScriptLibrary("partA") {
            source(
                """
                    import * from "sprudel"
                    let lead = note("a").solo()
                    export { lead }
                """.trimIndent()
            )
        }
        val engine = klangScript {
            registerLibrary(sprudelLib)
            registerLibrary(partA)
        }
        val pattern = SprudelPattern.compile(
            engine,
            """
                import * from "partA"
                let mine = note("b").solo()
                stack(lead, mine)
            """.trimIndent(),
        ) ?: error("did not compile")

        val ids = pattern.queryArc(0.0, 1.0).sounding().associate { it.data.note to it.data.patternId }

        ids.keys shouldBe setOf("a", "b")
        ids["a"] shouldNotBe ids["b"]
    }
})
