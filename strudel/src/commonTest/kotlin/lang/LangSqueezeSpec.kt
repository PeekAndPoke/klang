package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

class LangSqueezeSpec : StringSpec({

    "squeeze() works as pattern extension with List lookup" {
        val pat1 = seq("bd hh")
        val pat2 = seq("sn cp")

        val result = seq("0 1").squeeze(listOf(pat1, pat2))
        val events = result.queryArc(0.0, 1.0)

        events shouldHaveSize 4
        events[0].data.value?.asString shouldBe "bd"
        events[1].data.value?.asString shouldBe "hh"
        events[2].data.value?.asString shouldBe "sn"
        events[3].data.value?.asString shouldBe "cp"
    }

    "squeeze() works as string extension with List lookup" {
        val pat1 = seq("bd hh")
        val pat2 = seq("sn cp")

        val result = "0 1".squeeze(listOf(pat1, pat2))
        val events = result.queryArc(0.0, 1.0)

        events shouldHaveSize 4
        events[0].data.value?.asString shouldBe "bd"
        events[3].data.value?.asString shouldBe "cp"
    }

    "squeeze() supports Map lookup" {
        val lookup = mapOf(
            "a" to seq("bd"),
            "b" to seq("sn")
        )
        val result = "a b".squeeze(lookup)
        val events = result.queryArc(0.0, 1.0)

        events shouldHaveSize 2
        events[0].data.value?.asString shouldBe "bd"
        events[1].data.value?.asString shouldBe "sn"
    }

    "squeeze() supports varargs style" {
        val result = seq("0 1").squeeze(seq("bd"), seq("sn"))
        val events = result.queryArc(0.0, 1.0)

        events shouldHaveSize 2
        events[0].data.value?.asString shouldBe "bd"
        events[1].data.value?.asString shouldBe "sn"
    }

    "squeeze() works as string extension with varargs" {
        val result = "0 1".squeeze(seq("bd"), seq("sn"))
        val events = result.queryArc(0.0, 1.0)

        events shouldHaveSize 2
        events[0].data.value?.asString shouldBe "bd"
        events[1].data.value?.asString shouldBe "sn"
    }
})
