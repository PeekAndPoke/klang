package io.peekandpoke.klang.strudel.lang

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.doubles.shouldBeExactly
import io.kotest.matchers.shouldBe

/**
 * Tests for pick() and pickmod() with innerJoin behavior.
 *
 * InnerJoin means: the timing comes from the PICKED (inner) patterns, not the selector (outer) pattern.
 */
class LangPickInnerSpec : StringSpec({

    "pick() with empty list returns silence" {
        val lookup: List<Any> = emptyList()
        val selector = seq("0 1")

        val result = pick(lookup, selector)

        result shouldBe silence
    }

    "pick() with list of strings picks by numeric index" {
        val lookup: List<Any> = listOf("bd", "hh", "sn")
        val selector = seq("0 1 2")

        val result = pick(lookup, selector)
        val events = result.queryArc(0.0, 1.0)

        // Should have 3 events (one per selector event)
        events shouldHaveSize 3

        // Check note values from picked patterns
        events[0].data.value?.asString shouldBe "bd"
        events[1].data.value?.asString shouldBe "hh"
        events[2].data.value?.asString shouldBe "sn"
    }

    "pick() clamps out-of-bounds indices" {
        val lookup: List<Any> = listOf("bd", "hh")
        val selector = seq("0 1 2 3 99")

        val result = pick(lookup, selector)
        val events = result.queryArc(0.0, 1.0)

        events shouldHaveSize 5

        events[0].data.value?.asString shouldBe "bd"  // index 0
        events[1].data.value?.asString shouldBe "hh"  // index 1
        events[2].data.value?.asString shouldBe "hh"  // index 2 clamped to 1
        events[3].data.value?.asString shouldBe "hh"  // index 3 clamped to 1
        events[4].data.value?.asString shouldBe "hh"  // index 99 clamped to 1
    }

    "pickmod() wraps out-of-bounds indices with modulo" {
        val lookup: List<Any> = listOf("bd", "hh")
        val selector = seq("0 1 2 3 4")

        val result = pickmod(lookup, selector)
        val events = result.queryArc(0.0, 1.0)

        events shouldHaveSize 5

        events[0].data.value?.asString shouldBe "bd"  // index 0 % 2 = 0
        events[1].data.value?.asString shouldBe "hh"  // index 1 % 2 = 1
        events[2].data.value?.asString shouldBe "bd"  // index 2 % 2 = 0
        events[3].data.value?.asString shouldBe "hh"  // index 3 % 2 = 1
        events[4].data.value?.asString shouldBe "bd"  // index 4 % 2 = 0
    }

    "pick() with map picks by string key" {
        val lookup: Map<String, Any> = mapOf(
            "a" to "bd",
            "b" to "hh",
            "c" to "sn"
        )
        val selector = seq("a b c")

        val result = pick(lookup, selector)
        val events = result.queryArc(0.0, 1.0)

        events shouldHaveSize 3

        events[0].data.value?.asString shouldBe "bd"
        events[1].data.value?.asString shouldBe "hh"
        events[2].data.value?.asString shouldBe "sn"
    }

    "pick() with map returns silence for unknown keys" {
        val lookup: Map<String, Any> = mapOf(
            "a" to "bd",
            "b" to "hh"
        )
        val selector = seq("a b c")

        val result = pick(lookup, selector)
        val events = result.queryArc(0.0, 1.0)

        // Only events for "a" and "b", "c" is unknown so no event
        events shouldHaveSize 2

        events[0].data.value?.asString shouldBe "bd"
        events[1].data.value?.asString shouldBe "hh"
    }

    "pick() with list of patterns picks patterns" {
        val lookup: List<Any> = listOf(
            sound("bd hh"),
            sound("sn cp")
        )
        val selector = seq("0 1")

        val result = pick(lookup, selector)
        val events = result.queryArc(0.0, 1.0)

        // First selector event (0) picks "bd hh" which has 2 events in its half cycle
        // Second selector event (1) picks "sn cp" which has 2 events in its half cycle
        events shouldHaveSize 2

        // First Event from "bd hh" pattern
        events[0].data.sound shouldBe "bd"
        // Second event from "sn cp" pattern
        events[1].data.sound shouldBe "cp"
    }

    "pick() with fractional indices rounds to nearest integer" {
        val lookup: List<Any> = listOf("bd", "hh", "sd")
        val selector = seq("0.2 1.5 2.8")

        val result = pick(lookup, selector)
        val events = result.queryArc(0.0, 1.0)

        events shouldHaveSize 3

        events[0].data.value?.asString shouldBe "bd"  // 0.2 rounds to 0
        events[1].data.value?.asString shouldBe "hh"  // 1.5 rounds to 2
        events[2].data.value?.asString shouldBe "sd"  // 2.8 rounds to 3, clamped to 2
    }

    "pick() preserves timing from picked patterns (innerJoin)" {
        val lookup: List<Any> = listOf(
            sound("bd hh"),  // Two events
            sound("sd")      // One event
        )
        val selector = seq("0 1")

        val result = pick(lookup, selector)
        val events = result.queryArc(0.0, 1.0)

        assertSoftly {
            events shouldHaveSize 2
            // First event of "bd hh" pattern events
            events[0].begin.toDouble() shouldBeExactly 0.0
            events[0].end.toDouble() shouldBeExactly 0.5
            events[0].data.sound shouldBe "bd"

            events[1].begin.toDouble() shouldBeExactly 0.5
            events[1].end.toDouble() shouldBeExactly 1.0
            events[1].data.sound shouldBe "sd"
        }
    }

    "pickmod() handles negative indices correctly" {
        val lookup: List<Any> = listOf("bd", "hh", "sn")
        val selector = seq("-1 -2 -3")

        val result = pickmod(lookup, selector)
        val events = result.queryArc(0.0, 1.0)

        events shouldHaveSize 3

        // -1 % 3 = 2
        events[0].data.value?.asString shouldBe "sn"
        // -2 % 3 = 1
        events[1].data.value?.asString shouldBe "hh"
        // -3 % 3 = 0
        events[2].data.value?.asString shouldBe "bd"
    }
})
