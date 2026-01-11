package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class LangComparisonAndLogicSpec : StringSpec({

    // -- Comparison --
    "lt() checks less than" {
        // 1 < 2 -> 1, 2 < 2 -> 0, 3 < 2 -> 0
        val p = n("1 2 3").lt("2")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 3
        events[0].data.value?.asInt shouldBe 1
        events[1].data.value?.asInt shouldBe 0
        events[2].data.value?.asInt shouldBe 0
    }

    "gt() checks greater than" {
        val p = n("1 2 3").gt("2")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 3
        events[0].data.value?.asInt shouldBe 0
        events[1].data.value?.asInt shouldBe 0
        events[2].data.value?.asInt shouldBe 1
    }

    "lte() checks less than or equal" {
        val p = n("1 2 3").lte("2")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 3
        events[0].data.value?.asInt shouldBe 1
        events[1].data.value?.asInt shouldBe 1
        events[2].data.value?.asInt shouldBe 0
    }

    "gte() checks greater than or equal" {
        val p = n("1 2 3").gte("2")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 3
        events[0].data.value?.asInt shouldBe 0
        events[1].data.value?.asInt shouldBe 1
        events[2].data.value?.asInt shouldBe 1
    }

    "eq() checks equality" {
        val p = n("1 2 3").eq("2")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 3
        events[0].data.value?.asInt shouldBe 0
        events[1].data.value?.asInt shouldBe 1
        events[2].data.value?.asInt shouldBe 0
    }

    "ne() checks inequality" {
        val p = n("1 2 3").ne("2")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 3
        events[0].data.value?.asInt shouldBe 1
        events[1].data.value?.asInt shouldBe 0
        events[2].data.value?.asInt shouldBe 1
    }

    // -- Logical --
    "and() performs logical AND" {
        // 0 and 5 -> 0 (falsy)
        // 1 and 5 -> 5 (truthy -> other)
        val p = n("0 1").and("5")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 0
        events[1].data.value?.asInt shouldBe 5
    }

    "or() performs logical OR" {
        // 0 or 5 -> 5 (falsy -> other)
        // 1 or 5 -> 1 (truthy -> self)
        val p = n("0 1").or("5")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 5
        events[1].data.value?.asInt shouldBe 1
    }
})
