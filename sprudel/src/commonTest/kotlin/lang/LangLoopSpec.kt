package io.peekandpoke.klang.sprudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.dslInterfaceTests

class LangLoopSpec : StringSpec({

    "loop dsl interface" {
        val pat = "a b"
        val ctrl = "1 0"
        dslInterfaceTests(
            "pattern.loop(ctrl)" to seq(pat).loop(ctrl),
            "script pattern.loop(ctrl)" to SprudelPattern.compile("""seq("$pat").loop("$ctrl")"""),
            "string.loop(ctrl)" to pat.loop(ctrl),
            "script string.loop(ctrl)" to SprudelPattern.compile(""""$pat".loop("$ctrl")"""),
            "loop(ctrl)" to seq(pat).apply(loop(ctrl)),
            "script loop(ctrl)" to SprudelPattern.compile("""seq("$pat").apply(loop("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.loop shouldBe true
            events[1].data.loop shouldBe false
        }
    }

    "loop() with default true enables looping" {
        val p = sound("hh").loop()
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 1
        events[0].data.loop shouldBe true
    }

    "loop() as top-level PatternMapperFn" {
        val p = sound("hh").apply(loop(1))
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 1
        events[0].data.loop shouldBe true
    }

    "loop() sets VoiceData.loop correctly" {
        val p = sound("hh hh").apply(loop("1 0"))
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events.map { it.data.loop } shouldBe listOf(true, false)
    }

    "loop() works as string extension" {
        val p = "c3".loop(1)
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 1
        events[0].data.loop shouldBe true
    }

    "loop() works within compiled code" {
        val p = SprudelPattern.compile("""note("a b").loop("1 0")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 2
        events.map { it.data.loop } shouldBe listOf(true, false)
    }
})
