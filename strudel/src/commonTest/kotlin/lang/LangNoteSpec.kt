package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeEqualIgnoringCase
import io.peekandpoke.klang.strudel.StrudelPattern

class LangNoteSpec : StringSpec({

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
