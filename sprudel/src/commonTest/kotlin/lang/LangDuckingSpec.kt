package io.peekandpoke.klang.sprudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.dslInterfaceTests

class LangDuckingSpec : StringSpec({

    "duckorbit dsl interface" {
        val pat = "0 1"
        val ctrl = "1 2"

        dslInterfaceTests(
            "pattern.duckorbit(ctrl)" to
                    seq(pat).duckorbit(ctrl),
            "script pattern.duckorbit(ctrl)" to
                    SprudelPattern.compile("""seq("$pat").duckorbit("$ctrl")"""),
            "string.duckorbit(ctrl)" to
                    pat.duckorbit(ctrl),
            "script string.duckorbit(ctrl)" to
                    SprudelPattern.compile(""""$pat".duckorbit("$ctrl")"""),
            "duckorbit(ctrl)" to
                    seq(pat).apply(duckorbit(ctrl)),
            "script duckorbit(ctrl)" to
                    SprudelPattern.compile("""seq("$pat").apply(duckorbit("$ctrl"))"""),
            // duck alias
            "pattern.duck(ctrl)" to
                    seq(pat).duck(ctrl),
            "script pattern.duck(ctrl)" to
                    SprudelPattern.compile("""seq("$pat").duck("$ctrl")"""),
            "string.duck(ctrl)" to
                    pat.duck(ctrl),
            "script string.duck(ctrl)" to
                    SprudelPattern.compile(""""$pat".duck("$ctrl")"""),
            "duck(ctrl)" to
                    seq(pat).apply(duck(ctrl)),
            "script duck(ctrl)" to
                    SprudelPattern.compile("""seq("$pat").apply(duck("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.duckOrbit shouldBe 1
            events[1].data.duckOrbit shouldBe 2
        }
    }

    "duckattack dsl interface" {
        val pat = "0 1"
        val ctrl = "0.1 0.5"

        dslInterfaceTests(
            "pattern.duckattack(ctrl)" to
                    seq(pat).duckattack(ctrl),
            "script pattern.duckattack(ctrl)" to
                    SprudelPattern.compile("""seq("$pat").duckattack("$ctrl")"""),
            "string.duckattack(ctrl)" to
                    pat.duckattack(ctrl),
            "script string.duckattack(ctrl)" to
                    SprudelPattern.compile(""""$pat".duckattack("$ctrl")"""),
            "duckattack(ctrl)" to
                    seq(pat).apply(duckattack(ctrl)),
            "script duckattack(ctrl)" to
                    SprudelPattern.compile("""seq("$pat").apply(duckattack("$ctrl"))"""),
            // duckatt alias
            "pattern.duckatt(ctrl)" to
                    seq(pat).duckatt(ctrl),
            "script pattern.duckatt(ctrl)" to
                    SprudelPattern.compile("""seq("$pat").duckatt("$ctrl")"""),
            "string.duckatt(ctrl)" to
                    pat.duckatt(ctrl),
            "script string.duckatt(ctrl)" to
                    SprudelPattern.compile(""""$pat".duckatt("$ctrl")"""),
            "duckatt(ctrl)" to
                    seq(pat).apply(duckatt(ctrl)),
            "script duckatt(ctrl)" to
                    SprudelPattern.compile("""seq("$pat").apply(duckatt("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.duckAttack shouldBe 0.1
            events[1].data.duckAttack shouldBe 0.5
        }
    }

    "duckdepth dsl interface" {
        val pat = "0 1"
        val ctrl = "0.1 0.5"

        dslInterfaceTests(
            "pattern.duckdepth(ctrl)" to
                    seq(pat).duckdepth(ctrl),
            "script pattern.duckdepth(ctrl)" to
                    SprudelPattern.compile("""seq("$pat").duckdepth("$ctrl")"""),
            "string.duckdepth(ctrl)" to
                    pat.duckdepth(ctrl),
            "script string.duckdepth(ctrl)" to
                    SprudelPattern.compile(""""$pat".duckdepth("$ctrl")"""),
            "duckdepth(ctrl)" to
                    seq(pat).apply(duckdepth(ctrl)),
            "script duckdepth(ctrl)" to
                    SprudelPattern.compile("""seq("$pat").apply(duckdepth("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.duckDepth shouldBe 0.1
            events[1].data.duckDepth shouldBe 0.5
        }
    }

    "duckorbit() sets duckOrbit" {
        val p = note("c3").duckorbit(1)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.duckOrbit shouldBe 1
    }

    "duck() alias sets duckOrbit" {
        val p = note("c3").duck(0)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.duckOrbit shouldBe 0
    }

    "duck() can be used as PatternMapper" {
        val p = note("c3").apply(duck(1))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.duckOrbit shouldBe 1
    }

    "duckattack() sets duckAttack" {
        val p = note("c3").duckattack(0.15)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.duckAttack shouldBe 0.15
    }

    "duckatt() alias sets duckAttack" {
        val p = note("c3").duckatt(0.2)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.duckAttack shouldBe 0.2
    }

    "duckattack() can be used as PatternMapper" {
        val p = note("c3").apply(duckattack(0.25))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.duckAttack shouldBe 0.25
    }

    "duckdepth() sets duckDepth" {
        val p = note("c3").duckdepth(0.7)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.duckDepth shouldBe 0.7
    }

    "duckdepth() can be used as PatternMapper" {
        val p = note("c3").apply(duckdepth(0.9))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.duckDepth shouldBe 0.9
    }

    "ducking parameters merge correctly" {
        val p = note("c3")
            .duck(0)
            .duckatt(0.1)
            .duckdepth(0.8)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.duckOrbit shouldBe 0
        events[0].data.duckAttack shouldBe 0.1
        events[0].data.duckDepth shouldBe 0.8
    }

    "ducking parameters transfer to VoiceData" {
        val p = note("c3")
            .duck(0)
            .duckatt(0.15)
            .duckdepth(0.6)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        val voiceData = events[0].data.toVoiceData()

        voiceData.duckOrbit shouldBe 0
        voiceData.duckAttack shouldBe 0.15
        voiceData.duckDepth shouldBe 0.6
    }

    "ducking parameters work with pattern control" {
        val p = note("c3 d3")
            .duck(2)
            .duckatt(0.1)
            .duckdepth(0.5)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.duckOrbit shouldBe 2
        events[0].data.duckAttack shouldBe 0.1
        events[0].data.duckDepth shouldBe 0.5
    }
})
