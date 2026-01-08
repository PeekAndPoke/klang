package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.StrudelPattern

class LangSeqSpec : StringSpec({

    "seq() creates a sequence pattern from multiple values" {
        // Given a sequence of sounds
        val p = seq(sound("bd"), sound("hh"), sound("sn"))

        // When querying one cycle
        val events = p.queryArc(0.0, 1.0)

        // Then we get all three sounds in order, evenly distributed
        events.size shouldBe 3
        events.map { it.data.sound } shouldBe listOf("bd", "hh", "sn")

        // Each event should occupy 1/3 of the cycle
        events[0].begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
        events[0].end.toDouble() shouldBe ((1.0 / 3.0) plusOrMinus EPSILON)
        events[1].begin.toDouble() shouldBe ((1.0 / 3.0) plusOrMinus EPSILON)
        events[1].end.toDouble() shouldBe ((2.0 / 3.0) plusOrMinus EPSILON)
        events[2].begin.toDouble() shouldBe ((2.0 / 3.0) plusOrMinus EPSILON)
        events[2].end.toDouble() shouldBe (1.0 plusOrMinus EPSILON)
    }

    "seq() with empty arguments returns silence" {
        // Given an empty sequence
        val p = seq()

        // When querying one cycle
        val events = p.queryArc(0.0, 1.0)

        // Then we get no events
        events.size shouldBe 0
    }

    "seq() with single element works correctly" {
        // Given a sequence with one element
        val p = seq(sound("bd"))

        // When querying one cycle
        val events = p.queryArc(0.0, 1.0)

        // Then we get one event spanning the full cycle
        events.size shouldBe 1
        events[0].data.sound shouldBe "bd"
        events[0].begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
        events[0].end.toDouble() shouldBe (1.0 plusOrMinus EPSILON)
    }

    "seq() repeats across multiple cycles" {
        // Given a sequence of two sounds
        val p = seq(sound("bd"), sound("sn"))

        // When querying two cycles
        val events = p.queryArc(0.0, 2.0)

        // Then the pattern repeats
        events.size shouldBe 4
        events.map { it.data.sound } shouldBe listOf("bd", "sn", "bd", "sn")
    }

    "seq() can be nested" {
        // Given nested sequences
        val inner = seq(sound("bd"), sound("hh"))
        val p = seq(inner, sound("sn"))

        // When querying one cycle
        val events = p.queryArc(0.0, 1.0)

        // Then inner sequence takes first half, sn takes second half
        events.size shouldBe 3
        events.map { it.data.sound } shouldBe listOf("bd", "hh", "sn")

        // bd and hh should each take 1/4 of the cycle (half of the first half)
        events[0].end.toDouble() shouldBe ((1.0 / 4.0) plusOrMinus EPSILON)
        events[1].begin.toDouble() shouldBe ((1.0 / 4.0) plusOrMinus EPSILON)
        events[1].end.toDouble() shouldBe ((1.0 / 2.0) plusOrMinus EPSILON)
        // sn takes the second half
        events[2].begin.toDouble() shouldBe ((1.0 / 2.0) plusOrMinus EPSILON)
        events[2].end.toDouble() shouldBe (1.0 plusOrMinus EPSILON)
    }

    "seq() works within compiled code" {
        val p = StrudelPattern.compile("""seq(sound("bd"), sound("hh"), sound("sn"))""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 3
        events.map { it.data.sound } shouldBe listOf("bd", "hh", "sn")
        events[0].begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
        events[2].end.toDouble() shouldBe (1.0 plusOrMinus EPSILON)
    }
})
