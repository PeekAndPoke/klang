package io.peekandpoke.klang.strudel.pattern

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.StrudelVoiceData
import io.peekandpoke.klang.strudel.math.Rational.Companion.toRational

class RepeatCyclesPatternSpec : StringSpec({

    "RepeatCyclesPattern repeats each cycle n times" {
        val source = AtomicPattern(StrudelVoiceData.empty.copy(note = "c"))

        val pattern = RepeatCyclesPattern(source, 2.0.toRational())

        // For a static pattern (every cycle is "c"), repeatCycles acts as identity
        // Query 4 cycles - should get all 4 (not stop after 2)
        val events = pattern.queryArc(0.0.toRational(), 4.0.toRational())

        events shouldHaveSize 4  // All 4 cycles
        events.all { it.data.note == "c" } shouldBe true
    }
})
