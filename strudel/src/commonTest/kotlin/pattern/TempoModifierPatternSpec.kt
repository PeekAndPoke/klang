package io.peekandpoke.klang.strudel.pattern

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeEqualIgnoringCase
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.lang.fast
import io.peekandpoke.klang.strudel.lang.note
import io.peekandpoke.klang.strudel.lang.slow

class TempoModifierPatternSpec : StringSpec({

    "TempoModifierPattern: Direct Instantiation (slow 2)" {
        val inner = AtomicPattern(VoiceData.empty.copy(note = "a"))
        val pattern = TempoModifierPattern(inner, 2.0)

        // a normally is 0..1. slow(2) makes it 0..2.
        val events = pattern.queryArc(0.0, 2.0).sortedBy { it.begin }

        events.size shouldBe 1
        events[0].data.note shouldBe "a"
        events[0].begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
        events[0].dur.toDouble() shouldBe (2.0 plusOrMinus EPSILON)

        // Weight should be delegated
        pattern.weight shouldBe inner.weight
    }

    "TempoModifierPattern: Kotlin DSL (slow)" {
        // [a b] takes 1 cycle. slow(2) makes it take 2 cycles.
        val pattern = note("a b").slow(2)

        val events = pattern.queryArc(0.0, 2.0).sortedBy { it.begin }

        events.size shouldBe 2
        events[0].data.note shouldBeEqualIgnoringCase "a"
        events[0].begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
        events[0].dur.toDouble() shouldBe (1.0 plusOrMinus EPSILON)

        events[1].data.note shouldBeEqualIgnoringCase "b"
        events[1].begin.toDouble() shouldBe (1.0 plusOrMinus EPSILON)
        events[1].dur.toDouble() shouldBe (1.0 plusOrMinus EPSILON)
    }

    "TempoModifierPattern: Kotlin DSL (fast)" {
        // [a b] takes 1 cycle. fast(2) makes it take 0.5 cycles.
        val pattern = note("a b").fast(2)

        val events = pattern.queryArc(0.0, 1.0).sortedBy { it.begin }

        // Should play twice in one cycle
        events.size shouldBe 4

        // 1st iteration
        events[0].data.note shouldBeEqualIgnoringCase "a"
        events[0].dur.toDouble() shouldBe (0.25 plusOrMinus EPSILON)
        events[1].data.note shouldBeEqualIgnoringCase "b"
        events[1].dur.toDouble() shouldBe (0.25 plusOrMinus EPSILON)

        // 2nd iteration
        events[2].data.note shouldBeEqualIgnoringCase "a"
        events[2].begin.toDouble() shouldBe (0.5 plusOrMinus EPSILON)
        events[3].data.note shouldBeEqualIgnoringCase "b"
        events[3].begin.toDouble() shouldBe (0.75 plusOrMinus EPSILON)
    }

    "TempoModifierPattern: Compiled Code" {
        val pattern = StrudelPattern.compile("""note("a").slow(4)""")

        pattern.shouldNotBeNull()
        val events = pattern.queryArc(0.0, 4.0)

        events.size shouldBe 1
        events[0].dur.toDouble() shouldBe (4.0 plusOrMinus EPSILON)
    }

    "TempoModifierPattern: Weight preservation" {
        val inner = note("a@5")
        val pattern = inner.slow(2)

        pattern.weight shouldBe (5.0 plusOrMinus EPSILON)
    }
})
