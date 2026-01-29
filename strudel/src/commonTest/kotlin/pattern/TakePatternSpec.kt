package io.peekandpoke.klang.strudel.pattern

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.peekandpoke.klang.strudel.StrudelVoiceData
import io.peekandpoke.klang.strudel.math.Rational.Companion.toRational

class TakePatternSpec : StringSpec({

    "TakePattern keeps only first n steps (stretched)" {
        val source = SequencePattern(
            listOf(
                AtomicPattern(StrudelVoiceData.empty.copy(note = "c")),
                AtomicPattern(StrudelVoiceData.empty.copy(note = "d"))
            )
        )

        // Source has 2 steps: "c" and "d"
        // take(1.5) keeps first 1.5 steps: "c" fully, "d" half
        // These 1.5 steps are stretched to fill each cycle
        val pattern = TakePattern(source, 1.5.toRational())

        // Query 2 cycles - should get repeated pattern of c+half-d
        val events = pattern.queryArc(0.0.toRational(), 2.0.toRational())

        assertSoftly {
            // Per cycle: c (stretched to 0.0-0.667), d (clipped, stretched to 0.667-1.0)
            // Over 2 cycles: 2 c's, 2 d's
            events.filter { it.data.note?.lowercase() == "c" } shouldHaveSize 2
            events.filter { it.data.note?.lowercase() == "d" } shouldHaveSize 2
        }
    }
})
