package io.peekandpoke.klang.strudel.pattern

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeEqualIgnoringCase
import io.peekandpoke.klang.strudel.lang.note
import io.peekandpoke.klang.strudel.lang.seq
import io.peekandpoke.klang.strudel.lang.silence
import io.peekandpoke.klang.strudel.map

/**
 * Tests for MapPattern - Generic pattern for event list transformations.
 */
class MapPatternSpec : StringSpec({

    "MapPattern should delegate weight, steps, and cycle duration to source" {
        val source = seq("a", "b", "c")
        val pattern = source.map { events -> events }

        pattern.weight shouldBe source.weight
        pattern.steps shouldBe source.steps
        pattern.estimateCycleDuration() shouldBe source.estimateCycleDuration()
    }

    "MapPattern should transform event list" {
        val source = seq("a", "b", "c")
        val pattern = source.map { events ->
            events.map { it.copy(data = it.data.copy(note = "x")) }
        }

        val events = pattern.queryArc(0.0, 1.0)
        events shouldHaveSize 3
        events.forEach { it.data.note shouldBe "x" }
    }

    "MapPattern should support filtering events" {
        val source = seq("a", "b", "c", "d")
        val pattern = source.map { events ->
            events.filterIndexed { index, _ -> index % 2 == 0 }
        }

        val events = pattern.queryArc(0.0, 1.0)
        events shouldHaveSize 2
        events[0].data.value?.asString shouldBe "a"
        events[1].data.value?.asString shouldBe "c"
    }

    "MapPattern should support sorting events" {
        val source = note("c", "a", "b")
        val pattern = source.map { events ->
            events.sortedBy { it.data.note }
        }

        val events = pattern.queryArc(0.0, 1.0)
        events shouldHaveSize 3
        events[0].data.note shouldBeEqualIgnoringCase "a"
        events[1].data.note shouldBeEqualIgnoringCase "b"
        events[2].data.note shouldBeEqualIgnoringCase "c"
    }

    "MapPattern should support adding events" {
        val source = note("a")
        val pattern = source.map { events ->
            events + events.map { it.copy(data = it.data.copy(note = "b")) }
        }

        val events = pattern.queryArc(0.0, 1.0)
        events shouldHaveSize 2
        events[0].data.note shouldBe "a"
        events[1].data.note shouldBe "b"
    }

    "MapPattern should support removing all events" {
        val source = seq("a", "b", "c")
        val pattern = source.map { emptyList() }

        val events = pattern.queryArc(0.0, 1.0)
        events shouldHaveSize 0
    }

    "MapPattern should preserve event timing when not modified" {
        val source = seq("a", "b", "c")
        val pattern = source.map { events -> events }

        val sourceEvents = source.queryArc(0.0, 1.0)
        val patternEvents = pattern.queryArc(0.0, 1.0)

        patternEvents shouldHaveSize sourceEvents.size
        patternEvents.forEachIndexed { index, event ->
            event.begin shouldBe sourceEvents[index].begin
            event.end shouldBe sourceEvents[index].end
        }
    }

    "MapPattern should work with empty source" {
        val source = silence
        val pattern = source.map { events ->
            events + listOf()  // Try to add events (but source is empty)
        }

        val events = pattern.queryArc(0.0, 1.0)
        events shouldHaveSize 0
    }

    "MapPattern should support chaining multiple transformations" {
        val source = note("a", "b", "c", "d")
        val pattern = source
            .map { events -> events.filter { it.data.note?.lowercase() != "b" } }
            .map { events -> events.map { it.copy(data = it.data.copy(note = it.data.note + "!")) } }

        val events = pattern.queryArc(0.0, 1.0)
        events shouldHaveSize 3
        events[0].data.note shouldBeEqualIgnoringCase "a!"
        events[1].data.note shouldBeEqualIgnoringCase "c!"
        events[2].data.note shouldBeEqualIgnoringCase "d!"
    }

    "MapPattern should support reversing event order" {
        val source = note("a", "b", "c")
        val pattern = source.map { events -> events.reversed() }

        val events = pattern.queryArc(0.0, 1.0)
        events shouldHaveSize 3
        events[0].data.note shouldBeEqualIgnoringCase "c"
        events[1].data.note shouldBeEqualIgnoringCase "b"
        events[2].data.note shouldBeEqualIgnoringCase "a"
    }

    "MapPattern should work across multiple query arcs" {
        val source = note("a", "b", "c", "d")
        val pattern = source.map { events ->
            events.filter { it.data.note?.lowercase() in listOf("a", "c") }
        }

        val events1 = pattern.queryArc(0.0, 0.5)
        events1 shouldHaveSize 1
        events1[0].data.note shouldBeEqualIgnoringCase "a"

        val events2 = pattern.queryArc(0.5, 1.0)
        events2 shouldHaveSize 1
        events2[0].data.note shouldBeEqualIgnoringCase "c"
    }

    "MapPattern should support duplicating events" {
        val source = note("a")
        val pattern = source.map { events ->
            events.flatMap { event -> List(3) { event } }
        }

        val events = pattern.queryArc(0.0, 1.0)
        events shouldHaveSize 3
        events.forEach { it.data.note shouldBeEqualIgnoringCase "a" }
    }
})
