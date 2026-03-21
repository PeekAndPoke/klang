package io.peekandpoke.klang.sprudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.sprudel.EPSILON
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.dslInterfaceTests

class LangApplyNSpec : StringSpec({

    "applyN dsl interface" {
        val pat = "a b"
        val transform: PatternMapperFn = { it.fast(2) }
        dslInterfaceTests(
            "pattern.applyN(2, fn)" to note(pat).applyN(2, transform),
            "script pattern.applyN(2, fn)" to SprudelPattern.compile("""note("$pat").applyN(2, x => x.fast(2))"""),
            "string.applyN(2, fn)" to pat.applyN(2, transform),
            "script string.applyN(2, fn)" to SprudelPattern.compile(""""$pat".applyN(2, x => x.fast(2))"""),
            "applyN(2, fn)" to note(pat).apply(applyN(2, transform)),
            "script applyN(2, fn)" to SprudelPattern.compile("""note("$pat").apply(applyN(2, x => x.fast(2)))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events.size shouldBe 8  // fast(2) applied twice = fast(4), 2 notes × 4 = 8
        }
    }

    "applyN() applies function n times" {
        // Apply fast(2) three times: fast(2).fast(2).fast(2) = fast(8)
        val pattern = note("a").applyN(3) { it.fast(2) }
        val events = pattern.queryArc(0.0, 1.0)

        // Should have 8 events in one cycle (2^3)
        events.size shouldBe 8
    }

    "applyN() with n=0 returns pattern unchanged" {
        val pattern = note("a b").applyN(0) { it.fast(2) }
        val events = pattern.queryArc(0.0, 1.0)

        // Should have 2 events (unchanged)
        events.size shouldBe 2
    }

    "applyN() with n=1 applies function once" {
        val pattern = note("a b").applyN(1) { it.fast(2) }
        val events = pattern.queryArc(0.0, 1.0)

        // Should have 4 events (applied once)
        events.size shouldBe 4
    }

    "applyN() with rev function" {
        // Apply rev twice should return to original
        val original = note("a b c")
        val doubled = original.applyN(2) { it.rev() }

        val originalEvents = original.queryArc(0.0, 1.0).sortedBy { it.part.begin }
        val doubledEvents = doubled.queryArc(0.0, 1.0).sortedBy { it.part.begin }

        originalEvents.size shouldBe doubledEvents.size
        originalEvents.forEachIndexed { i, event ->
            event.data.note shouldBe doubledEvents[i].data.note
            event.part.begin.toDouble() shouldBe (doubledEvents[i].part.begin.toDouble() plusOrMinus EPSILON)
        }
    }

    "applyN() with control pattern for n" {
        // Use "2 1" as control pattern for n
        val pattern = seq("1").applyN("2 1") { it.add(1) }
        val events = pattern.queryArc(0.0, 1.0).sortedBy { it.part.begin }

        // First half: n=2, add(1) twice: 1 + 1 + 1 = 3
        // Second half: n=1, add(1) once: 1 + 1 = 2
        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 3 // First half
        events[1].data.value?.asInt shouldBe 2 // Second half
    }

    "applyN() works as PatternMapperFn" {
        val pattern = note("a").apply(applyN(2) { p: SprudelPattern -> p.fast(2) })
        val events = pattern.queryArc(0.0, 1.0)

        // Should have 4 events (fast(2) applied twice)
        events.size shouldBe 4
    }

    "applyN() works as string extension" {
        val pattern = "a b".applyN(2) { it.fast(2) }
        val events = pattern.queryArc(0.0, 1.0)

        // Should have 8 events
        events.size shouldBe 8
    }

    "applyN() works in compiled code" {
        val pattern = SprudelPattern.compile("""note("a").applyN(2, x => x.fast(2))""")
        val events = pattern?.queryArc(0.0, 1.0) ?: emptyList()

        // Should have 4 events
        events.size shouldBe 4
    }

    "applyN() with slow function" {
        val pattern = note("a b c d").applyN(2) { it.slow(2) }
        val events = pattern.queryArc(0.0, 1.0)

        // slow(2).slow(2) = slow(4)
        // Only first event should be in cycle 0
        events.size shouldBe 1
        events[0].data.note shouldBe "a"
    }

    "applyN() with different transformations" {
        val pattern = note("c d e").applyN(2) { it.fast(2) }
        val events = pattern.queryArc(0.0, 1.0)

        // fast(2) applied twice = fast(4)
        // Original has 3 events per cycle, after fast(4) should have 12
        events.size shouldBe 12
    }
})
