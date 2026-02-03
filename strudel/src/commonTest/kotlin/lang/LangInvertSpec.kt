package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.StrudelPattern

class LangInvertSpec : StringSpec({

    "invert() inverts boolean values true to false" {
        val pattern = pure(true).invert()
        val events = pattern.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.value?.asBoolean shouldBe false
    }

    "invert() inverts boolean values false to true" {
        val pattern = pure(false).invert()
        val events = pattern.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.value?.asBoolean shouldBe true
    }

    "invert() inverts numeric 1 to 0" {
        val pattern = pure(1).invert()
        val events = pattern.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.value?.asBoolean shouldBe false
    }

    "invert() inverts numeric 0 to 1" {
        val pattern = pure(0).invert()
        val events = pattern.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.value?.asBoolean shouldBe true
    }

    "invert() works with sequences" {
        val pattern = seq(true, false, true, false).invert()
        val events = pattern.queryArc(0.0, 1.0).sortedBy { it.part.begin }

        events.size shouldBe 4
        events[0].data.value?.asBoolean shouldBe false
        events[1].data.value?.asBoolean shouldBe true
        events[2].data.value?.asBoolean shouldBe false
        events[3].data.value?.asBoolean shouldBe true
    }

    "inv() is an alias for invert()" {
        val pattern = pure(true).inv()
        val events = pattern.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.value?.asBoolean shouldBe false
    }

    "invert() works as standalone function" {
        val pattern = invert(pure(true))
        val events = pattern.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.value?.asBoolean shouldBe false
    }

    "invert() works as string extension" {
        val pattern = "true false".invert()
        val events = pattern.queryArc(0.0, 1.0).sortedBy { it.part.begin }

        events.size shouldBe 2
        events[0].data.value?.asString shouldBe "false"
        events[1].data.value?.asString shouldBe "true"
    }

    "invert() works in compiled code" {
        val pattern = StrudelPattern.compile("""pure(true).invert()""")
        val events = pattern?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 1
        events[0].data.value?.asBoolean shouldBe false
    }

    "invert() with numeric sequence 1 0 1 0" {
        val pattern = seq(1, 0, 1, 0).invert()
        val events = pattern.queryArc(0.0, 1.0).sortedBy { it.part.begin }

        events.size shouldBe 4
        events[0].data.value?.asBoolean shouldBe false // 1 -> false
        events[1].data.value?.asBoolean shouldBe true  // 0 -> true
        events[2].data.value?.asBoolean shouldBe false // 1 -> false
        events[3].data.value?.asBoolean shouldBe true  // 0 -> true
    }

    "invert() double inversion returns to original" {
        val original = seq(true, false, true)
        val doubleInverted = original.invert().invert()

        val originalEvents = original.queryArc(0.0, 1.0).sortedBy { it.part.begin }
        val doubleInvertedEvents = doubleInverted.queryArc(0.0, 1.0).sortedBy { it.part.begin }

        originalEvents.size shouldBe doubleInvertedEvents.size
        originalEvents.forEachIndexed { i, event ->
            event.data.value?.asBoolean shouldBe doubleInvertedEvents[i].data.value?.asBoolean
        }
    }
})
