package io.peekandpoke.klang.strudel.pattern

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.lang.arrange
import io.peekandpoke.klang.strudel.lang.note

class ArrangementPatternSpec : StringSpec({

    "ArrangementPattern: Direct Instantiation" {
        val p1 = AtomicPattern(VoiceData.empty.copy(note = "a"))
        val p2 = AtomicPattern(VoiceData.empty.copy(note = "b"))
        // Arrange p1 for 2 cycles, then p2 for 1 cycle. Total = 3 cycles.
        val pattern = ArrangementPattern(listOf(2.0 to p1, 1.0 to p2))

        // Total duration = 3.0.
        // p1 (Atomic) plays at relative 0.0 and 1.0.
        // p2 (Atomic) plays at relative 0.0 (which is absolute 2.0).
        val events = pattern.queryArc(0.0, 3.0).sortedBy { it.begin }

        events.size shouldBe 3

        events[0].data.note shouldBe "a"
        events[0].begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)

        events[1].data.note shouldBe "a"
        events[1].begin.toDouble() shouldBe (1.0 plusOrMinus EPSILON)

        events[2].data.note shouldBe "b"
        events[2].begin.toDouble() shouldBe (2.0 plusOrMinus EPSILON)
    }

    "ArrangementPattern: Kotlin DSL (arrange function)" {
        // arrange([2, note("a")], note("b"))
        val pattern = arrange(listOf(2.0, note("a")), note("b"))

        val events = pattern.queryArc(0.0, 3.0).sortedBy { it.begin }

        events.size shouldBe 3
        events[0].data.note shouldBe "a"
        events[1].data.note shouldBe "a"
        events[2].data.note shouldBe "b"
    }

    "ArrangementPattern: Compiled Code" {
        val pattern = StrudelPattern.compile("""arrange([2, note("a")], note("b"))""")

        pattern.shouldNotBeNull()
        val events = pattern.queryArc(0.0, 3.0).sortedBy { it.begin }

        events.size shouldBe 3
        events[0].data.note shouldBe "a"
        events[1].data.note shouldBe "a"
        events[2].data.note shouldBe "b"
    }
})
