// strudel/src/commonTest/kotlin/lang/LangFilterSpec.kt
package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe

class LangFilterSpec : StringSpec({

    "filter() works as pattern extension" {
        // filter(predicate)
        // Keep events where note is "a"
        val p = note("a b").filter { it.data.note == "a" }

        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 1
        events[0].data.note shouldBe "a"
    }

    "filter() works as string extension" {
        val p = "a b".filter { it.data.value?.asString == "b" }.note()

        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 1
        events[0].data.note shouldBe "B"
    }

    "filter() works as top-level function" {
        // filter(predicate, pattern)
        val p = filter { it.data.note == "a" }

        p.queryArc(0.0, 1.0).shouldBeEmpty()
    }

    "filter() can use other properties" {
        // Keep events with gain > 0.5
        val p = note("a b").gain("0.8 0.2").filter { (it.data.gain ?: 0.0) > 0.5 }

        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 1
        events[0].data.note shouldBe "a"
        events[0].data.gain shouldBe 0.8
    }
})
