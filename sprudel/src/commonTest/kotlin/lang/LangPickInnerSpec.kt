package io.peekandpoke.klang.sprudel.lang

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.doubles.shouldBeExactly
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.sprudel.StrudelPattern
import io.peekandpoke.klang.sprudel.dslInterfaceTests

/**
 * Tests for pick() and pickmod() with innerJoin behavior.
 *
 * InnerJoin means: the timing comes from the PICKED (inner) patterns, clipped to the selector (outer) pattern.
 */
class LangPickInnerSpec : StringSpec({

    // ---- pick() dsl interface ----

    "pick() dsl interface" {
        val pat = "0 1 2"
        dslInterfaceTests(
            "pattern.pick()" to seq(pat).pick("bd", "hh", "sn"),
            "script pattern.pick()" to StrudelPattern.compile("""seq("$pat").pick("bd", "hh", "sn")"""),
            "string.pick()" to pat.pick("bd", "hh", "sn"),
            "script string.pick()" to StrudelPattern.compile(""""$pat".pick("bd", "hh", "sn")"""),
            "pattern.apply(pick())" to seq(pat).apply(pick("bd", "hh", "sn")),
            "script pattern.apply(pick())" to StrudelPattern.compile("""seq("$pat").apply(pick("bd", "hh", "sn"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.value?.asString shouldBe "bd"
            events[1].data.value?.asString shouldBe "hh"
            events[2].data.value?.asString shouldBe "sn"
        }
    }

    "pick() as PatternMapperFn" {
        val mapperFn = pick("bd", "hh", "sn")
        val result = seq("0 1 2").apply(mapperFn)
        val events = result.queryArc(0.0, 1.0)
        events shouldHaveSize 3
        events[0].data.value?.asString shouldBe "bd"
        events[1].data.value?.asString shouldBe "hh"
        events[2].data.value?.asString shouldBe "sn"
    }

    "PatternMapperFn.pick() extension" {
        val identity: PatternMapperFn = { it }
        val mapperWithPick = identity.pick("bd", "hh", "sn")
        val result = seq("0 1 2").apply(mapperWithPick)
        val events = result.queryArc(0.0, 1.0)
        events shouldHaveSize 3
        events[0].data.value?.asString shouldBe "bd"
        events[1].data.value?.asString shouldBe "hh"
        events[2].data.value?.asString shouldBe "sn"
    }

    // ---- pickmod() dsl interface ----

    "pickmod() dsl interface" {
        val pat = "0 1 2 3 4"
        dslInterfaceTests(
            "pattern.pickmod()" to seq(pat).pickmod("bd", "hh", "sn"),
            "script pattern.pickmod()" to StrudelPattern.compile("""seq("$pat").pickmod("bd", "hh", "sn")"""),
            "string.pickmod()" to pat.pickmod("bd", "hh", "sn"),
            "script string.pickmod()" to StrudelPattern.compile(""""$pat".pickmod("bd", "hh", "sn")"""),
            "pattern.apply(pickmod())" to seq(pat).apply(pickmod("bd", "hh", "sn")),
            "script pattern.apply(pickmod())" to StrudelPattern.compile(
                """seq("$pat").apply(pickmod("bd", "hh", "sn"))"""
            ),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.value?.asString shouldBe "bd"  // 0 % 3 = 0
            events[1].data.value?.asString shouldBe "hh"  // 1 % 3 = 1
            events[2].data.value?.asString shouldBe "sn"  // 2 % 3 = 2
            events[3].data.value?.asString shouldBe "bd"  // 3 % 3 = 0
            events[4].data.value?.asString shouldBe "hh"  // 4 % 3 = 1
        }
    }

    "pickmod() as PatternMapperFn" {
        val mapperFn = pickmod("bd", "hh", "sn")
        val result = seq("0 1 2 3 4").apply(mapperFn)
        val events = result.queryArc(0.0, 1.0)
        events shouldHaveSize 5
        events[0].data.value?.asString shouldBe "bd"
        events[1].data.value?.asString shouldBe "hh"
        events[2].data.value?.asString shouldBe "sn"
        events[3].data.value?.asString shouldBe "bd"
        events[4].data.value?.asString shouldBe "hh"
    }

    "PatternMapperFn.pickmod() extension" {
        val identity: PatternMapperFn = { it }
        val mapperWithPickmod = identity.pickmod("bd", "hh", "sn")
        val result = seq("0 1 2 3 4").apply(mapperWithPickmod)
        val events = result.queryArc(0.0, 1.0)
        events shouldHaveSize 5
        events[0].data.value?.asString shouldBe "bd"
        events[1].data.value?.asString shouldBe "hh"
        events[2].data.value?.asString shouldBe "sn"
        events[3].data.value?.asString shouldBe "bd"
        events[4].data.value?.asString shouldBe "hh"
    }

    // ---- pick() behavioral tests ----

    "pick() with empty list returns silence" {
        val lookup: List<Any> = emptyList()
        val result = seq("0 1").pick(lookup)
        result shouldBe silence
    }

    "pick() with list of strings picks by numeric index" {
        val lookup: List<Any> = listOf("bd", "hh", "sn")
        val result = seq("0 1 2").pick(lookup)
        val events = result.queryArc(0.0, 1.0)
        events shouldHaveSize 3
        events[0].data.value?.asString shouldBe "bd"
        events[1].data.value?.asString shouldBe "hh"
        events[2].data.value?.asString shouldBe "sn"
    }

    "pick() supports varargs style" {
        val result = seq("0 1 2").pick("bd", "hh", "sn")
        val events = result.queryArc(0.0, 1.0)
        events shouldHaveSize 3
        events[0].data.value?.asString shouldBe "bd"
        events[1].data.value?.asString shouldBe "hh"
        events[2].data.value?.asString shouldBe "sn"
    }

    "pick() clamps out-of-bounds indices" {
        val lookup: List<Any> = listOf("bd", "hh")
        val result = seq("0 1 2 3 99").pick(lookup)
        val events = result.queryArc(0.0, 1.0)
        events shouldHaveSize 5
        events[0].data.value?.asString shouldBe "bd"  // index 0
        events[1].data.value?.asString shouldBe "hh"  // index 1
        events[2].data.value?.asString shouldBe "hh"  // index 2 clamped to 1
        events[3].data.value?.asString shouldBe "hh"  // index 3 clamped to 1
        events[4].data.value?.asString shouldBe "hh"  // index 99 clamped to 1
    }

    "pickmod() wraps out-of-bounds indices with modulo" {
        val lookup: List<Any> = listOf("bd", "hh")
        val result = seq("0 1 2 3 4").pickmod(lookup)
        val events = result.queryArc(0.0, 1.0)
        events shouldHaveSize 5
        events[0].data.value?.asString shouldBe "bd"  // 0 % 2 = 0
        events[1].data.value?.asString shouldBe "hh"  // 1 % 2 = 1
        events[2].data.value?.asString shouldBe "bd"  // 2 % 2 = 0
        events[3].data.value?.asString shouldBe "hh"  // 3 % 2 = 1
        events[4].data.value?.asString shouldBe "bd"  // 4 % 2 = 0
    }

    "pickmod() supports varargs style" {
        val result = seq("0 1 2 3").pickmod("bd", "hh")
        val events = result.queryArc(0.0, 1.0)
        events shouldHaveSize 4
        events[0].data.value?.asString shouldBe "bd"
        events[1].data.value?.asString shouldBe "hh"
        events[2].data.value?.asString shouldBe "bd"
        events[3].data.value?.asString shouldBe "hh"
    }

    "pick() with map picks by string key" {
        val lookup: Map<String, Any> = mapOf(
            "a" to "bd",
            "b" to "hh",
            "c" to "sn"
        )
        val result = seq("a b c").pick(lookup)
        val events = result.queryArc(0.0, 1.0)
        events shouldHaveSize 3
        events[0].data.value?.asString shouldBe "bd"
        events[1].data.value?.asString shouldBe "hh"
        events[2].data.value?.asString shouldBe "sn"
    }

    "pick() with map returns silence for unknown keys" {
        val lookup: Map<String, Any> = mapOf(
            "a" to "bd",
            "b" to "hh"
        )
        val result = seq("a b c").pick(lookup)
        val events = result.queryArc(0.0, 1.0)
        events shouldHaveSize 2
        events[0].data.value?.asString shouldBe "bd"
        events[1].data.value?.asString shouldBe "hh"
    }

    "pick() with patterns picks patterns" {
        val result = seq("0 1")
            .pick(sound("bd hh"), sound("sn cp"))

        val events = result.queryArc(0.0, 1.0)
        events shouldHaveSize 2
        events[0].data.sound shouldBe "bd"
        events[1].data.sound shouldBe "cp"
    }

    "pick() with list of patterns picks patterns" {
        val lookup: List<Any> = listOf(
            sound("bd hh"),
            sound("sn cp")
        )
        val result = seq("0 1").pick(lookup)
        val events = result.queryArc(0.0, 1.0)
        events shouldHaveSize 2
        events[0].data.sound shouldBe "bd"
        events[1].data.sound shouldBe "cp"
    }

    "pick() with fractional indices floors to integer" {
        val lookup: List<Any> = listOf("bd", "hh", "sd")
        val result = seq("0.2 1.5 2.8").pick(lookup)
        val events = result.queryArc(0.0, 1.0)
        events shouldHaveSize 3
        events[0].data.value?.asString shouldBe "bd"  // floor(0.2) = 0
        events[1].data.value?.asString shouldBe "hh"  // floor(1.5) = 1
        events[2].data.value?.asString shouldBe "sd"  // floor(2.8) = 2
    }

    "pick() preserves timing from picked patterns (innerJoin)" {
        val lookup: List<Any> = listOf(
            sound("bd hh"),  // Two events
            sound("sd")      // One event
        )
        val result = seq("0 1").pick(lookup)
        val events = result.queryArc(0.0, 1.0)

        assertSoftly {
            events shouldHaveSize 2
            events[0].part.begin.toDouble() shouldBeExactly 0.0
            events[0].part.end.toDouble() shouldBeExactly 0.5
            events[0].data.sound shouldBe "bd"

            events[1].part.begin.toDouble() shouldBeExactly 0.5
            events[1].part.end.toDouble() shouldBeExactly 1.0
            events[1].data.sound shouldBe "sd"
        }
    }

    "pickmod() handles negative indices correctly" {
        val lookup: List<Any> = listOf("bd", "hh", "sn")
        val result = seq("-1 -2 -3").pickmod(lookup)
        val events = result.queryArc(0.0, 1.0)
        events shouldHaveSize 3
        events[0].data.value?.asString shouldBe "sn"  // -1 mod 3 = 2
        events[1].data.value?.asString shouldBe "hh"  // -2 mod 3 = 1
        events[2].data.value?.asString shouldBe "bd"  // -3 mod 3 = 0
    }
})
