package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe

class LangComparisonAndLogicSpec : StringSpec({

    // -- Comparison Operators --

    "lt() checks less than" {
        // 1 < 2 -> 1, 2 < 2 -> 0, 3 < 2 -> 0
        val p = seq("1 2 3").lt("2")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 3
        events[0].data.value?.asInt shouldBe 1
        events[1].data.value?.asInt shouldBe 0
        events[2].data.value?.asInt shouldBe 0
    }

    "gt() checks greater than" {
        val p = seq("1 2 3").gt("2")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 3
        events[0].data.value?.asInt shouldBe 0
        events[1].data.value?.asInt shouldBe 0
        events[2].data.value?.asInt shouldBe 1
    }

    "lte() checks less than or equal" {
        val p = seq("1 2 3").lte("2")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 3
        events[0].data.value?.asInt shouldBe 1
        events[1].data.value?.asInt shouldBe 1
        events[2].data.value?.asInt shouldBe 0
    }

    "gte() checks greater than or equal" {
        val p = seq("1 2 3").gte("2")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 3
        events[0].data.value?.asInt shouldBe 0
        events[1].data.value?.asInt shouldBe 1
        events[2].data.value?.asInt shouldBe 1
    }

    "eq() checks equality" {
        val p = seq("1 2 3").eq("2")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 3
        events[0].data.value?.asInt shouldBe 0
        events[1].data.value?.asInt shouldBe 1
        events[2].data.value?.asInt shouldBe 0
    }

    "eqt() checks truthiness equality" {
        // 0 (falsy), 1 (truthy), 2 (truthy) vs 1 (truthy)
        val p = seq("0 1 2").eqt("1")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 3
        events[0].data.value?.asInt shouldBe 0 // falsy vs truthy -> 0
        events[1].data.value?.asInt shouldBe 1 // truthy vs truthy -> 1
        events[2].data.value?.asInt shouldBe 1 // truthy vs truthy -> 1
    }

    "ne() checks inequality" {
        val p = seq("1 2 3").ne("2")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 3
        events[0].data.value?.asInt shouldBe 1
        events[1].data.value?.asInt shouldBe 0
        events[2].data.value?.asInt shouldBe 1
    }

    "net() checks truthiness inequality" {
        // 0 (falsy), 1 (truthy), 2 (truthy) vs 0 (falsy)
        val p = seq("0 1 2").net("0")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 3
        events[0].data.value?.asInt shouldBe 0 // falsy vs falsy -> 0
        events[1].data.value?.asInt shouldBe 1 // truthy vs falsy -> 1
        events[2].data.value?.asInt shouldBe 1 // truthy vs falsy -> 1
    }

    // -- Logical Operators --

    "and() performs logical AND" {
        // 0 and 5 -> 0 (falsy)
        // 1 and 5 -> 5 (truthy -> returns other)
        val p = seq("0 1").and("5")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 0
        events[1].data.value?.asInt shouldBe 5
    }

    "or() performs logical OR" {
        // 0 or 5 -> 5 (falsy -> returns other)
        // 1 or 5 -> 1 (truthy -> returns self)
        val p = seq("0 1").or("5")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 5
        events[1].data.value?.asInt shouldBe 1
    }

    // -- Top Level Function Verification --

    "top-level comparison functions work" {
        val p = eq("2")
        p.queryArc(0.0, 1.0).shouldBeEmpty()
    }

    "top-level logical functions work" {
        val p = and("5")
        p.queryArc(0.0, 1.0).shouldBeEmpty()
    }
})
