package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class LangDuckingSpec : StringSpec({

    "duck() sets duckOrbit" {
        val p = note("c3").duck(0)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.duckOrbit shouldBe 0
    }

    "duckorbit() sets duckOrbit" {
        val p = note("c3").duckorbit(1)
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

    "duckdepth() sets duckDepth" {
        val p = note("c3").duckdepth(0.7)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.duckDepth shouldBe 0.7
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

    "duck() can be used as standalone function" {
        val p = duck(1)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.duckOrbit shouldBe 1
    }

    "duckattack() can be used as standalone function" {
        val p = duckattack(0.25)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.duckAttack shouldBe 0.25
    }

    "duckdepth() can be used as standalone function" {
        val p = duckdepth(0.9)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.duckDepth shouldBe 0.9
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
