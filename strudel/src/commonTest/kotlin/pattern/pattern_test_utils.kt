package io.peekandpoke.klang.strudel.pattern

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.StrudelPattern

// Helper to verify pattern output
fun verifyPattern(
    pattern: StrudelPattern?,
    expectedCount: Int,
    check: (index: Int, note: String?, begin: Double, dur: Double) -> Unit,
) {
    pattern.shouldNotBeNull()

    val events = pattern.queryArc(0.0, 1.0).sortedBy { it.begin }

    events.size shouldBe expectedCount

    events.forEachIndexed { index, event ->
        check(
            index,
            event.data.note,
            event.begin.toDouble(),
            event.dur.toDouble(),
        )
    }
}
