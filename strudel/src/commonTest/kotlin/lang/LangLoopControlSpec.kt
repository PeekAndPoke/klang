package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.StrudelPattern

class LangLoopControlSpec : StringSpec({

    // loopBegin tests

    "loopBegin() sets StrudelVoiceData.loopBegin" {
        val p = sound("bd").loopBegin("0.25")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.loopBegin shouldBe 0.25
    }

    "loopBegin() works with multiple values" {
        val p = loopBegin("0.0 0.25 0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 3
        events.map { it.data.loopBegin } shouldBe listOf(0.0, 0.25, 0.5)
    }

    "loopBegin() works as pattern extension" {
        val p = sound("bd").loopBegin("0.25")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.loopBegin shouldBe 0.25
    }

    "loopBegin() works as string extension" {
        val p = "bd".loopBegin("0.25")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.loopBegin shouldBe 0.25
    }

    "loopBegin() works in compiled code" {
        val p = StrudelPattern.compile("""sound("bd").loopBegin("0.25")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.loopBegin shouldBe 0.25
    }

    "loopb() is an alias for loopBegin()" {
        val p = sound("bd").loopb("0.25")
        val events = p.queryArc(0.0, 1.0)
        events[0].data.loopBegin shouldBe 0.25
    }

    // loopEnd tests

    "loopEnd() sets StrudelVoiceData.loopEnd" {
        val p = sound("bd").loopEnd("0.75")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.loopEnd shouldBe 0.75
    }

    "loopEnd() works with multiple values" {
        val p = loopEnd("0.5 0.75 1.0")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 3
        events.map { it.data.loopEnd } shouldBe listOf(0.5, 0.75, 1.0)
    }

    "loopEnd() works as pattern extension" {
        val p = sound("bd").loopEnd("0.75")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.loopEnd shouldBe 0.75
    }

    "loopEnd() works as string extension" {
        val p = "bd".loopEnd("0.75")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.loopEnd shouldBe 0.75
    }

    "loopEnd() works in compiled code" {
        val p = StrudelPattern.compile("""sound("bd").loopEnd("0.75")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.loopEnd shouldBe 0.75
    }

    "loope() is an alias for loopEnd()" {
        val p = sound("bd").loope("0.75")
        val events = p.queryArc(0.0, 1.0)
        events[0].data.loopEnd shouldBe 0.75
    }

    // Combined test

    "loopBegin and loopEnd work together" {
        val p = sound("bd").loopBegin("0.25").loopEnd("0.75")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.loopBegin shouldBe 0.25
        events[0].data.loopEnd shouldBe 0.75
    }

    "loopBegin and loopEnd work with loop()" {
        val p = sound("bd").loop().loopBegin("0.25").loopEnd("0.75")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.loop shouldBe true
        events[0].data.loopBegin shouldBe 0.25
        events[0].data.loopEnd shouldBe 0.75
    }
})
