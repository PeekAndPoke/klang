package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeEqualIgnoringCase
import io.peekandpoke.klang.audio_bridge.VoiceValue
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.StrudelPattern

class LangStackSpec : StringSpec({

    "stack() plays patterns simultaneously" {
        val p = stack(
            note("a"),
            note("b")
        )

        val events = p.queryArc(0.0, 1.0).sortedBy { it.data.note }

        events.size shouldBe 2
        events[0].data.note shouldBeEqualIgnoringCase "a"
        events[0].begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
        events[0].end.toDouble() shouldBe (1.0 plusOrMinus EPSILON)

        events[1].data.note shouldBeEqualIgnoringCase "b"
        events[1].begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
        events[1].end.toDouble() shouldBe (1.0 plusOrMinus EPSILON)
    }

    "stack() works as method on StrudelPattern" {
        // note("a").stack(note("b")) -> stack(note("a"), note("b"))
        val p = note("a").stack(note("b"))

        val events = p.queryArc(0.0, 1.0).sortedBy { it.data.note }

        events.size shouldBe 2
        events[0].data.note shouldBeEqualIgnoringCase "a"
        events[1].data.note shouldBeEqualIgnoringCase "b"
    }

    "stack() works as extension on String" {
        // "a".stack("b") -> stack(note("a"), value("b")) -> note("a") + value("b")
        val p = "a".stack("b")

        val events = p.queryArc(0.0, 1.0).sortedBy { it.data.note ?: "z" }

        events.size shouldBe 2
        // "a" via defaultModifier -> value="a", note="a" (if interpreted as such)
        // stack implementation usually uses defaultModifier for args
        // "a".stack(...) -> "a" is receiver.
        // If "a" is patternified via defaultModifier -> value="a", note="a".

        // Check what we get.
        // First event (from "a")
        // Second event (from "b")

        // Since sort by note, "a" comes first.
        events[0].data.value shouldBe VoiceValue.Text("a")
        events[1].data.value shouldBe VoiceValue.Text("b")
    }

    "stack() works in compiled code" {
        val p = StrudelPattern.compile("""stack(note("a"), note("b"))""")
        val events = p?.queryArc(0.0, 1.0)?.sortedBy { it.data.note } ?: emptyList()

        events.size shouldBe 2
        events[0].data.note shouldBeEqualIgnoringCase "a"
        events[1].data.note shouldBeEqualIgnoringCase "b"
    }

    "stack() works as method in compiled code" {
        val p = StrudelPattern.compile("""note("a").stack(note("b"))""")
        val events = p?.queryArc(0.0, 1.0)?.sortedBy { it.data.note } ?: emptyList()

        events.size shouldBe 2
        events[0].data.note shouldBeEqualIgnoringCase "a"
        events[1].data.note shouldBeEqualIgnoringCase "b"
    }

    "stack() works as string extension in compiled code" {
        val p = StrudelPattern.compile(""""a".stack("b")""")
        val events = p?.queryArc(0.0, 1.0)?.sortedBy { it.data.note ?: "z" } ?: emptyList()

        events.size shouldBe 2
        events[0].data.value shouldBe VoiceValue.Text("a")
        events[1].data.value shouldBe VoiceValue.Text("b")
    }
})
