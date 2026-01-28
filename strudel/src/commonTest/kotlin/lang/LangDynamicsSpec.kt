package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class LangDynamicsSpec : StringSpec({

    "velocity() sets velocity" {
        val p = note("c3").velocity(0.5)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.velocity shouldBe 0.5
    }

    "vel() alias sets velocity" {
        val p = note("c3").vel(0.8)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.velocity shouldBe 0.8
    }

    "velocity() can be used as standalone function" {
        val p = velocity(0.7)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.velocity shouldBe 0.7
    }

    "postgain() sets postGain" {
        val p = note("c3").postgain(1.5)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.postGain shouldBe 1.5
    }

    "postgain() can be used as standalone function" {
        val p = postgain(2.0)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.postGain shouldBe 2.0
    }

    "compressor() sets compressor string" {
        val p = note("c3").compressor("-20:4:6:0.003:0.1")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.compressor shouldBe "-20:4:6:0.003:0.1"
    }

    "comp() alias sets compressor" {
        val p = note("c3").comp("-15:3:4:0.005:0.2")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.compressor shouldBe "-15:3:4:0.005:0.2"
    }

    "compressor() can be used as standalone function" {
        val p = compressor("-18:6:8:0.001:0.05")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.compressor shouldBe "-18:6:8:0.001:0.05"
    }

    "velocity, postgain, and compressor work together" {
        val p = note("c3")
            .velocity(0.8)
            .postgain(1.2)
            .compressor("-20:4:6:0.003:0.1")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.velocity shouldBe 0.8
        events[0].data.postGain shouldBe 1.2
        events[0].data.compressor shouldBe "-20:4:6:0.003:0.1"
    }

    "dynamics parameters transfer to VoiceData" {
        val p = note("c3")
            .velocity(0.6)
            .postgain(1.5)
            .compressor("-18:3:4:0.005:0.15")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        val voiceData = events[0].data.toVoiceData()

        voiceData.velocity shouldBe 0.6
        voiceData.postGain shouldBe 1.5
        voiceData.compressor shouldBe "-18:3:4:0.005:0.15"
    }

    "velocity works with pattern control" {
        val p = note("c3 d3 e3").velocity("0.5 0.8 1.0")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 3
        events[0].data.velocity shouldBe 0.5
        events[1].data.velocity shouldBe 0.8
        events[2].data.velocity shouldBe 1.0
    }
})
