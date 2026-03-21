package io.peekandpoke.klang.sprudel.lang

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeEqualIgnoringCase

class LangPickSqueezeSpec : StringSpec({

    "inhabit() squeezes patterns into selector events" {
        val lookup: List<Any> = listOf(
            seq("bd hh"),
            seq("sn cp")
        )
        val result = seq("0 1").inhabit(lookup)
        val events = result.queryArc(0.0, 1.0)

        events shouldHaveSize 4

        events[0].data.value?.asString shouldBe "bd"
        events[0].part.begin.toDouble() shouldBe 0.0
        events[0].part.end.toDouble() shouldBe 0.25

        events[1].data.value?.asString shouldBe "hh"
        events[1].part.begin.toDouble() shouldBe 0.25
        events[1].part.end.toDouble() shouldBe 0.5

        events[2].data.value?.asString shouldBe "sn"
        events[2].part.begin.toDouble() shouldBe 0.5
        events[2].part.end.toDouble() shouldBe 0.75

        events[3].data.value?.asString shouldBe "cp"
        events[3].part.begin.toDouble() shouldBe 0.75
        events[3].part.end.toDouble() shouldBe 1.0
    }

    "inhabit() works with string pattern method" {
        val result = "0 1".inhabit(
            listOf(
                sound("bd hh"),
                sound("sn cp")
            )
        )
        val events = result.queryArc(0.0, 1.0)
        assertSoftly {
            events shouldHaveSize 4
            events[0].data.sound shouldBe "bd"
            events[3].data.sound shouldBe "cp"
        }
    }

    "inhabit() works with map lookup" {
        val lookup = mapOf(
            "a" to sound("bd hh"),
            "b" to sound("sn cp")
        )
        val result = "a b".inhabit(lookup)
        val events = result.queryArc(0.0, 1.0)
        assertSoftly {
            events shouldHaveSize 4
            events[0].data.sound shouldBe "bd"
            events[2].data.sound shouldBe "sn"
        }
    }

    "inhabit() supports varargs style" {
        val result = seq("0 1").inhabit(seq("bd hh"), seq("sn cp"))
        val events = result.queryArc(0.0, 1.0)
        assertSoftly {
            events shouldHaveSize 4
            events[0].data.value?.asString shouldBe "bd"
            events[3].data.value?.asString shouldBe "cp"
        }
    }

    "inhabitmod() supports varargs style with wrapping" {
        // selector "0 1 2" -> 0->pat1, 1->pat2, 2->pat1 (wrap)
        val result = seq("0 1 2").inhabitmod(seq("bd"), seq("sn"))
        val events = result.queryArc(0.0, 1.0)
        events shouldHaveSize 3
        events[0].data.value?.asString shouldBe "bd"
        events[1].data.value?.asString shouldBe "sn"
        events[2].data.value?.asString shouldBe "bd"
    }

    "pickSqueeze() is alias for inhabit()" {
        val lookup = listOf(note("a"))
        val result = "0".pickSqueeze(lookup)
        val events = result.queryArc(0.0, 1.0)
        assertSoftly {
            events shouldHaveSize 1
            events[0].data.note shouldBeEqualIgnoringCase "a"
        }
    }
})
