package io.peekandpoke.klang.strudel.lang

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.doubles.shouldBeExactly
import io.kotest.matchers.shouldBe

/**
 * Tests for pickOut() and pickmodOut() with outerJoin behavior.
 *
 * NOTE: Currently pickOut behaves like pick (clipping events) to match JS implementation behavior.
 */
class LangPickOuterSpec : StringSpec({

    "pickOut() with empty list returns silence" {
        val lookup: List<Any> = emptyList()
        val selector = seq("0 1")

        val result = pickOut(lookup, selector)

        result shouldBe silence
    }

    "pickOut() with list of strings picks by numeric index" {
        val lookup: List<Any> = listOf("bd", "hh", "sn")
        val selector = seq("0 1 2")

        val result = pickOut(lookup, selector)
        val events = result.queryArc(0.0, 1.0)

        // Should have 3 events (one per selector event)
        events shouldHaveSize 3

        // Check note values from picked patterns
        events[0].data.value?.asString shouldBe "bd"
        events[1].data.value?.asString shouldBe "hh"
        events[2].data.value?.asString shouldBe "sn"
    }

    "pickOut() supports spread arguments" {
        val result = pickOut("bd", "hh", "sn", "0 1 2")
        val events = result.queryArc(0.0, 1.0)

        events shouldHaveSize 3
        events[0].data.value?.asString shouldBe "bd"
    }

    "pickOut() clamps out-of-bounds indices" {
        val lookup: List<Any> = listOf("bd", "hh")
        val selector = seq("0 1 2 3 99")

        val result = pickOut(lookup, selector)
        val events = result.queryArc(0.0, 1.0)

        events shouldHaveSize 5

        events[0].data.value?.asString shouldBe "bd"  // index 0
        events[1].data.value?.asString shouldBe "hh"  // index 1
        events[2].data.value?.asString shouldBe "hh"  // index 2 clamped to 1
        events[3].data.value?.asString shouldBe "hh"  // index 3 clamped to 1
        events[4].data.value?.asString shouldBe "hh"  // index 99 clamped to 1
    }

    "pickmodOut() wraps out-of-bounds indices with modulo" {
        val lookup: List<Any> = listOf("bd", "hh")
        val selector = seq("0 1 2 3 4")

        val result = pickmodOut(lookup, selector)
        val events = result.queryArc(0.0, 1.0)

        events shouldHaveSize 5

        events[0].data.value?.asString shouldBe "bd"
        events[1].data.value?.asString shouldBe "hh"
        events[2].data.value?.asString shouldBe "bd"
        events[3].data.value?.asString shouldBe "hh"
        events[4].data.value?.asString shouldBe "bd"
    }

    "pickmodOut() supports spread arguments" {
        val result = pickmodOut("bd", "hh", "0 1 2 3")
        val events = result.queryArc(0.0, 1.0)

        events shouldHaveSize 4
        events[0].data.value?.asString shouldBe "bd"
        events[3].data.value?.asString shouldBe "hh"
    }

    "pickOut() with map picks by string key" {
        val lookup: Map<String, Any> = mapOf(
            "a" to "bd",
            "b" to "hh",
            "c" to "sn"
        )
        val selector = seq("a b c")

        val result = pickOut(lookup, selector)
        val events = result.queryArc(0.0, 1.0)

        events shouldHaveSize 3

        events[0].data.value?.asString shouldBe "bd"
        events[1].data.value?.asString shouldBe "hh"
        events[2].data.value?.asString shouldBe "sn"
    }

    "pickOut() with map returns silence for unknown keys" {
        val lookup: Map<String, Any> = mapOf(
            "a" to "bd",
            "b" to "hh"
        )
        val selector = seq("a b c")

        val result = pickOut(lookup, selector)
        val events = result.queryArc(0.0, 1.0)

        // Only events for "a" and "b", "c" is unknown so no event
        events shouldHaveSize 2

        events[0].data.value?.asString shouldBe "bd"
        events[1].data.value?.asString shouldBe "hh"
    }

    "pickOut() with list of patterns works" {
        val lookup: List<Any> = listOf(
            sound("bd hh"),
            sound("sn cp")
        )
        val selector = seq("0 1")

        val result = pickOut(lookup, selector)
        val events = result.queryArc(0.0, 1.0)

        // Events are clipped to selector duration (0.5)
        events shouldHaveSize 2
        events[0].data.sound shouldBe "bd"
        events[1].data.sound shouldBe "cp"
    }

    "pickOut() clips events (matching JS behavior)" {
        // Inner pattern has one event of duration 1.0
        val lookup = listOf(sound("bd"))

        // Selector event has duration 0.5
        val selector = seq("0 ~") // 0.0-0.5

        val result = pickOut(lookup, selector)
        val events = result.queryArc(0.0, 1.0)

        assertSoftly {
            events shouldHaveSize 1
            events[0].part.begin.toDouble() shouldBeExactly 0.0
            // Event IS clipped to selector end (0.5)
            events[0].part.end.toDouble() shouldBeExactly 0.5
            events[0].data.sound shouldBe "bd"
        }
    }
})
