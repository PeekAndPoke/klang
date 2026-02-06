package io.peekandpoke.klang.strudel.lang

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeEqualIgnoringCase
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.math.Rational
import io.peekandpoke.klang.strudel.math.Rational.Companion.toRational
import io.peekandpoke.klang.tones.Tones

class LangNoteSpec : StringSpec({

    "note() handles numerical frequencies correctly" {
        val p = note("60 72.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.note shouldBe "60"
        events[0].data.freqHz shouldBe Tones.midiToFreq(60.0)

        events[1].data.note shouldBe "72.5"
        events[1].data.freqHz shouldBe Tones.midiToFreq(72.5)
    }

    "note() handles continuous values combined with sequence over multiple cycles" {
        // seq("50 60") -> 0..0.5 = 50, 0.5..1 = 60
        // saw.range(0, 1).slow(4) -> linear ramp from 0 to 1 over 4 cycles
        val p = seq("50 60")
            .add(saw.range(0.0, 1.0).slow(4).segment(2)).note()

        assertSoftly {
            // Check over 12 cycles
            // The pattern has a period of 4 cycles (due to slow(4))
            // So at cycle 0, 4, 8 it should be the same
            for (cycle in 0 until 12) {

                withClue("Cycle $cycle") {
                    val cycleDbl = cycle.toDouble()
                    val events = p.queryArc(cycleDbl, cycleDbl + 1.0)
                    val offset = (cycle % 4) / 4.0

                    // Event 1 at start of cycle
                    // base = 50
                    // saw value = offset
                    // expected = 50 + offset
                    val e1 = events.find { it.part.begin == cycle.toRational() }!!
                    val expectedNote1 = 50.0 + offset

                    // We compare approximate frequency because float parsing in note() might have slight differences vs direct calculation
                    // But here we constructed the string via logic in Strudel, so it might be exact string "50.0" etc.
                    // Actually, .add() results in a Double value, which .note() converts to string.

                    e1.data.note?.toDouble() shouldBe expectedNote1
                    e1.data.freqHz shouldBe Tones.midiToFreq(expectedNote1)

                    // Event 2 at middle of cycle
                    // base = 60
                    // saw value at offset + 0.125 (since half cycle = 1/8 of 4-cycle period)
                    // expected = 60 + offset + 0.125
                    val e2 = events.find { it.part.begin == cycle.toRational() + Rational.HALF }!!
                    val expectedNote2 = 60.0 + offset + 0.125

                    e2.data.note?.toDouble() shouldBe expectedNote2
                    e2.data.freqHz shouldBe Tones.midiToFreq(expectedNote2)
                }
            }
        }
    }

    "top-level note() sets VoiceData.note correctly" {
        val p = note("c3 g3")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.note?.lowercase() } shouldBe listOf("c3", "g3")
    }

    "control pattern note() sets note on existing pattern" {
        val base = s("bd bd")
        val p = base.note("a4 b4")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.note?.lowercase() } shouldBe listOf("a4", "b4")
    }

    "note() works as string extension" {
        // "c3".note("e3") should parse "c3", then apply note "e3" -> result is "e3"
        val p = "c3".note("e3")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.note shouldBeEqualIgnoringCase "e3"
    }

    "note() re-interprets value/index + scale when called without args" {
        // seq("0").scale("C4:minor").note()
        // Should behave like n() with re-interpretation logic
        val p = seq("0 2").scale("C4:minor").note()

        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        // 0 -> C4
        events[0].data.note shouldBeEqualIgnoringCase "C4"
        // 2 -> Eb4 (minor third)
        events[1].data.note shouldBeEqualIgnoringCase "Eb4"
    }

    "note() re-interpretation uses existing note/value as fallback if no scale" {
        // seq("a3").note() -> re-interprets value "a3" as note?
        // seq("a3") sets value="a3".
        // note() calls resolveNote.
        // resolveNote: n = null (since "a3" not int).
        // Fallback case B: fallbackNote = note ?: value.toString().
        // So it sets note="a3".

        val p = seq("a3").note()
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 1
        events[0].data.note shouldBeEqualIgnoringCase "A3"
    }

    "note().note() is idempotent (chained re-interpretation)" {
        // seq("0").scale("C4:major").note() -> resolves to C4.
        // .note() again -> resolves again.
        // First resolve: note="C4", soundIndex=null, value=null.
        // Second resolve: n=null. Fallback B: fallbackNote = note ("C4").
        // Result: note="C4". Same.

        val p1 = seq("0").scale("C4:major").note()
        val p2 = seq("0").scale("C4:major").note().note()

        val events1 = p1.queryArc(0.0, 1.0)
        val events2 = p2.queryArc(0.0, 1.0)

        events1.size shouldBe 1
        events2.size shouldBe 1
        events1[0].data.note shouldBeEqualIgnoringCase "C4"
        events2[0].data.note shouldBeEqualIgnoringCase "C4"
    }

    "note() works within compiled code" {
        val p = StrudelPattern.compile("""note("c3 e3")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.note?.lowercase() } shouldBe listOf("c3", "e3")
    }

    "note() re-interpretation works within compiled code" {
        val p = StrudelPattern.compile("""seq("0 2").scale("C4:minor").note()""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events[0].data.note shouldBeEqualIgnoringCase "C4"
        events[1].data.note shouldBeEqualIgnoringCase "Eb4"
    }
})
