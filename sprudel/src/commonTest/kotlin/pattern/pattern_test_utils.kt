package io.peekandpoke.klang.sprudel.pattern

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.sprudel.SprudelPattern

// Helper to verify pattern output
fun verifyPattern(
    pattern: SprudelPattern?,
    expectedCount: Int,
    check: (index: Int, note: String?, begin: Double, dur: Double) -> Unit,
) {
    pattern.shouldNotBeNull()

    val events = pattern.queryArc(0.0, 1.0).sortedBy { it.part.begin }

    events.size shouldBe expectedCount

    events.forEachIndexed { index, event ->
        check(
            index,
            event.data.note,
            event.part.begin.toDouble(),
            event.part.duration.toDouble(),
        )
    }
}
