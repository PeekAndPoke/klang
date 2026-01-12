package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.StrudelPattern

class LangArrangeSpec : StringSpec({

    "arrange() with simple patterns defaults to 1 cycle each" {
        // Given two patterns without duration specification
        val p = arrange(sound("bd"), sound("hh"))

        // When querying two cycles
        val events = p.queryArc(0.0, 2.0).sortedBy { it.begin }

        // Then each pattern takes 1 cycle
        events.size shouldBe 2
        events[0].data.sound shouldBe "bd"
        events[0].begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
        events[0].end.toDouble() shouldBe (1.0 plusOrMinus EPSILON)

        events[1].data.sound shouldBe "hh"
        events[1].begin.toDouble() shouldBe (1.0 plusOrMinus EPSILON)
        events[1].end.toDouble() shouldBe (2.0 plusOrMinus EPSILON)
    }

    "arrange() with duration specification [2, pattern]" {
        // Given a pattern that should play for 2 cycles
        val p = arrange(listOf(2, sound("bd")), sound("hh"))

        // When querying three cycles
        val events = p.queryArc(0.0, 3.0).sortedBy { it.begin }

        // Then bd plays for 2 cycles, hh for 1
        events.size shouldBe 3

        // First two cycles: bd
        events[0].data.sound shouldBe "bd"
        events[0].begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
        events[0].end.toDouble() shouldBe (1.0 plusOrMinus EPSILON)
        events[1].data.sound shouldBe "bd"
        events[1].begin.toDouble() shouldBe (1.0 plusOrMinus EPSILON)
        events[1].end.toDouble() shouldBe (2.0 plusOrMinus EPSILON)

        // Third cycle: hh
        events[2].data.sound shouldBe "hh"
        events[2].begin.toDouble() shouldBe (2.0 plusOrMinus EPSILON)
        events[2].end.toDouble() shouldBe (3.0 plusOrMinus EPSILON)
    }

    "arrange() works as method on StrudelPattern" {
        // sound("bd").arrange(sound("hh")) -> arrange(sound("bd"), sound("hh"))
        val p = sound("bd").arrange(sound("hh"))

        val events = p.queryArc(0.0, 2.0).sortedBy { it.begin }

        events.size shouldBe 2
        events[0].data.sound shouldBe "bd"
        events[1].data.sound shouldBe "hh"
    }

    "arrange() works as extension on String" {
        // "bd".arrange("hh") -> arrange(sound("bd"), sound("hh"))?
        // Wait, "bd" as pattern via defaultModifier goes to 'note' or 'value'.
        // But `sound("bd")` puts it in `sound`.
        // If we use `"bd".arrange("hh")`, "bd" becomes a pattern via defaultModifier (note/value).
        // So we check for note/value.

        val p = "bd".arrange("hh")

        val events = p.queryArc(0.0, 2.0).sortedBy { it.begin }

        events.size shouldBe 2
        // "bd" -> value="bd" (as VoiceValue.Text)
        // "hh" -> value="hh"
        // Note: this assumes "bd" isn't parsed as sound("bd") automatically unless we use sound("bd").

        // Let's check what defaultModifier does. It sets note and value.
        // So we check note.
        events[0].data.value?.asString shouldBe "bd"
        events[1].data.value?.asString shouldBe "bd"
    }

    "arrange() works in compiled code" {
        val p = StrudelPattern.compile("""arrange(sound("bd"), sound("hh"))""")
        val events = p?.queryArc(0.0, 2.0)?.sortedBy { it.begin } ?: emptyList()

        events.size shouldBe 2
        events[0].data.sound shouldBe "bd"
        events[1].data.sound shouldBe "hh"
    }

    "arrange() works as method in compiled code" {
        val p = StrudelPattern.compile("""sound("bd").arrange(sound("hh"))""")
        val events = p?.queryArc(0.0, 2.0)?.sortedBy { it.begin } ?: emptyList()

        events.size shouldBe 2
        events[0].data.sound shouldBe "bd"
        events[1].data.sound shouldBe "hh"
    }
})
