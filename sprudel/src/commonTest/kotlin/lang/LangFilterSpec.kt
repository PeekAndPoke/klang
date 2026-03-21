package io.peekandpoke.klang.sprudel.lang

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeEqualIgnoringCase
import io.peekandpoke.klang.sprudel.SprudelPatternEvent

class LangFilterSpec : StringSpec({

    "filter dsl interface" {
        val predicate: (SprudelPatternEvent) -> Boolean = {
            it.data.value?.asString == "a"
        }
        // All 3 call styles produce: only "a" from "a b" passes
        val pat = seq("a b")

        val viaPat = pat.filter(predicate)
        val viaStr = "a b".filter(predicate)
        val viaMapper = pat.apply(filter(predicate))

        listOf(
            "viaPat" to viaPat,
            "viaStr" to viaStr,
            "viaMapper" to viaMapper,
        ).forEach { (name, p) ->
            withClue(name) {
                assertSoftly {
                    val events = p.queryArc(0.0, 1.0)
                    events.shouldNotBeEmpty()
                    events.size shouldBe 1
                    events[0].data.value?.asString?.lowercase() shouldBe "a"
                }
            }
        }
    }

    "filter() works as pattern extension" {
        // filter(predicate)
        // Keep events where note is "a"
        val p = note("a b").filter { it.data.note?.lowercase() == "a" }

        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 1
        events[0].data.note shouldBeEqualIgnoringCase "a"
    }

    "filter() works as string extension" {
        val p = "a b".filter { it.data.value?.asString == "b" }.note()

        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 1
        events[0].data.note shouldBeEqualIgnoringCase "B"
    }

    "filter() works as top-level PatternMapperFn" {
        val p = note("a b").apply(filter { it.data.note?.lowercase() == "a" })

        p.queryArc(0.0, 1.0).size shouldBe 1
    }

    "filter() can use other properties" {
        // Keep events with gain > 0.5
        val p = note("a b").gain("0.8 0.2").filter { (it.data.gain ?: 0.0) > 0.5 }

        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 1
        events[0].data.note shouldBeEqualIgnoringCase "a"
        events[0].data.gain shouldBe 0.8
    }
})
