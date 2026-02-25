package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class LangShuffleSpec : StringSpec({

    "shuffle(4) preserves elements but changes order" {
        val p = n("0 1 2 3").shuffle(4).seed(123)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4
        val values = events.mapNotNull { it.data.soundIndex }

        // Should contain all elements 0, 1, 2, 3 exactly once
        values.sorted() shouldBe listOf(0, 1, 2, 3)
    }

    "shuffle works as string extension" {
        val p = "0 1 2 3".shuffle(4).seed(456)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4
        val values = events.mapNotNull { it.data.value?.asInt }
        values.sorted() shouldBe listOf(0, 1, 2, 3)
    }

    "shuffle() as top-level PatternMapperFn reorders all slices" {
        val events = seq("0 1 2 3").apply(shuffle(4)).queryArc(0.0, 1.0)
        events.size shouldBe 4
        events.mapNotNull { it.data.value?.asInt }.sorted() shouldBe listOf(0, 1, 2, 3)
    }

    "PatternMapperFn.shuffle() chains shuffle onto a mapper" {
        val identity: PatternMapperFn = { it }
        val events = seq("0 1 2 3").apply(identity.shuffle(4)).queryArc(0.0, 1.0)
        events.size shouldBe 4
        events.mapNotNull { it.data.value?.asInt }.sorted() shouldBe listOf(0, 1, 2, 3)
    }

    "scramble() as top-level PatternMapperFn selects slices with replacement" {
        val events = seq("0 1 2 3").apply(scramble(4)).queryArc(0.0, 1.0)
        events.size shouldBe 4
    }

    "PatternMapperFn.scramble() chains scramble onto a mapper" {
        val identity: PatternMapperFn = { it }
        val events = seq("0 1 2 3").apply(identity.scramble(4)).queryArc(0.0, 1.0)
        events.size shouldBe 4
    }
})
