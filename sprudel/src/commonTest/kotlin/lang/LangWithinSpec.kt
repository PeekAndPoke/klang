package io.peekandpoke.klang.sprudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.dslInterfaceTests

class LangWithinSpec : StringSpec({

    "within dsl interface" {
        val pat = "0 1 2 3"
        val transform: PatternMapperFn = add(1)
        dslInterfaceTests(
            "pattern.within(0.5, 1.0, add(1))" to n(pat).within(0.5, 1.0, transform = transform),
            "script pattern.within(0.5, 1.0, add(1))" to SprudelPattern.compile("""n("$pat").within(0.5, 1.0, x => x.add(1))"""),
            "string.within(0.5, 1.0, add(1))" to pat.within(0.5, 1.0, transform = transform),
            "script string.within(0.5, 1.0, add(1))" to SprudelPattern.compile(""""$pat".within(0.5, 1.0, x => x.add(1))"""),
            "within(0.5, 1.0, add(1))" to n(pat).apply(within(0.5, 1.0, transform = transform)),
            "script within(0.5, 1.0, add(1))" to SprudelPattern.compile("""n("$pat").apply(within(0.5, 1.0, x => x.add(1)))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events.size shouldBe 4  // transform changes values but not event count
        }
    }

    "within() transforms events inside the window, leaves others unchanged" {
        // "0 1 2 3": 0 at [0,0.25), 1 at [0.25,0.5), 2 at [0.5,0.75), 3 at [0.75,1.0)
        // within(0.5, 1.0, add(1)): events 2 and 3 become 3 and 4; events 0 and 1 are unchanged
        val p = seq("0 1 2 3").within(0.5, 1.0, transform = add(1))
        val events = p.queryArc(0.0, 1.0).sortedBy { it.part.begin.toDouble() }

        events.size shouldBe 4
        events[0].data.value?.asInt shouldBe 0  // outside window — unchanged
        events[1].data.value?.asInt shouldBe 1  // outside window — unchanged
        events[2].data.value?.asInt shouldBe 3  // inside window — 2 + 1
        events[3].data.value?.asInt shouldBe 4  // inside window — 3 + 1
    }

    "within() works with string extension" {
        val p = "0 1 2 3".within(0.5, 1.0, transform = add(1))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4
    }

    "within() returns unchanged pattern if start >= end" {
        val p = seq("0 1 2 3").within(0.5, 0.5, transform = add(1))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4
        events.map { it.data.value?.asInt } shouldBe listOf(0, 1, 2, 3)
    }

    "within() top-level function via apply()" {
        val p = seq("0 1 2 3").apply(within(0.5, 1.0, transform = add(1)))
        val events = p.queryArc(0.0, 1.0).sortedBy { it.part.begin.toDouble() }

        events.size shouldBe 4
        events[2].data.value?.asInt shouldBe 3  // 2 + 1
        events[3].data.value?.asInt shouldBe 4  // 3 + 1
    }

    "within() PatternMapperFn extension chaining" {
        val mapper: PatternMapperFn = within(0.5, 1.0, transform = add(1))
        val p = n("0 1 2 3").apply(mapper)
        val events = p.queryArc(0.0, 1.0)

        events.shouldNotBeEmpty()
        events.size shouldBe 4
    }
})
