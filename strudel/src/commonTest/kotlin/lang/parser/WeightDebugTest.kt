package io.peekandpoke.klang.strudel.lang.parser

import io.kotest.core.spec.style.StringSpec
import io.peekandpoke.klang.strudel.lang.note

class WeightDebugTest : StringSpec({

    "Debug bd@2 hh sd@2 hh pattern across multiple cycles" {
        val pattern = note("bd@2 hh sd@2 hh")

        // Query multiple cycles and verify no overlaps
        for (cycle in 0..10) {
            val from = cycle.toDouble()
            val to = from + 1.0
            val events = pattern.queryArc(from, to).sortedBy { it.begin }

            // Check for overlaps
            for (i in 0 until events.size - 1) {
                if (events[i].end > events[i + 1].begin + 1e-10) { // Allow tiny floating-point tolerance
                    throw AssertionError(
                        "Cycle $cycle: OVERLAP detected - ${events[i].data.note} ends at ${events[i].end}, " +
                                "${events[i + 1].data.note} starts at ${events[i + 1].begin}"
                    )
                }
            }
        }
    }
})
