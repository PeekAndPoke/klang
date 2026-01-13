package io.peekandpoke.klang.strudel.pattern

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeSameSizeAs
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.lang.note
import io.peekandpoke.klang.strudel.lang.seq

class SequencePatternSpec : StringSpec({

    "SequencePattern: Direct Instantiation" {
        val p1 = AtomicPattern(VoiceData.empty.copy(note = "a"))
        val p2 = AtomicPattern(VoiceData.empty.copy(note = "b"))
        val pattern = SequencePattern(listOf(p1, p2))

        verifyPattern(pattern, 2) { i, note, begin, dur ->
            if (i == 0) {
                note shouldBe "a"
                begin shouldBe (0.0 plusOrMinus EPSILON)
                dur shouldBe (0.5 plusOrMinus EPSILON)
            } else {
                note shouldBe "b"
                begin shouldBe (0.5 plusOrMinus EPSILON)
                dur shouldBe (0.5 plusOrMinus EPSILON)
            }
        }
    }

    "SequencePattern: Kotlin DSL (seq function)" {
        val pattern = seq(note("a"), note("b"))

        verifyPattern(pattern, 2) { i, note, begin, dur ->
            if (i == 0) {
                note shouldBe "a"
                begin shouldBe (0.0 plusOrMinus EPSILON)
                dur shouldBe (0.5 plusOrMinus EPSILON)
            } else {
                note shouldBe "b"
                begin shouldBe (0.5 plusOrMinus EPSILON)
                dur shouldBe (0.5 plusOrMinus EPSILON)
            }
        }
    }

    "SequencePattern: Kotlin DSL (mini-notation space)" {
        val pattern = note("a b")

        verifyPattern(pattern, 2) { i, note, begin, dur ->
            if (i == 0) {
                note shouldBe "a"
                begin shouldBe (0.0 plusOrMinus EPSILON)
                dur shouldBe (0.5 plusOrMinus EPSILON)
            } else {
                note shouldBe "b"
                begin shouldBe (0.5 plusOrMinus EPSILON)
                dur shouldBe (0.5 plusOrMinus EPSILON)
            }
        }
    }

    "SequencePattern: Compiled Code" {
        val pattern = StrudelPattern.compile("""note("a b")""")

        verifyPattern(pattern, 2) { i, note, begin, dur ->
            if (i == 0) {
                note shouldBe "a"
                begin shouldBe (0.0 plusOrMinus EPSILON)
                dur shouldBe (0.5 plusOrMinus EPSILON)
            } else {
                note shouldBe "b"
                begin shouldBe (0.5 plusOrMinus EPSILON)
                dur shouldBe (0.5 plusOrMinus EPSILON)
            }
        }
    }

    "SequencePattern: querying by cycles must always return the same events" {
        val patterns = listOf(
            """seq("1")""",
            """seq("1 2")""",
            """seq("1 2 3")""",
            """seq("1 2 3 4")""",
            """seq("1 2 3 4 5")""",
            """seq("1 2 3 4 5 6")""",
            """seq("1 2 3 4 5 6 7")""",
            """seq("1 2 3 4 5 6 7 8")""",
            """seq("1 2 3 4 5 6 7 8 9")""",
            """seq("1 2 3 4 5 6 7 8 9 10")""",
            """seq("1 2 3 4 5 6 7 8 9 10 11")""",
            """seq("1 2 3 4 5 6 7 8 9 10 11 12")""",
            """seq("1 2 3 4 5 6 7 8 9 10 11 12 13")""",
            """seq("1 2 3 4 5 6 7 8 9 10 11 12 13 14")""",
            """seq("1 2 3 4 5 6 7 8 9 10 11 12 13 14 15")""",
            """seq("1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16")""",
            """seq("1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17")""",
            """seq("1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18")""",
            """seq("1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19")""",
            """seq("1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20")""",
            """seq("1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20 21")""",
            """seq("1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20 21 22")""",
            """seq("1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20 21 22 23")""",
            """seq("1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24")""",
        ).map { it to StrudelPattern.compile(it)!! }

        patterns.forEach { (code, pattern) ->
            val firstArc = pattern.queryArc(0.0, 1.0)
            val firstArcData = firstArc.map { it.data }

            (1..100).forEach { round ->
                val from = round.toDouble()
                val to = from + 1.0

                withClue("Round $round | query: $from - $to | pattern: $code") {
                    val secondArc = pattern.queryArc(from, to)
                    val secondArcData = secondArc.map { it.data }

                    firstArcData shouldBeSameSizeAs secondArcData
                    firstArcData shouldBe secondArcData
                }
            }
        }
    }
})
