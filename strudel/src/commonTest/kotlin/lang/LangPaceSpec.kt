package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.dslInterfaceTests

class LangPaceSpec : StringSpec({

    "pace dsl interface" {
        val pat = "a b"
        val ctrl = "4"
        dslInterfaceTests(
            "pattern.pace(ctrl)" to note(pat).pace(ctrl),
            "script pattern.pace(ctrl)" to StrudelPattern.compile("""note("$pat").pace("$ctrl")"""),
            "string.pace(ctrl)" to pat.pace(ctrl),
            "script string.pace(ctrl)" to StrudelPattern.compile(""""$pat".pace("$ctrl")"""),
            "pace(ctrl)" to note(pat).apply(pace(ctrl)),
            "script pace(ctrl)" to StrudelPattern.compile("""note("$pat").apply(pace("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
        }
    }

    "steps dsl interface" {
        val pat = "a b"
        val ctrl = "4"
        dslInterfaceTests(
            "pattern.steps(ctrl)" to note(pat).steps(ctrl),
            "script pattern.steps(ctrl)" to StrudelPattern.compile("""note("$pat").steps("$ctrl")"""),
            "string.steps(ctrl)" to pat.steps(ctrl),
            "script string.steps(ctrl)" to StrudelPattern.compile(""""$pat".steps("$ctrl")"""),
            "steps(ctrl)" to note(pat).apply(steps(ctrl)),
            "script steps(ctrl)" to StrudelPattern.compile("""note("$pat").apply(steps("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
        }
    }

    "pace() adjusts speed to match target steps" {
        // Pattern with 4 steps, pace to 8 should double speed
        val p = note("c d e f").pace(8)
        val events = p.queryArc(0.0, 1.0)

        // Should have more events (2x speed = 2 cycles in 1)
        events.size shouldBeGreaterThan 4
    }

    "steps() is alias for pace()" {
        val p1 = note("c d e f").pace(8)
        val p2 = note("c d e f").steps(8)

        val events1 = p1.queryArc(0.0, 1.0)
        val events2 = p2.queryArc(0.0, 1.0)

        events1.size shouldBe events2.size
    }
})
