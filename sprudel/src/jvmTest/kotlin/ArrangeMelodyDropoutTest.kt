package io.peekandpoke.klang.sprudel

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * Regression test: swing() caused melody dropout at large cycle numbers.
 *
 * Root cause: the old `inside(n, lateInCycle().stretchBy())` implementation
 * amplified time shifts at large cycle numbers, causing events to land outside
 * the query window after fast(n) scaling. Fixed by introducing a dedicated SwingPattern.
 */
class ArrangeMelodyDropoutTest : StringSpec({

    fun countOnsetDropouts(pattern: SprudelPattern, totalCycles: Int): List<Int> {
        val dropouts = mutableListOf<Int>()
        for (cycle in 0 until totalCycles) {
            val events = pattern.queryArc(cycle.toDouble(), cycle + 1.0)
            val onsets = events.count { it.isOnset }
            if (onsets == 0) dropouts.add(cycle)
        }
        return dropouts
    }

    "swing alone does not drop events at large cycle numbers" {
        val pattern = SprudelPattern.compile("""note("<a b c d>").swing(0.05)""") ?: error("Failed")
        val dropouts = countOnsetDropouts(pattern, 40)
        dropouts shouldBe emptyList()
    }

    "swingBy alone does not drop events at large cycle numbers" {
        val pattern = SprudelPattern.compile("""note("<a b c d>").swingBy(0.5, 2)""") ?: error("Failed")
        val dropouts = countOnsetDropouts(pattern, 40)
        dropouts shouldBe emptyList()
    }

    "arrange + swing does not drop melody across multiple loops" {
        val code = """
let mel = note("<a b c d>").swing(0.05).sound("mel")
arrange([4, mel])
        """.trimIndent()
        val pattern = SprudelPattern.compile(code) ?: error("Failed to compile")
        val dropouts = countOnsetDropouts(pattern, 20)
        dropouts shouldBe emptyList()
    }

    "full composition with swing does not drop melody across 2 loops" {
        val code = """
let melody1 = note("<[d4 f4 e4 d4] [c4 a4 g4 f4] [d4 g4 f4 e4] [c4 bb4 a4 g4] [d5 c5 bb4 a4] [g4 e4 d4 f4] [a4 g4 f4 e4] [d4 c4 d4 ~]>")
    .sound("blockfloete").gain(0.28).swing(0.05).orbit(0)
let guitar1 = note("<[d3 ~ a3 ~ d4 ~ f4 ~] [g2 ~ d3 ~ g3 ~ bb3 ~] [c3 ~ g3 ~ c4 ~ e4 ~] [f2 ~ c3 ~ f3 ~ a3 ~]>").fast(2)
    .sound("fingerpick").gain(0.3).legato(0.8).orbit(1)
let bass1 = note("<[d2@2 ~ ~] [g2@2 ~ ~] [c2@2 ~ ~] [f2@2 ~ ~]>")
    .sound("contrabass").gain(0.25).legato(0.9).orbit(2)

let melody2 = note("<[a5 c6 b5 a5] [d6 c6 a5 g5] [bb5 a5 g5 f5] [g5 e5 c5 a5]>")
    .sound("blockfloete").gain(0.28).swing(0.05).orbit(0)
let guitar2 = note("<[a3 e4 c4 a3 e4 c4 a3 e4] [d4 a4 f4 d4 a4 f4 d4 a4]>").fast(2)
    .sound("fingerpick").gain(0.3).legato(0.8).orbit(1)
let bass2 = note("<[a2@2 ~ ~] [d2@2 ~ ~]>")
    .sound("contrabass").gain(0.25).legato(0.9).orbit(2)

let part1 = stack(melody1, guitar1, bass1)
let part2 = stack(melody2, guitar2, bass2)

arrange([8, part1], [8, part2], [8, part1], [8, part2])
        """.trimIndent()

        val pattern = SprudelPattern.compile(code) ?: error("Failed to compile")

        val totalCycles = 64
        val dropouts = mutableListOf<String>()

        for (cycle in 0 until totalCycles) {
            val events = pattern.queryArc(cycle.toDouble(), cycle + 1.0)
            val sounds = events.filter { it.isOnset }.groupBy { it.data.sound ?: "unknown" }

            val melodyCount = sounds["blockfloete"]?.size ?: 0
            val guitarCount = sounds["fingerpick"]?.size ?: 0
            val bassCount = sounds["contrabass"]?.size ?: 0

            if (melodyCount == 0) dropouts.add("Cycle $cycle: MELODY")
            if (guitarCount == 0) dropouts.add("Cycle $cycle: GUITAR")
            if (bassCount == 0) dropouts.add("Cycle $cycle: BASS")
        }

        dropouts shouldBe emptyList()
    }
})
